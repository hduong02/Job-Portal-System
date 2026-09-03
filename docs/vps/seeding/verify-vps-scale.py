"""Run next to scale-manifest.json on the VPS after seed-vps-scale.py.

Checks service reads twice and the gateway's authentication requirement.
"""
import json
from pathlib import Path
import urllib.request
import urllib.error

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'scale-manifest.json').read_text())
assert manifest['verified'], 'Run database verification first.'
results = []
for kind, port, route in [('company', 5002, 'companies'), ('job', 5003, 'jobs')]:
    ids = manifest['ids'][kind]
    for target_port in [port]:
        for record_id in [ids[0], ids[len(ids)//2], ids[-1]]:
            url = f'http://127.0.0.1:{target_port}/api/{route}/{record_id}'
            for attempt in range(2):
                with urllib.request.urlopen(url, timeout=30) as response:
                    data = json.load(response)
                    assert response.status == 200 and data['id'] == record_id
                results.append({'url': url, 'attempt': attempt+1, 'status': 200})
        url = f'http://127.0.0.1:{target_port}/api/{route}?page=0&size=10'
        with urllib.request.urlopen(url, timeout=30) as response:
            data = json.load(response)
            assert response.status == 200 and data['content']
            assert data['totalElements'] >= len(ids)
        results.append({'url': url, 'status': 200, 'totalElements': data['totalElements']})
    url = f'http://127.0.0.1:5000/api/{route}/{ids[0]}'
    try:
        urllib.request.urlopen(url, timeout=30)
        raise AssertionError('Gateway unexpectedly permitted an unauthenticated read')
    except urllib.error.HTTPError as error:
        assert error.code == 401
        results.append({'url': url, 'status': 401, 'expected': 'Authentication required'})
report = {'database_counts': manifest['after'], 'api_reads': results}
(root / 'scale-verification.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
