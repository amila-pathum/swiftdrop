#!/usr/bin/env python3
"""Real Identity + Gateway + Parcel integration; disposable PostgreSQL, pricing stub.
Build the three service jars first. Requires Docker, Java 21 and Python 3.
Never reads .env or mutates configured development/production databases.
"""
import base64
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.request import Request, urlopen
from urllib.error import HTTPError
import uuid

ROOT = Path(__file__).resolve().parents[3]


def port():
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0))
        return s.getsockname()[1]


def wait(check):
    for _ in range(120):
        try:
            if check():
                return
        except (OSError, ValueError):
            pass
        time.sleep(.5)
    raise RuntimeError('Service readiness timeout')


class Pricing(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def do_POST(self):
        self.rfile.read(int(self.headers.get('Content-Length', '0')))
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', '17')
        self.send_header('Connection', 'close')
        self.end_headers()
        self.wfile.write(b'{"charge":450.00}')

    def log_message(self, *args):
        pass


def call(base, path, method='GET', body=None, token=None, expected=200):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = Request(base + path, method=method, headers=headers,
                  data=None if body is None else json.dumps(body).encode())
    try:
        response = urlopen(req, timeout=10)
    except HTTPError as e:
        response = e
    with response:
        data = json.load(response)
        assert response.status == expected, (method, path, response.status, expected)
        return data


container = 'swiftdrop-identity-test-' + uuid.uuid4().hex[:12]
processes = []
pricing = ThreadingHTTPServer(('127.0.0.1', 0), Pricing)
threading.Thread(target=pricing.serve_forever, daemon=True).start()
with tempfile.TemporaryDirectory(prefix='identity-parcel-test-') as logs:
    try:
        subprocess.run(['docker', 'run', '--rm', '-d', '--name', container,
                        '-e', 'POSTGRES_PASSWORD=isolated-test-only',
                        '-p', '127.0.0.1::5432', 'postgres:18'], check=True, capture_output=True)
        dbport = subprocess.check_output(['docker', 'port', container, '5432'], text=True).strip().split(':')[-1]
        wait(lambda: subprocess.run(['docker', 'exec', container, 'pg_isready', '-U', 'postgres'],
                                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0)

        def sql(database, query):
            return subprocess.check_output(['docker', 'exec', '-i', container, 'psql', '-U', 'postgres',
                '-d', database, '-X', '-At', '-v', 'ON_ERROR_STOP=1'], input=query, text=True).strip()

        sql('postgres', 'CREATE DATABASE identity_db;\nCREATE DATABASE parcel_db;')
        identity, parcel, gateway = port(), port(), port()
        env = {k: os.environ[k] for k in ('PATH', 'HOME', 'JAVA_HOME') if k in os.environ}
        env.update({'JWT_SECRET': base64.b64encode(bytes(range(32))).decode(),
                    'IDENTITY_SERVICE_URL': f'http://127.0.0.1:{identity}',
                    'PARCEL_SERVICE_URL': f'http://127.0.0.1:{parcel}',
                    'PRICING_SERVICE_URL': f'http://127.0.0.1:{pricing.server_port}',
                    'ADMIN_BOOTSTRAP_EMAIL': 'admin@example.test',
                    'ADMIN_BOOTSTRAP_PASSWORD': 'Isolated-Admin-123!',
                    'DB_PASSWORD': 'isolated-test-only'})
        for service, service_port, database in [('identity-service', identity, 'identity_db'),
                                                ('parcel-service', parcel, 'parcel_db'),
                                                ('api-gateway', gateway, None)]:
            jar = ROOT / 'backend' / service / 'target' / f'{service}-0.0.1-SNAPSHOT.jar'
            args = ['java', '-jar', str(jar), f'--server.port={service_port}']
            if database:
                args += [f'--spring.datasource.url=jdbc:postgresql://127.0.0.1:{dbport}/{database}',
                         '--spring.datasource.username=postgres', '--spring.datasource.password=isolated-test-only']
            with open(Path(logs) / f'{service}.log', 'w') as log:
                processes.append(subprocess.Popen(args, env=env, stdout=log, stderr=log))
            wait(lambda: call(f'http://127.0.0.1:{service_port}', '/actuator/health')['status'] == 'UP')

        base = f'http://127.0.0.1:{gateway}'
        def login(email, password='Isolated-User-123!'):
            return call(base, '/api/auth/login', 'POST', {'email': email, 'password': password})

        admin = login('admin@example.test', 'Isolated-Admin-123!')['accessToken']
        users = {}
        for role, suffix in [('CUSTOMER', ''), ('MERCHANT', '/merchant'), ('COURIER', '/courier')]:
            email = role.lower() + '@example.test'
            user = call(base, '/api/auth/register' + suffix, 'POST',
                        {'name': role.title(), 'email': email, 'password': 'Isolated-User-123!'}, expected=201)
            if role != 'CUSTOMER':
                call(base, f'/api/admin/users/{user["id"]}/status', 'PATCH', {'status': 'ACTIVE'}, admin)
            session = login(email)
            assert isinstance(session['user']['id'], int)
            payload = session['accessToken'].split('.')[1]
            claims = json.loads(base64.urlsafe_b64decode(payload + '=' * (-len(payload) % 4)))
            assert claims['sub'] == str(session['user']['id'])
            users[role] = session

        customer = users['CUSTOMER']; courier = users['COURIER']; merchant = users['MERCHANT']
        body = {'receiverName': 'Receiver', 'receiverPhone': '0771234567', 'pickupAddress': 'Colombo',
                'deliveryAddress': 'Kandy', 'weight': 2.5, 'size': 'MEDIUM', 'parcelType': 'STANDARD'}
        # Attempted identity injection must not override the authenticated customer.
        created = call(base, '/api/parcels', 'POST', {**body, 'senderId': merchant['user']['id'],
                        'createdBy': merchant['user']['id'], 'merchantId': merchant['user']['id'],
                        'assignedCourierId': courier['user']['id']}, customer['accessToken'], 201)
        parcel_id = str(uuid.UUID(created['id']))
        details = call(base, f'/api/parcels/{parcel_id}', token=customer['accessToken'])
        assert details['senderId'] == customer['user']['id'] and details['merchantId'] is None
        assert sql('parcel_db', f"SELECT sender_id || ':' || created_by FROM parcels WHERE id='{parcel_id}'") == \
            f"{customer['user']['id']}:{customer['user']['id']}"
        assert sql('parcel_db', f"SELECT assigned_courier_id IS NULL FROM parcels WHERE id='{parcel_id}'") == 't'
        call(base, '/api/parcels', 'POST', body, expected=401)
        call(base, '/api/parcels', 'POST', body, merchant['accessToken'], 403)
        call(base, f'/api/parcels/{parcel_id}/courier', 'PATCH', {'courierUserId': courier['user']['id']},
             customer['accessToken'], 403)
        call(base, f'/api/parcels/{parcel_id}/courier', 'PATCH', {'courierUserId': courier['user']['id']}, admin)
        assert sql('parcel_db', f"SELECT assigned_courier_id FROM parcels WHERE id='{parcel_id}'") == str(courier['user']['id'])
        projection = call(base, f'/api/parcels/dispatch/{parcel_id}', token=admin)
        assert projection['courierUserId'] == courier['user']['id'] and projection['courierId'] is None
        call(base, f'/api/parcels/{parcel_id}/status', 'PATCH', {'status': 'PICKED_UP'}, courier['accessToken'])
        call(base, f'/api/parcels/{parcel_id}/status', 'PATCH', {'status': 'IN_TRANSIT'}, admin)
        history = call(base, f'/api/parcels/{parcel_id}/history', token=customer['accessToken'])
        assert [h['newStatus'] for h in history] == ['CREATED', 'PICKED_UP', 'IN_TRANSIT']
        assert history[0]['changedBy'] == customer['user']['id']
        assert history[1]['changedBy'] == courier['user']['id']
        public = call(base, '/api/parcels/track/' + created['trackingNumber'])
        assert public['status'] == 'IN_TRANSIT' and len(public['trackingHistory']) == 3
        assert 'changedBy' not in json.dumps(public) and 'senderId' not in public
        call(base, '/api/parcels/track/SD-UNKNOWN', expected=404)
        booking = call(base, '/api/parcels/merchant', 'POST', body, merchant['accessToken'], 201)
        mid = str(uuid.UUID(booking['id']))
        assert sql('parcel_db', f"SELECT sender_id || ':' || created_by || ':' || merchant_id FROM parcels WHERE id='{mid}'") == \
            ':'.join([str(merchant['user']['id'])] * 3)
        print('PASS: real Identity JWTs through Gateway: customer/merchant creation, ownership, numeric IDs, '
              'staff assignment, assigned-courier/admin status changes, history and anonymous tracking. Pricing is stubbed.')
    except Exception:
        for log in Path(logs).glob('*.log'):
            # Only isolated test services; never print login response bodies/tokens.
            print(log.name, log.read_text()[-3500:])
        raise
    finally:
        pricing.shutdown()
        pricing.server_close()
        for process in processes:
            process.terminate()
        for process in processes:
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait()
        subprocess.run(['docker', 'rm', '-f', container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
