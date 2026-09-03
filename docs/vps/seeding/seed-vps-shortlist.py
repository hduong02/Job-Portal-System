"""Create an isolated shortlist load-test dataset on the Docker VPS.

Run on the VPS with Python 3. No screening, status changes, or mail are triggered.
SQL backups, credentials, the recovery manifest, and an ID CSV stay on the VPS.
Reruns recover committed phases using reserved IDs, without resetting sequences.
"""
import csv
import datetime as dt
import io
import json
from pathlib import Path
import secrets
import subprocess
import urllib.request

ROOT = Path('/root/job-portal-shortlist-loadtest-20261007')
MANIFEST = ROOT / 'manifest.json'
SLUG = 'shortlist-loadtest-20261007'
NAME = 'Shortlist Load Test 2026-10-07'


def sql(service, statement):
    result = subprocess.run([
        'docker', 'exec', '-i', f'job-portal-{service}db', 'psql', '-X',
        '-U', 'postgres', '-d', f'job_portal_{service}',
        '-v', 'ON_ERROR_STOP=1', '-Atq'], input=statement, text=True,
        capture_output=True)
    if result.returncode:
        raise RuntimeError(f'{service}: {result.stderr}')
    return result.stdout.strip()


def query(service, statement):
    return json.loads(sql(service,
        f"SELECT coalesce(json_agg(t),'[]'::json) FROM ({statement}) t"))


def write_private(path, data):
    # Atomic replacement keeps the last complete recovery state on interruption.
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.touch(mode=0o600, exist_ok=True)
    temporary.chmod(0o600)
    temporary.write_text(data)
    temporary.replace(path)


def save(manifest):
    write_private(MANIFEST, json.dumps(manifest, indent=2))


def reserve(service, table, count):
    sequence = sql(service, f"SELECT pg_get_serial_sequence('public.{table}','id')")
    sequence = sequence or f'public.{table}_seq'
    return [int(i) for i in sql(service,
        f"SELECT nextval('{sequence}') FROM generate_series(1,{count})").splitlines()]


def copy_rows(table, columns, rows):
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator='\n')
    for row in rows:
        writer.writerow(['\\N' if value is None else value for value in row])
    return (f"COPY {table} ({','.join(columns.split())}) FROM STDIN "
            "WITH (FORMAT csv, NULL '\\N');\n" + buffer.getvalue() + '\\.\n')


def http(url, body=None, headers=None):
    request = urllib.request.Request(url,
        data=json.dumps(body).encode() if body is not None else None,
        headers={'Content-Type': 'application/json', **(headers or {})})
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.load(response)


def commit_reserved(manifest, service, table, ids, script):
    found = int(sql(service,
        f"SELECT count(*) FROM {table} WHERE id IN ({','.join(map(str, ids))})"))
    if service in manifest['committed']:
        if found != len(ids):
            raise RuntimeError(f'{service}: committed rows missing')
        return
    if found == len(ids):
        # Covers a successful transaction whose manifest update was interrupted.
        print(f'Recovered committed {service} phase', flush=True)
    elif found:
        raise RuntimeError(f'{service}: partial reserved-ID collision; inspect before recovery')
    else:
        write_private(ROOT / f'{service}-seed.sql', 'BEGIN;\n' + script + '\nCOMMIT;')
        sql(service, 'BEGIN;\n' + script + '\nCOMMIT;')
        print(f'Committed {service}: {len(ids)} records', flush=True)
    manifest['committed'].append(service)
    save(manifest)


def candidate_table(manifest):
    return ('CREATE TEMP TABLE seed_candidates (candidate_id bigint PRIMARY KEY, resume_id bigint NOT NULL);\n'
            + copy_rows('seed_candidates', 'candidate_id resume_id',
                        sorted((int(c), r) for c, r in manifest['resume_by_candidate'].items())))


