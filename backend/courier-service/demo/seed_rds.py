"""Explicit RDS development fixture, limited to marked demo records.

Uses the existing Identity/Parcel/Dispatch APIs and Parcel outbox. No other
service's source code or schema is modified. See README.md before running.
"""
import argparse
import json
from pathlib import Path
import secrets
import subprocess
import time
from datetime import datetime, timedelta, timezone
from uuid import uuid5, NAMESPACE_URL
from urllib.request import Request, urlopen
from rds_support import ROOT, sql, quote as q

OUTPUT = ROOT / 'backend/courier-service/target/rds-demo'


def accounts():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    path = OUTPUT/'accounts.json'
    if path.exists():
        data = json.loads(path.read_text())
    else:
        password = 'Demo-' + secrets.token_urlsafe(16)
        helper = OUTPUT/'HashDemoPassword.java'
        helper.write_text('import org.springframework.security.crypto.bcrypt.BCrypt;\n'
            'class HashDemoPassword { public static void main(String[] args) throws Exception {\n'
            'String password=new String(System.in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);\n'
            'System.out.print(BCrypt.hashpw(password,BCrypt.gensalt())); }}')
        jars=sorted((Path.home()/'.m2/repository/org/springframework/security/spring-security-crypto').glob('*/*.jar'))
        result=subprocess.run(['java','--class-path',str(jars[-1]),str(helper)],input=password.encode(),capture_output=True)
        if result.returncode: raise RuntimeError('Could not generate demo password hash')
        data={'password':password,'passwordHash':result.stdout.decode().strip(),'accounts':{}}
        path.write_text(json.dumps(data,indent=2))
    for key,role in [('courier-a','COURIER'),('courier-b','COURIER'),('customer','CUSTOMER'),('dispatcher','DISPATCHER')]:
        email=key+'@courier-demo.invalid'
        name='RDS Demo '+key
        existing=sql('identity',f"SELECT name || '|' || role FROM users WHERE email={q(email)};")
        if existing and existing!=name+'|'+role: raise RuntimeError('Reserved demo email already belongs to another account')
        user_id=int(sql('identity',f"""BEGIN;
            INSERT INTO users(name,email,password_hash,role,status,created_at,updated_at,phone,address,token_version)
            VALUES ({q(name)},{q(email)},{q(data['passwordHash'])},{q(role)},'ACTIVE',now(),now(),'0770000000','RDS demo address',0)
            ON CONFLICT(email) DO NOTHING;
            SELECT id FROM users WHERE email={q(email)}; COMMIT;"""))
        data['accounts'][key]={'email':email,'id':user_id,'role':role}
        path.write_text(json.dumps(data,indent=2))
    return data


