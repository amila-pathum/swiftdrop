"""Seed local Docker only; no schema changes or third-party Python packages.

Requires the existing Identity/Parcel/Courier/Dispatch migrations, a running local
Parcel Service outbox publisher, and RabbitMQ's management API. See README.md.
"""
import base64
import json
from pathlib import Path
import secrets
import subprocess
import sys
import time
from datetime import datetime, timedelta, timezone
from urllib.request import Request, urlopen
from uuid import NAMESPACE_URL, uuid5

ROOT = Path(__file__).resolve().parents[3]
OUTPUT = ROOT / "backend/courier-service/target/local-demo"
POSTGRES = "swiftdrop-postgres-1"
RABBIT = "swiftdrop-rabbitmq-1"
EXCHANGE = "swiftdrop.events"
QUEUE = "courier.demo.seed-events"
VHOST = "courier-demo"
PREFIX = "swiftdrop-courier-demo-v1"


def run(args, data=None):
    result = subprocess.run(args, input=data, capture_output=True)
    if result.returncode:
        # Do not echo arbitrary command arguments or container environments.
        raise RuntimeError(result.stderr.decode(errors="replace"))
    return result.stdout.decode().strip()


def inspect_local(name, port):
    container = json.loads(run(["docker", "inspect", name]))[0]
    bindings = container["NetworkSettings"]["Ports"].get(f"{port}/tcp") or []
    if not container["State"]["Running"] or not bindings:
        raise RuntimeError(f"{name} is not running with a published local port")
    if any(b["HostIp"] not in ("127.0.0.1", "::1") for b in bindings):
        raise RuntimeError(f"{name} must bind to loopback only")
    return container, bindings[0]["HostPort"]


def sql(database, statement):
    return run(["docker", "exec", "-i", POSTGRES, "sh", "-c",
                'exec psql -X -q -U "$POSTGRES_USER" -d "$1" -v ON_ERROR_STOP=1 -At',
                "sh", database], statement.encode())


def q(value):
    return "NULL" if value is None else "'" + str(value).replace("'", "''") + "'"


def uid(key):
    return str(uuid5(NAMESPACE_URL, f"{PREFIX}/{key}"))


def stamp(value):
    return value.isoformat().replace("+00:00", "Z")