def main():
    ROOT.mkdir(mode=0o700, exist_ok=True)
    ROOT.chmod(0o700)
    if MANIFEST.exists():
        manifest = json.loads(MANIFEST.read_text())
    else:
        candidates = query('user', "SELECT id FROM users WHERE role='ROLE_JOB_SEEKER' ORDER BY id")
        if len(candidates) != 7999:
            raise RuntimeError(f'Expected 7,999 job seekers; found {len(candidates)}')
        if int(sql('company', f"SELECT count(*) FROM companies WHERE slug='{SLUG}'")):
            raise RuntimeError('Test company exists without a recovery manifest')
        backup = ROOT / 'backups'
        backup.mkdir(mode=0o700)
        before = {}
        for service, table in [('user', 'users'), ('company', 'companies'),
                               ('job', 'jobs'), ('resume', 'resumes'), ('application', 'applications')]:
            before[table] = int(sql(service, f'SELECT count(*) FROM {table}'))
            path = backup / f'{service}.sql'
            with path.open('wb') as output:
                path.chmod(0o600)
                subprocess.run(['docker', 'exec', f'job-portal-{service}db',
                    'pg_dump', '-U', 'postgres', f'job_portal_{service}'], stdout=output, check=True)
        manifest = {'name': NAME, 'slug': SLUG, 'before': before,
                    'candidate_ids': [c['id'] for c in candidates],
                    'committed': [], 'verified': False}
        save(manifest)
        print('Created backups and candidate snapshot', flush=True)

    credentials_path = ROOT / 'employer-credentials.json'
    if credentials_path.exists():
        credentials = json.loads(credentials_path.read_text())
    else:
        credentials = {'email': f'{SLUG}@example.invalid', 'password': secrets.token_urlsafe(32)}
        write_private(credentials_path, json.dumps(credentials, indent=2))
    if 'employer_id' not in manifest:
        existing = query('user', f"SELECT id,role FROM users WHERE email='{credentials['email']}'")
        if existing:
            if existing[0]['role'] != 'ROLE_EMPLOYER':
                raise RuntimeError('Test email has an unexpected role')
            auth = http('http://127.0.0.1:5001/auth/login', credentials)
        else:
            auth = http('http://127.0.0.1:5001/auth/signup', {
                **credentials, 'fullName': 'Shortlist Load Test Employer', 'role': 'ROLE_EMPLOYER'})
        manifest['employer_id'] = auth['user']['id']
        write_private(ROOT / 'employer-auth.json', json.dumps(auth))
        save(manifest)
        print(f"Created test employer: {manifest['employer_id']}", flush=True)

    now = dt.datetime.now(dt.timezone.utc).replace(tzinfo=None, microsecond=0)
    owner = manifest['employer_id']
    if 'company_id' not in manifest:
        manifest['company_id'] = reserve('company', 'companies', 1)[0]
        save(manifest)
    company = manifest['company_id']
    commit_reserved(manifest, 'company', 'companies', [company], copy_rows('companies',
        'id name slug owner_id description tagline company_size company_type industry_type status active is_verified founded_year email website registration_number created_at updated_at',
        [[company, NAME, SLUG, owner, 'Synthetic company for shortlist, Kafka and mail load testing.',
          'Performance test dataset', 'LARGE', 'PRIVATE', 'TECHNOLOGY', 1, True, True, 2026,
          credentials['email'], 'https://shortlist-loadtest.example.invalid', SLUG.upper(), now, now]]))

    if 'job_ids' not in manifest:
        categories = query('job', 'SELECT id FROM job_categories WHERE active=true ORDER BY id LIMIT 1')
        if not categories:
            raise RuntimeError('A valid job category is required')
        manifest['category_id'] = categories[0]['id']
        manifest['job_ids'] = reserve('job', 'jobs', 30)
        save(manifest)
    jobs = manifest['job_ids']
    commit_reserved(manifest, 'job', 'jobs', jobs, copy_rows('jobs',
        'id title description requirements responsibilities benefits company_id employer_id category_id city country job_type work_mode experience_level status active openings min_salary max_salary application_deadline expires_at created_at published_at updated_at',
        [[j, f'Shortlist Load Test Job {i:02d}', 'Synthetic vacancy for shortlist throughput testing.',
          'Test dataset: no real hiring activity.', 'Load test candidate pipeline.', 'Test role.',
          company, owner, manifest['category_id'], 'Bangkok', 'Thailand', 'FULL_TIME', 'REMOTE',
          'MID_LEVEL', 'OPEN', True, 10000, 1000, 2000,
          now.date() + dt.timedelta(days=180), now.date() + dt.timedelta(days=180), now, now, now]
         for i, j in enumerate(jobs, 1)]))

    if 'resume_by_candidate' not in manifest:
        resumes = query('resume', 'SELECT DISTINCT ON (candidate_id) candidate_id,id FROM resumes '
                        'ORDER BY candidate_id, is_default DESC, is_active DESC, id')
        mapping = {r['candidate_id']: r['id'] for r in resumes}
        missing = [c for c in manifest['candidate_ids'] if c not in mapping]
        new_ids = reserve('resume', 'resumes', len(missing)) if missing else []
        mapping.update(zip(missing, new_ids))
        manifest['resume_by_candidate'] = {str(c): mapping[c] for c in manifest['candidate_ids']}
        manifest['new_resumes'] = dict(zip(map(str, missing), new_ids))
        save(manifest)
    if manifest['new_resumes']:
        commit_reserved(manifest, 'resume', 'resumes', list(manifest['new_resumes'].values()),
            copy_rows('resumes',
                'id candidate_id title template visibility is_default is_active completion_score first_name last_name headline city country summary created_at updated_at',
                [[r, int(c), 'Shortlist Load Test Resume', 'PROFESSIONAL', 'PRIVATE', False, True,
                  0, 'Test', 'Candidate', 'Synthetic resume for load-test applications', 'Bangkok',
                  'Thailand', 'Added because this candidate had no resume; test fixture only.', now, now]
                 for c, r in manifest['new_resumes'].items()]))

    expected = len(manifest['candidate_ids']) * len(jobs)
    found = int(sql('application', f'SELECT count(*) FROM applications WHERE company_id={company}'))
    if found not in (0, expected):
        raise RuntimeError(f'Unexpected application count {found}; inspect before recovery')
    if not found:
        script = ('BEGIN;\n' + candidate_table(manifest)
            + 'CREATE TEMP TABLE seed_jobs (job_id bigint PRIMARY KEY);\n'
            + copy_rows('seed_jobs', 'job_id', [[j] for j in jobs])
            + 'INSERT INTO applications (candidate_id,resume_id,job_id,company_id,employer_id,status,'
              'cover_letter,is_starred,applied_at,updated_at)\n'
            + f"SELECT c.candidate_id,c.resume_id,j.job_id,{company},{owner},'PENDING',"
              "'Synthetic shortlist load-test application.',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP "
              'FROM seed_candidates c CROSS JOIN seed_jobs j ORDER BY j.job_id,c.candidate_id;\nCOMMIT;')
        write_private(ROOT / 'application-seed.sql', script)
        sql('application', script)
        print(f'Committed applications: {expected}', flush=True)
    if 'application' not in manifest['committed']:
        manifest['committed'].append('application')
        save(manifest)

    verify(manifest)