def api(path, data=None, token=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    request=Request('http://localhost:8080'+path,data=json.dumps(data).encode() if data is not None else None,headers=headers)
    with urlopen(request,timeout=30) as response:
        return json.loads(response.read() or b'null')


def login(data,key):
    return api('/api/auth/login',{'email':data['accounts'][key]['email'],'password':data['password']})['accessToken']


def seed(data):
    # Preserve the existing Identity account/password. Create a Courier business
    # profile only if that verified COURIER identity does not already have one.
    existing=json.loads(sql('identity',"SELECT row_to_json(t) FROM (SELECT id,email,name,role,status FROM users WHERE id=13)t;"))
    if existing['role']!='COURIER' or existing['status']!='ACTIVE':
        raise RuntimeError('Existing courier 13 is not an active COURIER identity')
    people=[(data['accounts'][key]['id'],'RDS Demo '+key,key) for key in ('courier-a','courier-b')]
    people.append((13,existing['name'],'existing-courier'))
    statements=[]
    for user_id,name,key in people:
        statements.append(f"""INSERT INTO courier_profiles(user_id,name,phone,vehicle_type,vehicle_number,area,availability,active,created_at,updated_at,version)
            VALUES ({user_id},{q(name)},'0770000000','VAN',{q('RDS-DEMO-'+str(user_id))},'Colombo','AVAILABLE',true,now(),now(),0)
            ON CONFLICT(user_id) DO NOTHING;""")
    ids=','.join(str(person[0]) for person in people)
    profiles=json.loads(sql('courier','BEGIN;'+''.join(statements)+f"SELECT json_agg(t) FROM (SELECT id,user_id,dispatch_courier_id,name,active,availability FROM courier_profiles WHERE user_id IN ({ids}))t;COMMIT;"))
    profiles={str(profile['user_id']):profile for profile in profiles}
    (OUTPUT/'profiles.json').write_text(json.dumps(profiles,indent=2))
    manifest_path=OUTPUT/'manifest.json'
    def uid(key):return str(uuid5(NAMESPACE_URL,'swiftdrop-rds-courier-demo-v1/'+key))
    if manifest_path.exists():manifest=json.loads(manifest_path.read_text())
    else:
        now=datetime.now(timezone.utc).replace(microsecond=0)
        manifest={'parcels':[],'existingCourierEmail':existing['email']}
        statuses=['PICKED_UP','OUT_FOR_DELIVERY','OUT_FOR_DELIVERY','DELIVERED','DELIVERED','DELIVERED','FAILED','PICKED_UP','PICKED_UP','CREATED']
        for index,status in enumerate(statuses,1):
            key='courier-b' if index==8 else 'existing-courier' if index==9 else 'courier-a'
            user_id=13 if key=='existing-courier' else data['accounts'][key]['id']
            profile=profiles[str(user_id)]
            created=now-timedelta(hours=3)
            manifest['parcels'].append({'id':uid(f'parcel/{index}'),'assignmentId':uid(f'assignment/{index}'),
                'trackingNumber':f'SD-RDS-COURIER-{index:03d}','status':status,'courierUserId':user_id,
                'profileId':profile['id'],'dispatchCourierId':profile['dispatch_courier_id'],
                'courierName':profile['name'],'receiverName':f'RDS Demo Recipient {index}','receiverPhone':'0770000000',
                'pickupAddress':'8 Lake Drive, Kandy','deliveryAddress':['7 Hospital Road, Dehiwala 10350','19 High Level Road, Nugegoda','31 Temple Road, Moratuwa'][(index-1)%3],
                'weight':[2.5,0.8,0.5][(index-1)%3],'size':'SMALL','createdAt':created.isoformat(),
                'deliveryDate':now.astimezone(timezone(timedelta(hours=5,minutes=30))).date().isoformat(),
                'initiallyAssigned':index!=10})
        manifest_path.write_text(json.dumps(manifest,indent=2))
    events=[];parcel_sql=[];dispatch_sql=[]
    customer=data['accounts']['customer']['id'];dispatcher=data['accounts']['dispatcher']['id']
    tracking_numbers=','.join(q(p['trackingNumber']) for p in manifest['parcels'])
    collisions=json.loads(sql('parcel',f"SELECT coalesce(json_object_agg(tracking_number,id),'{{}}'::json) FROM parcels WHERE tracking_number IN ({tracking_numbers});"))
    for p in manifest['parcels']:
        pid=p['id'];created=datetime.fromisoformat(p['createdAt']);updated=created+timedelta(hours=2)
        collision=collisions.get(p['trackingNumber'])
        if collision and collision!=pid:raise RuntimeError('Reserved RDS fixture tracking collision; no rows overwritten')
        parcel_sql.append(f"""INSERT INTO parcels(id,tracking_number,sender_id,receiver_name,receiver_phone,pickup_address,delivery_address,
            weight,size,parcel_type,charge,status,created_by,assigned_courier_id,created_at,updated_at)
            VALUES ({q(pid)},{q(p['trackingNumber'])},{customer},{q(p['receiverName'])},{q(p['receiverPhone'])},{q(p['pickupAddress'])},
                {q(p['deliveryAddress'])},{p['weight']},'SMALL','STANDARD',850,{q(p['status'])},{customer},
                {p['courierUserId'] if p['initiallyAssigned'] else 'NULL'},{q(p['createdAt'])},{q(updated.isoformat())}) ON CONFLICT(id) DO NOTHING;""")
        if p['initiallyAssigned']:
            state='COMPLETED' if p['status'] in ('DELIVERED','FAILED') else 'ACTIVE'
            assigned=(created+timedelta(minutes=10)).isoformat()
            dispatch_sql.append(f"""INSERT INTO dispatch_assignments(assignment_id,parcel_id,courier_profile_id,courier_user_id,courier_name,
                tracking_number,assigned_by,assigned_at,state,version)
                VALUES ({q(p['assignmentId'])},{q(pid)},{p['profileId']},{p['courierUserId']},{q(p['courierName'])},
                    {q(p['trackingNumber'])},{q(str(dispatcher))},{q(assigned)},{q(state)},0) ON CONFLICT(assignment_id) DO NOTHING;""")
            events.append({'route':'parcel.assigned','payload':{'eventId':uid('assigned/'+pid),'eventType':'ParcelAssigned',
                'occurredAt':assigned,'parcelId':pid,'trackingNumber':p['trackingNumber'],'courierId':p['dispatchCourierId'],
                'previousCourierId':None,'assignedBy':str(dispatcher),'assignmentId':p['assignmentId']}})
        events.append({'route':'parcel.delivery-snapshot.v1','payload':{'eventId':uid('snapshot/'+pid),'eventType':'ParcelDeliverySnapshot',
            'occurredAt':updated.isoformat(),'parcelId':pid,'trackingNumber':p['trackingNumber'],'pickupAddress':p['pickupAddress'],
            'deliveryAddress':p['deliveryAddress'],'receiverName':p['receiverName'],'receiverPhone':p['receiverPhone'],
            'weight':p['weight'],'size':p['size'],'deliveryDate':p['deliveryDate']}})
        stages=['CREATED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY']
        stages=stages[:stages.index(p['status'])+1] if p['status'] in stages else stages+[p['status']]
        for index,status in enumerate(stages):
            previous=stages[index-1] if index else None
            at=(created+timedelta(minutes=30*index)).isoformat();event_id=uid(f'status/{pid}/{status}')
            parcel_sql.append(f"""INSERT INTO parcel_status_history(id,parcel_id,previous_status,new_status,changed_at,changed_by,note)
                VALUES ({q(event_id)},{q(pid)},{q(previous)},{q(status)},{q(at)},{p['courierUserId'] if index else customer},'RDS Courier demo fixture') ON CONFLICT(id) DO NOTHING;""")
            events.append({'route':'parcel.status-changed','payload':{'eventId':event_id,'eventType':'ParcelStatusChanged','occurredAt':at,
                'parcelId':pid,'trackingNumber':p['trackingNumber'],'customerId':str(customer),'courierId':str(p['courierUserId']) if index else None,
                'merchantId':None,'merchantOrderReference':None,'oldStatus':previous,'newStatus':status}})
    # Publish fixtures only after both databases have their matching rows.
    sql('parcel','BEGIN;'+''.join(parcel_sql)+'COMMIT;')
    sql('dispatch','BEGIN;'+''.join(dispatch_sql)+'COMMIT;')
    outbox=[]
    for event in events:
        payload=event['payload']
        outbox.append(f"INSERT INTO parcel_event_outbox(event_id,parcel_id,routing_key,payload,occurred_at) VALUES ({q(payload['eventId'])},{q(payload['parcelId'])},{q(event['route'])},{q(json.dumps(payload))},{q(payload['occurredAt'])}) ON CONFLICT(event_id) DO NOTHING;")
    sql('parcel','BEGIN;'+''.join(outbox)+'COMMIT;')
    (OUTPUT/'events.json').write_text(json.dumps(events,indent=2))
    courier=login(data,'courier-a');dispatcher_token=login(data,'dispatcher')
    candidate=manifest['parcels'][-1]
    for _ in range(30):
        active=api('/api/couriers/me/deliveries?filter=active',token=courier)
        history=api('/api/couriers/me/deliveries?filter=history',token=courier)
        if active['totalElements']>=3 and history['totalElements']>=4:break
        time.sleep(1)
    else:raise RuntimeError('Courier has not consumed the RDS fixtures; check Parcel outbox and Courier queue')
    # This final assignment is real: it must be published by the running Dispatch
    # service, consumed by Courier, and appear through the normal browser gateway.
    active_assignment=sql('dispatch',f"SELECT assignment_id FROM dispatch_assignments WHERE parcel_id={q(candidate['id'])} AND state='ACTIVE';")
    if not active_assignment:
        api('/api/dispatch/assignments',{'parcelId':candidate['id'],'courierId':candidate['dispatchCourierId']},dispatcher_token)
    for _ in range(30):
        page=api('/api/couriers/me/deliveries?filter=active',token=courier)
        if any(item['parcel']['id']==candidate['id'] for item in page['content']):break
        time.sleep(1)
    else:raise RuntimeError('Live Dispatch assignment did not reach Courier through RabbitMQ')
    detail=api('/api/couriers/me/deliveries/'+candidate['id'],token=courier)
    assert detail['parcel']['deliveryAddress']==candidate['deliveryAddress']
    snapshot_source=sql('courier',f"SELECT CASE WHEN p.snapshot_at>=a.assigned_at THEN 'verified' ELSE 'missing' END FROM courier_delivery_parcels p JOIN courier_delivery_assignments a USING(parcel_id) WHERE p.parcel_id={q(candidate['id'])};")
    if snapshot_source!='verified':raise RuntimeError('Live assignment did not include its parcel snapshot')
    other=login(data,'courier-b')
    from urllib.error import HTTPError
    try:api('/api/couriers/me/deliveries/'+candidate['id'],token=other)
    except HTTPError as error:
        if error.code!=403:raise
    else:raise RuntimeError('Another courier could read the assigned parcel')
    summary={'parcels':len(manifest['parcels']),'fixtureEvents':len(events),'courierAActive':page['totalElements'],
        'courierACompleted':history['totalElements'],'liveDispatchAssignmentVerified':candidate['trackingNumber'],
        'liveParcelSnapshotVerified':True,'otherCourierDenied':True,'browser':'http://localhost:3000','credentialsFile':str(OUTPUT/'accounts.json')}
    (OUTPUT/'verification.json').write_text(json.dumps(summary,indent=2))
    print(json.dumps(summary,indent=2))


def enrich_details(data):
    """Optional detail/history fixture delivered through the existing Parcel outbox."""
    if sql('courier',"SELECT to_regclass('public.courier_delivery_history') IS NOT NULL;") not in ('t','true'):
        raise RuntimeError('Start the updated Courier Service to apply its V4 migration first')
    manifest=json.loads((OUTPUT/'manifest.json').read_text())
    ids=','.join(q(p['id']) for p in manifest['parcels'])
    parcels=json.loads(sql('parcel',f"SELECT json_agg(t) FROM (SELECT * FROM parcels WHERE id IN ({ids}) AND tracking_number LIKE 'SD-RDS-COURIER-%')t;"))
    histories=json.loads(sql('parcel',f"SELECT json_agg(t) FROM (SELECT * FROM parcel_status_history WHERE parcel_id IN ({ids}) ORDER BY changed_at,id)t;"))
    if len(parcels)!=len(manifest['parcels']):raise RuntimeError('RDS demo parcel set does not match its manifest')
    details_file=OUTPUT/'detail-snapshots.json'
    if details_file.exists():events=json.loads(details_file.read_text())
    else:
        profiles=json.loads((OUTPUT/'profiles.json').read_text())
        roles={int(user_id):'COURIER' for user_id in profiles}
        roles[data['accounts']['customer']['id']]='CUSTOMER'
        roles[data['accounts']['dispatcher']['id']]='DISPATCHER'
        schedules={p['id']:p['deliveryDate'] for p in manifest['parcels']}
        events=[];now=datetime.now(timezone.utc).isoformat()
        for parcel in parcels:
            entries=[]
            for item in histories:
                if item['parcel_id']!=parcel['id']:continue
                # These are explicitly marked demo scenarios, not inferred real GPS.
                is_fixture=item.get('note')=='RDS Courier demo fixture'
                location='Kandy' if item['new_status'] in ('CREATED','PICKED_UP') else 'Colombo'
                entries.append({'id':item['id'],'status':item['new_status'],'recordedAt':item['changed_at'],
                    'locationDescription':location if is_fixture else None,'attemptNo':1 if is_fixture else None,
                    'actorRole':roles.get(item['changed_by'])})
            events.append({'eventId':str(uuid5(NAMESPACE_URL,'swiftdrop-rds-courier-detail-v1/'+parcel['id'])),
                'eventType':'ParcelDeliverySnapshot','occurredAt':now,'parcelId':parcel['id'],'trackingNumber':parcel['tracking_number'],
                'pickupAddress':parcel['pickup_address'],'deliveryAddress':parcel['delivery_address'],
                'receiverName':parcel['receiver_name'],'receiverPhone':parcel['receiver_phone'],'weight':parcel['weight'],'size':parcel['size'],
                'deliveryDate':schedules[parcel['id']],'pickupContactName':'Demo Pickup Contact','pickupContactPhone':'0770000099',
                'contentsDescription':'Clothing','lengthCm':25,'widthCm':20,'heightCm':10,'history':entries})
        details_file.write_text(json.dumps(events,indent=2))
    statements=[]
    for event in events:
        statements.append(f"INSERT INTO parcel_event_outbox(event_id,parcel_id,routing_key,payload,occurred_at) VALUES ({q(event['eventId'])},{q(event['parcelId'])},'parcel.delivery-snapshot.v1',{q(json.dumps(event))},{q(event['occurredAt'])}) ON CONFLICT(event_id) DO NOTHING;")
    sql('parcel','BEGIN;'+''.join(statements)+'COMMIT;')
    token=login(data,'courier-a');first=manifest['parcels'][0]
    for _ in range(40):
        detail=api('/api/couriers/me/deliveries/'+first['id'],token=token)
        parcel=detail['parcel']
        if parcel.get('contentsDescription')=='Clothing' and len(parcel.get('history',[]))>=2:break
        time.sleep(1)
    else:raise RuntimeError('Detail snapshots have not reached the Courier projection')
    assert parcel['pickupContactName']=='Demo Pickup Contact' and float(parcel['lengthCm'])==25
    assert {entry['actorRole'] for entry in parcel['history']} >= {'CUSTOMER','COURIER'}
    summary={'detailSnapshots':len(events),'exampleTrackingNumber':first['trackingNumber'],
        'historyEntries':len(parcel['history']),'contents':parcel['contentsDescription'],'dimensionsCm':[parcel['lengthCm'],parcel['widthCm'],parcel['heightCm']],
        'source':'RabbitMQ via Parcel demo outbox; optional fields and location labels are explicit sample data'}
    (OUTPUT/'detail-verification.json').write_text(json.dumps(summary,indent=2))
    print(json.dumps(summary,indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--prepare-accounts',action='store_true')
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--details-only',action='store_true')
    args=parser.parse_args()
    if args.details_only:
        enrich_details(json.loads((OUTPUT/'accounts.json').read_text()))
        raise SystemExit(0)
    if not args.prepare_accounts and not args.apply:
        parser.error('Use --prepare-accounts, --apply or --details-only for the RDS development fixture')
    data=accounts()
    print(json.dumps({key: {'id':value['id'],'role':value['role']} for key,value in data['accounts'].items()}))
    if args.apply:seed(data)