def main():
    inspect_local(POSTGRES, 5432)
    rabbit, management_port = inspect_local(RABBIT, 15672)
    # Read credentials inside this process, never print or write them.
    env = dict(item.split("=", 1) for item in rabbit["Config"]["Env"])
    auth = base64.b64encode((env["RABBITMQ_DEFAULT_USER"] + ":" +
                             env["RABBITMQ_DEFAULT_PASS"]).encode()).decode()

    def api(method, path, body):
        path = path.replace("%2F", VHOST)
        request = Request(f"http://127.0.0.1:{management_port}/api/{path}",
                          data=json.dumps(body).encode(), method=method,
                          headers={"Authorization": "Basic " + auth,
                                   "Content-Type": "application/json"})
        with urlopen(request, timeout=15) as response:
            return json.loads(response.read() or b"null")

    # Preflight every database before inserting anything.
    for db, tables in {
        "identity_db": ["users"],
        "courier_db": ["courier_profiles", "couriers"],
        "parcel_db": ["parcels", "parcel_status_history", "parcel_event_outbox"],
        "dispatch_db": ["dispatch_parcels", "dispatch_assignments", "dispatch_status_history"],
    }.items():
        for table in tables:
            if sql(db, f"SELECT to_regclass('public.{table}') IS NOT NULL;") != "t":
                raise RuntimeError(f"Run the existing migrations for {db}: missing {table}")

    OUTPUT.mkdir(parents=True, exist_ok=True)
    credentials_file = OUTPUT / "accounts.json"
    manifest_file = OUTPUT / "manifest.json"
    if credentials_file.exists():
        credentials = json.loads(credentials_file.read_text())
    else:
        password = "Demo-" + secrets.token_urlsafe(16)
        crypto = sorted((Path.home() / ".m2/repository/org/springframework/security/spring-security-crypto").glob("*/*.jar"))
        if not crypto:
            raise RuntimeError("Build Identity Service first to install spring-security-crypto")
        helper = OUTPUT / "HashDemoPassword.java"
        helper.write_text('import org.springframework.security.crypto.bcrypt.BCrypt;\n'
                          'class HashDemoPassword { public static void main(String[] args) throws Exception {\n'
                          'String password = new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);\n'
                          'System.out.print(BCrypt.hashpw(password, BCrypt.gensalt())); }}\n')
        hashed = run(["java", "--class-path", str(crypto[-1]), str(helper)], password.encode())
        credentials = {"password": password, "passwordHash": hashed}
        # Write before inserts so a partially interrupted seed can be resumed.
        credentials_file.write_text(json.dumps(credentials, indent=2))

    accounts = {}
    for key, role, name in [
        ("courier-a", "COURIER", "Local Demo Courier A"),
        ("courier-b", "COURIER", "Local Demo Courier B"),
        ("customer", "CUSTOMER", "Local Demo Sender"),
        ("dispatcher", "DISPATCHER", "Local Demo Dispatcher"),
    ]:
        email = f"{key}@courier-demo.invalid"
        existing = sql("identity_db", f"SELECT name || '|' || role FROM users WHERE email={q(email)};")
        if existing and existing != name + "|" + role:
            raise RuntimeError(f"Reserved demo account collision: {email}")
        sql("identity_db", f"""BEGIN;
            INSERT INTO users (name,email,password_hash,role,status,created_at,updated_at,phone,address,token_version)
            VALUES ({q(name)},{q(email)},{q(credentials['passwordHash'])},{q(role)},'ACTIVE',now(),now(),'0770000000','Local demo address',0)
            ON CONFLICT (email) DO NOTHING; COMMIT;""")
        accounts[key] = {"email": email, "id": int(sql("identity_db", f"SELECT id FROM users WHERE email={q(email)};")), "role": role}
    credentials["accounts"] = accounts
    credentials_file.write_text(json.dumps(credentials, indent=2))

    for index, key in enumerate(("courier-a", "courier-b"), 1):
        user_id = accounts[key]["id"]
        sql("courier_db", f"""INSERT INTO courier_profiles
            (user_id,name,phone,vehicle_type,vehicle_number,area,availability,active,created_at,updated_at,version)
            VALUES ({user_id},{q('Local Demo Courier ' + key[-1].upper())},'0770000000','VAN',
                    {q('DEMO-' + str(index))},'Colombo','AVAILABLE',true,now(),now(),0)
            ON CONFLICT (user_id) DO NOTHING;""")
        accounts[key]["profileId"] = int(sql("courier_db", f"SELECT id FROM courier_profiles WHERE user_id={user_id};"))
        accounts[key]["dispatchCourierId"] = f"10000000-0000-4000-8000-{index:012d}"
    credentials["accounts"] = accounts
    credentials_file.write_text(json.dumps(credentials, indent=2))

    api("PUT", f"vhosts/{VHOST}", {})
    from urllib.parse import quote
    api("PUT", f"permissions/{VHOST}/{quote(env['RABBITMQ_DEFAULT_USER'], safe='')}",
        {"configure": ".*", "write": ".*", "read": ".*"})
    api("PUT", f"exchanges/%2F/{EXCHANGE}", {"type": "topic", "durable": True, "auto_delete": False, "arguments": {}})
    api("PUT", f"queues/%2F/{QUEUE}", {"durable": True, "auto_delete": False, "arguments": {}})
    for route in ("parcel.assigned", "parcel.status-changed"):
        api("POST", f"bindings/%2F/e/{EXCHANGE}/q/{QUEUE}", {"routing_key": route, "arguments": {}})
    api("PUT", "queues/%2F/dispatch.parcel-status-changed", {"durable": True, "auto_delete": False, "arguments": {}})
    api("POST", f"bindings/%2F/e/{EXCHANGE}/q/dispatch.parcel-status-changed",
        {"routing_key": "parcel.status-changed", "arguments": {}})
    if "--prepare-broker" in sys.argv:
        print(f"Prepared isolated RabbitMQ virtual host: {VHOST}")
        return

    if manifest_file.exists():
        manifest = json.loads(manifest_file.read_text())
    else:
        now = datetime.now(timezone.utc).replace(microsecond=0)
        statuses = ["PICKED_UP", "OUT_FOR_DELIVERY", "OUT_FOR_DELIVERY", "DELIVERED", "DELIVERED", "DELIVERED", "FAILED", "PICKED_UP"]
        addresses = ["7 Hospital Road, Dehiwala 10350", "19 High Level Road, Nugegoda", "31 Temple Road, Moratuwa"]
        manifest = {"dataset": PREFIX, "note": "UUID/numeric courier association below is test metadata only; the application has no persisted link yet.", "parcels": []}
        for index, status in enumerate(statuses, 1):
            created = now - timedelta(hours=5) if index in (1, 2, 3, 8) else now - timedelta(days=2, hours=5)
            courier = accounts["courier-b" if index == 8 else "courier-a"]
            manifest["parcels"].append({
                "id": uid(f"parcel/{index}"), "assignmentId": uid(f"assignment/{index}"),
                "trackingNumber": f"SD-COURIER-DEMO-{index:03d}", "status": status,
                "createdAt": stamp(created), "updatedAt": stamp(created + timedelta(hours=2)),
                "courierUserId": courier["id"], "dispatchCourierId": courier["dispatchCourierId"],
                "receiverName": f"Demo Recipient {index}", "receiverPhone": f"07700000{index:02d}",
                "pickupAddress": "8 Lake Drive, Kandy", "deliveryAddress": addresses[(index - 1) % 3],
                "weight": [2.5, 0.8, 0.5][(index - 1) % 3], "size": "SMALL", "charge": 850,
                "unpersistedDisplayFields": {"pickupContactName": "Demo Pickup Contact", "pickupContactPhone": "0770000099",
                    "contentsDescription": "Clothing", "lengthCm": 25, "widthCm": 20, "heightCm": 10,
                    "deliveryDate": created.astimezone(timezone(timedelta(hours=5, minutes=30))).date().isoformat()},
            })
        manifest_file.write_text(json.dumps(manifest, indent=2))

    events = []
    customer = accounts["customer"]["id"]
    dispatcher = accounts["dispatcher"]["id"]
    for p in manifest["parcels"]:
        pid = p["id"]
        created = datetime.fromisoformat(p["createdAt"].replace("Z", "+00:00"))
        for db, query in [("parcel_db", f"SELECT id FROM parcels WHERE tracking_number={q(p['trackingNumber'])};"),
                          ("dispatch_db", f"SELECT parcel_id FROM dispatch_parcels WHERE tracking_number={q(p['trackingNumber'])};")]:
            collision = sql(db, query)
            if collision and collision != pid:
                raise RuntimeError(f"Reserved tracking number collision in {db}")
        assignment = {"eventId": uid(f"assigned/{pid}"), "eventType": "ParcelAssigned",
                      "occurredAt": stamp(created + timedelta(minutes=10)), "parcelId": pid,
                      "trackingNumber": p["trackingNumber"], "courierId": p["dispatchCourierId"],
                      "previousCourierId": None, "assignedBy": str(dispatcher)}
        events.append({"routingKey": "parcel.assigned", "payload": assignment})
        stages = ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"]
        if p["status"] in stages:
            stages = stages[:stages.index(p["status"]) + 1]
        else:
            stages.append(p["status"])
        parcel_history, outbox, dispatch_history = [], [], []
        for index, status in enumerate(stages):
            previous = stages[index - 1] if index else None
            changed = stamp(created + timedelta(minutes=30 * index))
            event_id = uid(f"status/{pid}/{status}")
            event = {"eventId": event_id, "eventType": "ParcelStatusChanged", "occurredAt": changed,
                     "trackingNumber": p["trackingNumber"], "parcelId": pid, "merchantId": None,
                     "merchantOrderReference": None, "customerId": str(customer),
                     "courierId": str(p["courierUserId"]) if index else None,
                     "oldStatus": previous, "newStatus": status}
            events.append({"routingKey": "parcel.status-changed", "payload": event})
            actor = p["courierUserId"] if index else customer
            parcel_history.append(f"INSERT INTO parcel_status_history (id,parcel_id,previous_status,new_status,changed_at,changed_by,note) VALUES ({q(event_id)},{q(pid)},{q(previous)},{q(status)},{q(changed)},{actor},'Local courier demo') ON CONFLICT(id) DO NOTHING;")
            outbox.append(f"INSERT INTO parcel_event_outbox (event_id,parcel_id,routing_key,payload,occurred_at) VALUES ({q(event_id)},{q(pid)},'parcel.status-changed',{q(json.dumps(event))},{q(changed)}) ON CONFLICT(event_id) DO NOTHING;")
            dispatch_history.append(f"INSERT INTO dispatch_status_history (history_id,parcel_id,previous_status,new_status,changed_at,changed_by,note) VALUES ({q(event_id)},{q(pid)},{q(previous)},{q(status)},{q(changed)},{q(str(actor))},'Local courier demo') ON CONFLICT(history_id) DO NOTHING;")
        sql("parcel_db", f"""BEGIN;
            INSERT INTO parcels (id,tracking_number,sender_id,receiver_name,receiver_phone,pickup_address,delivery_address,
                weight,size,parcel_type,charge,status,created_by,assigned_courier_id,created_at,updated_at)
            VALUES ({q(pid)},{q(p['trackingNumber'])},{customer},{q(p['receiverName'])},{q(p['receiverPhone'])},
                {q(p['pickupAddress'])},{q(p['deliveryAddress'])},{p['weight']},'SMALL','STANDARD',850,{q(p['status'])},
                {customer},{p['courierUserId']},{q(p['createdAt'])},{q(p['updatedAt'])}) ON CONFLICT(id) DO NOTHING;
            {''.join(parcel_history)} {''.join(outbox)} COMMIT;""")
        state = "COMPLETED" if p["status"] in ("DELIVERED", "FAILED") else "ACTIVE"
        sql("dispatch_db", f"""BEGIN;
            INSERT INTO dispatch_parcels (parcel_id,tracking_number,pickup_area,dropoff_area,pickup_address,delivery_address,
                size,status,assigned_courier_id,charge,created_at,updated_at)
            VALUES ({q(pid)},{q(p['trackingNumber'])},'Kandy','Colombo',{q(p['pickupAddress'])},{q(p['deliveryAddress'])},
                'SMALL',{q(p['status'])},{q(p['dispatchCourierId'])},850,{q(p['createdAt'])},{q(p['updatedAt'])}) ON CONFLICT(parcel_id) DO NOTHING;
            INSERT INTO dispatch_assignments (assignment_id,parcel_id,courier_id,tracking_number,assigned_by,assigned_at,state)
            VALUES ({q(p['assignmentId'])},{q(pid)},{q(p['dispatchCourierId'])},{q(p['trackingNumber'])},{q(str(dispatcher))},
                {q(assignment['occurredAt'])},{q(state)}) ON CONFLICT(assignment_id) DO NOTHING;
            {''.join(dispatch_history)} COMMIT;""")

    (OUTPUT / "events.json").write_text(json.dumps(events, indent=2))
    # Only assignments need direct publication; Parcel's existing outbox handles statuses.
    for event in events:
        if event["routingKey"] == "parcel.assigned":
            response = api("POST", f"exchanges/%2F/{EXCHANGE}/publish", {
                "properties": {"content_type": "application/json", "delivery_mode": 2},
                "routing_key": event["routingKey"], "payload": json.dumps(event["payload"]), "payload_encoding": "string"})
            if not response["routed"]:
                raise RuntimeError("Demo assignment event was not routed")
    parcel_ids = ",".join(q(p["id"]) for p in manifest["parcels"])
    for _ in range(30):
        pending = int(sql("parcel_db", f"SELECT count(*) FROM parcel_event_outbox WHERE parcel_id IN ({parcel_ids}) AND published_at IS NULL;"))
        if pending == 0:
            break
        time.sleep(1)
    if pending:
        raise RuntimeError(f"{pending} demo status messages still pending; check the local Parcel Service publisher")
    # Peek/requeue only our own queue, preserving messages for inspection.
    expected_ids = {event["payload"]["eventId"] for event in events}
    for _ in range(10):
        received = api("POST", f"queues/%2F/{QUEUE}/get", {"count": 500, "ackmode": "ack_requeue_true", "encoding": "auto"})
        received_ids = {json.loads(message["payload"])["eventId"] for message in received}
        missing = expected_ids - received_ids
        if not missing:
            break
        time.sleep(1)
    if missing:
        raise RuntimeError(f"Missing {len(missing)} events in demo capture queue")
    actual_parcels = int(sql("parcel_db", f"SELECT count(*) FROM parcels WHERE id IN ({parcel_ids});"))
    assignment_counts = sql("dispatch_db", f"SELECT courier_id::text || '|' || state || '|' || count(*) FROM dispatch_assignments WHERE parcel_id IN ({parcel_ids}) GROUP BY courier_id,state ORDER BY courier_id,state;")
    expected_counts = '\n'.join([
        accounts['courier-a']['dispatchCourierId'] + '|ACTIVE|3',
        accounts['courier-a']['dispatchCourierId'] + '|COMPLETED|4',
        accounts['courier-b']['dispatchCourierId'] + '|ACTIVE|1',
    ])
    if actual_parcels != 8 or assignment_counts != expected_counts:
        raise RuntimeError("Seeded database counts differ from the fixture; existing records were not overwritten")
    summary = {"parcels": actual_parcels, "courierA": {"active": 3, "completed": 4},
               "courierB": {"active": 1}, "uniqueMessagesVerified": len(expected_ids), "queue": QUEUE, "vhost": VHOST,
               "limitations": "No Courier AMQP consumer/UUID ownership link or missing display-field schema was implemented."}
    (OUTPUT / "verification.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary, indent=2))
    print(f"Local demo credentials and fixtures: {OUTPUT}")


if __name__ == "__main__":
    main()