def verify(manifest):
    company, owner = manifest['company_id'], manifest['employer_id']
    expected = len(manifest['candidate_ids']) * 30
    jobs = query('job', f'SELECT id,company_id,employer_id,status,active FROM jobs WHERE company_id={company}')
    assert {j['id'] for j in jobs} == set(manifest['job_ids'])
    assert all(j['employer_id'] == owner and j['status'] == 'OPEN' and j['active'] for j in jobs)
    users = query('user', "SELECT id FROM users WHERE role='ROLE_JOB_SEEKER' ORDER BY id")
    assert set(manifest['candidate_ids']).issubset({u['id'] for u in users})
    resumes = {r['id']: r['candidate_id'] for r in query('resume', 'SELECT id,candidate_id FROM resumes')}
    assert all(resumes[r] == int(c) for c, r in manifest['resume_by_candidate'].items())
    checks = sql('application', 'BEGIN;\n' + candidate_table(manifest) + f"""
SELECT count(*) || '|' || count(DISTINCT a.candidate_id) || '|' || count(DISTINCT a.job_id)
FROM applications a WHERE a.company_id={company};
SELECT count(*) FROM applications a LEFT JOIN seed_candidates c ON c.candidate_id=a.candidate_id
WHERE a.company_id={company} AND (c.candidate_id IS NULL OR a.resume_id<>c.resume_id
 OR a.employer_id<>{owner} OR a.job_id NOT IN ({','.join(map(str, manifest['job_ids']))}));
SELECT count(*) FROM (SELECT candidate_id,job_id,count(*) FROM applications
 WHERE company_id={company} GROUP BY candidate_id,job_id HAVING count(*)<>1) d;
SELECT count(*) FROM (SELECT candidate_id,count(*) FROM applications
 WHERE company_id={company} GROUP BY candidate_id HAVING count(*)<>30) d;
COMMIT;
""").splitlines()
    assert checks == [f'{expected}|7999|30', '0', '0', '0'], checks
    path = ROOT / 'applications.csv'
    with path.open('wb') as output:
        path.chmod(0o600)
        subprocess.run(['docker', 'exec', 'job-portal-applicationdb', 'psql', '-X', '-U', 'postgres',
            '-d', 'job_portal_application', '-v', 'ON_ERROR_STOP=1', '-c',
            f'COPY (SELECT id,candidate_id,job_id FROM applications WHERE company_id={company} ORDER BY id) '
            'TO STDOUT WITH CSV HEADER'], stdout=output, check=True)
    samples = query('application', f'SELECT id FROM applications WHERE company_id={company} ORDER BY id LIMIT 3')
    api_checks = []
    company_response = http(f'http://127.0.0.1:5002/api/companies/{company}')
    assert company_response['id'] == company
    api_checks.append({'resource': 'company', 'id': company, 'status': 200})
    for job in (manifest['job_ids'][0], manifest['job_ids'][15], manifest['job_ids'][-1]):
        result = http(f'http://127.0.0.1:5003/api/jobs/{job}')
        assert result['id'] == job and result['company']['id'] == company
        api_checks.append({'resource': 'job', 'id': job, 'status': 200})
    for sample in samples:
        result = http(f"http://127.0.0.1:5005/api/applications/{sample['id']}", headers={'X-User-Id': str(owner)})
        assert result['id'] == sample['id']
        api_checks.append({'resource': 'application', 'id': sample['id'], 'status': 200})
    counts = {table: int(sql(service, f'SELECT count(*) FROM {table}')) for service, table in
              [('user','users'), ('company','companies'), ('job','jobs'), ('resume','resumes'), ('application','applications')]}
    for service, table in [('company','companies'), ('job','jobs'), ('resume','resumes'), ('application','applications')]:
        sql(service, f'ANALYZE {table}')
    manifest.update(verified=True, after=counts, applications=expected, api_checks=api_checks)
    save(manifest)
    report = {'company_name': NAME, 'company_id': company, 'employer_id': owner,
              'jobs': len(jobs), 'candidates': len(manifest['candidate_ids']), 'applications': expected,
              'new_resumes': len(manifest['new_resumes']), 'counts': counts, 'api_checks': api_checks,
              'manifest': str(MANIFEST), 'application_csv': str(path)}
    write_private(ROOT / 'verification.json', json.dumps(report, indent=2))
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    main()
