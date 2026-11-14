#!/usr/bin/env python3
"""Real gateway -> Parcel Service -> disposable PostgreSQL smoke test.
Build both service jars with ./mvnw -DskipTests package before running.
Requires Java 21, Docker and Python 3. Never uses application .env credentials.
"""
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def request(port, path, method='GET'):
    try:
        response = urllib.request.urlopen(urllib.request.Request(
            f'http://127.0.0.1:{port}{path}', method=method), timeout=2)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, json.load(response)


def wait_for(check):
    for _ in range(120):
        try:
            if check():
                return
        except (OSError, ValueError):
            pass
        time.sleep(0.5)
    raise RuntimeError('Test service did not become ready within 60 seconds')


container = 'swiftdrop-tracking-test-' + uuid.uuid4().hex[:12]
processes = []
with tempfile.TemporaryDirectory(prefix='swiftdrop-tracking-') as logs:
    try:
        subprocess.run(['docker', 'run', '--rm', '-d', '--name', container,
                        '-e', 'POSTGRES_PASSWORD=tracking-test-only', '-e', 'POSTGRES_DB=parcel_db',
                        '-p', '127.0.0.1::5432', 'postgres:18'], check=True, capture_output=True)
        db_port = subprocess.check_output(['docker', 'port', container, '5432'], text=True).strip().split(':')[-1]
        wait_for(lambda: subprocess.run(['docker', 'exec', container, 'pg_isready', '-U', 'postgres'],
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0)
        parcel_port, gateway_port = free_port(), free_port()
        env = {**{key: os.environ[key] for key in ('PATH', 'HOME', 'JAVA_HOME') if key in os.environ},
               'SPRING_PROFILES_ACTIVE': 'default',
               'JWT_SECRET': 'AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=',
               'PARCEL_DB_PASSWORD': 'tracking-test-only', 'POSTGRES_HOST': '127.0.0.1',
               'POSTGRES_PORT': db_port, 'PARCEL_SERVICE_PORT': str(parcel_port),
               'GATEWAY_PORT': str(gateway_port), 'PARCEL_SERVICE_URL': f'http://127.0.0.1:{parcel_port}'}
        for service, port in [('parcel-service', parcel_port), ('api-gateway', gateway_port)]:
            jar = ROOT / 'backend' / service / 'target' / f'{service}-0.0.1-SNAPSHOT.jar'
            with open(Path(logs) / f'{service}.log', 'w') as output:
                processes.append(subprocess.Popen(['java', '-jar', str(jar)], env=env, stdout=output, stderr=output))
            wait_for(lambda: request(port, '/actuator/health')[0] == 200)
        # Fixtures exist only in this disposable test database, never in user databases.
        parcel_id, actor = uuid.uuid4(), uuid.uuid4()
        number = 'SD' + uuid.uuid4().hex.upper()
        sql = f"""
        INSERT INTO parcels(id,tracking_number,sender_id,receiver_name,receiver_phone,pickup_address,
          delivery_address,weight,size,parcel_type,charge,status,created_by,created_at,updated_at)
        VALUES ('{parcel_id}','{number}','{actor}','Private recipient','0771234567','Private pickup',
          'Private delivery',1,'SMALL','STANDARD',100,'PICKED_UP','{actor}',now(),now());
        INSERT INTO parcel_status_history(id,parcel_id,new_status,changed_at,changed_by,note)
        VALUES ('{uuid.uuid4()}','{parcel_id}','CREATED','2026-01-01','{actor}','Private note'),
               ('{uuid.uuid4()}','{parcel_id}','PICKED_UP','2026-01-02','{actor}','Private note');
        """
        subprocess.run(['docker', 'exec', '-i', container, 'psql', '-U', 'postgres', '-d', 'parcel_db',
                        '-v', 'ON_ERROR_STOP=1'], input=sql, text=True, check=True, capture_output=True)
        status, body = request(gateway_port, '/api/parcels/track/' + number)
        assert status == 200 and body['trackingNumber'] == number and body['status'] == 'PICKED_UP'
        assert [entry['status'] for entry in body['trackingHistory']] == ['CREATED', 'PICKED_UP']
        assert body['estimatedDeliveryDate'] is None
        assert 'Private' not in json.dumps(body) and str(parcel_id) not in json.dumps(body)
        assert request(gateway_port, '/api/parcels/track/SD-UNKNOWN') == (404, {'error': 'Parcel not found'})
        assert request(gateway_port, '/api/parcels', 'POST')[0] == 401
        assert request(gateway_port, '/api/parcels/my')[0] == 401
        print('PASS: real gateway + Parcel Service + PostgreSQL: public tracking, history, privacy, 404 and protected routes')
    except Exception:
        for log in Path(logs).glob('*.log'):
            print(log.name, log.read_text()[-6000:])
        raise
    finally:
        for process in processes:
            process.terminate()
        for process in processes:
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        subprocess.run(['docker', 'rm', '-f', container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
