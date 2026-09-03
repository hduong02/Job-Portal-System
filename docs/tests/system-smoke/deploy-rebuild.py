"""Replace application images on the VPS while retaining inspected runtime settings."""
import copy
import http.client
import json
import os
import pathlib
import re
import socket
import subprocess
import time
import urllib.parse

TAG = 'rebuild-20261012-0200'
ROOT = pathlib.Path('/root/job-portal-rebuild-20261012-0200')
ROOT.mkdir(mode=0o700, exist_ok=True)
os.umask(0o077)
SERVICES = [
    ('discovery', 'service-registry'), ('config-server', 'config-server'),
    ('user-service', 'user-service'), ('company-service', 'company-service'),
    ('job-service', 'job-service'), ('resume-service', 'resume-service'),
    ('application-service', 'application-service'), ('preference-service', 'preference-service'),
    ('ai-service', 'ai-service'), ('notification-service', 'notification-service'),
    ('gateway', 'api-gateway'),
]

class DockerConnection(http.client.HTTPConnection):
    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(90)
        self.sock.connect('/var/run/docker.sock')

def api(method, route, body=None):
    conn = DockerConnection('localhost', timeout=90)
    data = None if body is None else json.dumps(body)
    conn.request(method, '/v1.45' + route, data, {'Content-Type': 'application/json'})
    res = conn.getresponse()
    raw = res.read()
    conn.close()
    if res.status >= 300:
        raise RuntimeError(f'Docker {method} {route}: HTTP {res.status}')
    return json.loads(raw) if raw else None

snapshots = {}
if (ROOT / 'containers-before.json').exists():
    snapshots = json.loads((ROOT / 'containers-before.json').read_text())
else:
    for service, repository in SERVICES:
        name = 'job-portal-' + service
        snapshots[name] = api('GET', '/containers/' + name + '/json')
    (ROOT / 'containers-before.json').write_text(json.dumps(snapshots, indent=2))
compose = pathlib.Path('/opt/apps/job-portal/Job-Portal-System/docker/docker-compose.yml')
if not (ROOT / 'docker-compose-before.yml').exists():
    (ROOT / 'docker-compose-before.yml').write_bytes(compose.read_bytes())
for service, repository in SERVICES:
    image = f'andyy02/job-portal-{repository}:{TAG}'
    subprocess.run(['docker', 'pull', image], check=True)

deployed = json.loads((ROOT / 'deployment.json').read_text()) if (ROOT / 'deployment.json').exists() else []
for service, repository in SERVICES:
    name = 'job-portal-' + service
    if any(item['container'] == name for item in deployed):
        continue
    old = snapshots[name]
    image = f'andyy02/job-portal-{repository}:{TAG}'
    image_config = api('GET', '/images/' + urllib.parse.quote(image, safe='') + '/json')['Config']
    config = copy.deepcopy(old['Config'])
    config.update(Image=image, Entrypoint=image_config['Entrypoint'], Cmd=image_config.get('Cmd'),
                  WorkingDir=image_config.get('WorkingDir', ''), User=image_config.get('User', ''))
    config['Hostname'] = name
    config['Labels'] = {k: v for k, v in config.get('Labels', {}).items()
                        if not k.startswith('org.opencontainers.')}
    config['Labels']['com.docker.compose.image'] = api('GET', '/images/' + urllib.parse.quote(image, safe='') + '/json')['Id']
    config['Labels']['job-portal.rebuild'] = TAG
    config['HostConfig'] = copy.deepcopy(old['HostConfig'])
    endpoints = {}
    for network, settings in old['NetworkSettings']['Networks'].items():
        aliases = [a for a in (settings.get('Aliases') or []) if a not in (old['Id'][:12], old['Id'])]
        endpoints[network] = {'Aliases': aliases}
    config['NetworkingConfig'] = {'EndpointsConfig': endpoints}
    backup_name = name + '-before-' + TAG
    print('Replacing ' + name, flush=True)
    backup_exists = subprocess.run(['docker', 'inspect', backup_name], capture_output=True).returncode == 0
    if not backup_exists:
        api('POST', '/containers/' + name + '/stop?t=30')
        api('POST', '/containers/' + name + '/rename?name=' + backup_name)
    try:
        if not backup_exists:
            created = api('POST', '/containers/create?name=' + name, config)
            api('POST', '/containers/' + created['Id'] + '/start')
        deadline = time.monotonic() + 240
        while time.monotonic() < deadline:
            state = api('GET', '/containers/' + name + '/json')['State']
            if not state['Running']:
                raise RuntimeError(name + ' exited during startup')
            health = state.get('Health')
            if health:
                ready = health['Status'] == 'healthy'
            elif service == 'notification-service':
                result = subprocess.run(['docker', 'exec', 'job-portal-kafka-dev',
                    '/opt/kafka/bin/kafka-consumer-groups.sh', '--bootstrap-server', 'localhost:9092',
                    '--describe', '--group', 'notification-service', '--state'], capture_output=True, text=True)
                ready = result.returncode == 0 and bool(re.search(r'\bStable\s+[1-9][0-9]*\s*$', result.stdout, re.M))
            else:
                port = {'gateway': 5000, 'user-service': 5001, 'company-service': 5002,
                        'job-service': 5003, 'resume-service': 5004, 'application-service': 5005,
                        'preference-service': 5006, 'ai-service': 6000, 'notification-service': 5011}[service]
                result = subprocess.run(['curl', '-fsS', '--max-time', '5', f'http://127.0.0.1:{port}/actuator/health'], capture_output=True)
                try:
                    ready = result.returncode == 0 and json.loads(result.stdout).get('status') == 'UP'
                except ValueError:
                    ready = False
            if ready:
                break
            time.sleep(3)
        else:
            raise RuntimeError(name + ' did not become ready')
    except Exception:
        subprocess.run(['docker', 'rm', '-f', name], capture_output=True)
        api('POST', '/containers/' + backup_name + '/rename?name=' + name)
        api('POST', '/containers/' + name + '/start')
        raise
    deployed.append({'container': name, 'image': image, 'imageId': api('GET', '/containers/' + name + '/json')['Image'], 'backup': backup_name})
    (ROOT / 'deployment.json').write_text(json.dumps(deployed, indent=2))
    print(name + ' ready', flush=True)
text = compose.read_text()
for service, repository in SERVICES:
    text = re.sub(r'(\n  ' + re.escape(service) + r':\s*\n\s+image:) [^\n]+',
                  r'\1 andyy02/job-portal-' + repository + ':' + TAG, text)
compose.write_text(text)
for item in deployed:
    api('DELETE', '/containers/' + item['backup'])
print('All eleven replacement containers ready; prior images and runtime snapshots retained for rollback.', flush=True)
