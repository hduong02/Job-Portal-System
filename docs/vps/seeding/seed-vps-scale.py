"""Run with Python 3 on the Docker VPS; top up without replacing existing rows.

Usage: python3 seed-vps-scale.py [--verify-only | --resume]
Backups and the recovery manifest stay beside this script on the VPS.
Each service commits independently. A manifest prevents accidental reruns.
"""
import csv
import datetime as dt
import io
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
MANIFEST = ROOT / 'scale-manifest.json'
TARGETS = {'user': ('users', 10000), 'company': ('companies', 2000),
           'job': ('jobs', 50000), 'application': ('applications', 50000)}
SERVICES = ['user', 'company', 'resume', 'job', 'application']


def sql(service, statement):
    result = subprocess.run(['docker', 'exec', '-i', f'job-portal-{service}db',
        'psql', '-X', '-U', 'postgres', '-d', f'job_portal_{service}',
        '-v', 'ON_ERROR_STOP=1', '-Atq'], input=statement, text=True,
        capture_output=True, check=True)
    return result.stdout.strip()


def query(service, statement):
    return json.loads(sql(service, f"SELECT coalesce(json_agg(t),'[]'::json) FROM ({statement}) t;"))


def reserve(service, table, count):
    # Use the live sequence, including Hibernate's allocation increment; never reset it.
    sequence = sql(service, f"SELECT pg_get_serial_sequence('public.{table}','id');")
    if not sequence:
        sequence = f'public.{table}_seq'
    return [int(v) for v in sql(service,
        f"SELECT nextval('{sequence}') FROM generate_series(1,{count});").splitlines()]


def copy_sql(table, columns, rows):
    output = io.StringIO()
    writer = csv.writer(output, lineterminator='\n')
    for row in rows:
        writer.writerow(['\\N' if v is None else v for v in row])
    return (f"COPY {table} ({','.join(columns.split())}) FROM STDIN WITH (FORMAT csv, NULL '\\N');\n"
            + output.getvalue() + '\\.\n')


def verify():
    users = {r['id']: r for r in query('user', 'SELECT id,role FROM users')}
    companies = {r['id']: r for r in query('company', 'SELECT id,owner_id FROM companies')}
    jobs = {r['id']: r for r in query('job', 'SELECT id,company_id,employer_id FROM jobs')}
    resumes = {r['id']: r for r in query('resume', 'SELECT id,candidate_id FROM resumes')}
    applications = query('application', 'SELECT id,candidate_id,resume_id,job_id,company_id,employer_id FROM applications')
    manifest = json.loads(MANIFEST.read_text())
    for cid in manifest['ids'].get('company', []):
        assert users[companies[cid]['owner_id']]['role'] == 'ROLE_EMPLOYER'
    for jid in manifest['ids'].get('job', []):
        job = jobs[jid]
        assert companies[job['company_id']]['owner_id'] == job['employer_id']
    seeded = set(manifest['ids'].get('application', []))
    pairs = set()
    for app in applications:
        if app['id'] not in seeded:
            continue
        assert users[app['candidate_id']]['role'] == 'ROLE_JOB_SEEKER'
        assert resumes[app['resume_id']]['candidate_id'] == app['candidate_id']
        job = jobs[app['job_id']]
        assert (job['company_id'], job['employer_id']) == (app['company_id'], app['employer_id'])
        pair = (app['candidate_id'], app['job_id'])
        assert pair not in pairs
        pairs.add(pair)
    counts = {'users': len(users), 'companies': len(companies), 'jobs': len(jobs),
              'applications': len(applications), 'resumes': len(resumes)}
    for table, target in TARGETS.values():
        assert counts[table] >= target, (table, counts[table], target)
    manifest['after'] = counts
    manifest['verified'] = True
    MANIFEST.write_text(json.dumps(manifest, indent=2))
    print(json.dumps({'counts': counts, 'seeded_relationships_verified': True}, indent=2), flush=True)


def main():
    if '--verify-only' in sys.argv:
        verify()
        return
    resuming = '--resume' in sys.argv
    if MANIFEST.exists() and not resuming:
        raise SystemExit('Manifest exists. Use --verify-only or inspect it before --resume.')
    if resuming and not MANIFEST.exists():
        raise SystemExit('No manifest to resume.')
    categories = query('job', 'SELECT id FROM job_categories WHERE active=true ORDER BY id')
    skills = query('job', 'SELECT id FROM job_skills WHERE active=true ORDER BY id')
    tags = query('job', 'SELECT id FROM job_tags ORDER BY id')
    if not categories or not skills or not tags:
        raise RuntimeError('Existing categories, skills and tags are required.')
    if resuming:
        manifest = json.loads(MANIFEST.read_text())
        if manifest['verified']:
            verify()
            return
        needed = {s: len(manifest['ids'][s]) for s in TARGETS}
        # Check every reserved ID before skipping a committed phase. Ambiguous
        # commit outcomes require inspection rather than risking duplicate inserts.
        for service in SERVICES:
            table = TARGETS[service][0] if service in TARGETS else 'resumes'
            ids = manifest['ids'][service]
            found = int(sql(service, f"SELECT count(*) FROM {table} WHERE id IN ({','.join(map(str,ids))})"))
            expected = len(ids) if service in manifest['committed'] else 0
            if found != expected:
                raise RuntimeError(f'{service}: reserved IDs differ from manifest; inspect before recovery.')
    else:
        manifest, needed = prepare()

    def save():
        MANIFEST.write_text(json.dumps(manifest, indent=2))
        MANIFEST.chmod(0o600)

    uid = manifest['ids']['user']
    employers, candidates = uid[:needed['company']], uid[needed['company']:]
    rid = manifest['ids']['resume']
    now = dt.datetime.now(dt.timezone.utc).replace(tzinfo=None, microsecond=0)
    cities = ['Ho Chi Minh City', 'Hanoi', 'Da Nang', 'Bangkok', 'Singapore']
    names = ['Nguyen', 'Tran', 'Le', 'Pham', 'Hoang', 'Vu', 'Do', 'Bui']

    def commit(service, script):
        if service in manifest['committed']:
            print(f'Already committed {service}', flush=True)
            return
        (ROOT / f'{service}-scale.sql').write_text(script)
        (ROOT / f'{service}-scale.sql').chmod(0o600)
        sql(service, 'BEGIN;\n' + script + '\nCOMMIT;')
        manifest['committed'].append(service)
        save()
        print(f'Committed {service}', flush=True)

    populate(manifest, needed, uid, employers, candidates, rid, now, cities, names,
             categories, skills, tags, commit)
    for service, table in [('user','users'),('company','companies'),('resume','resumes'),('job','jobs'),('application','applications')]:
        sql(service, f'ANALYZE {table};')
    verify()


def prepare():
    ROOT.chmod(0o700)
    before = {s: int(sql(s, f'SELECT count(*) FROM {t}')) for s, (t, _) in TARGETS.items()}
    needed = {s: max(0, target - before[s]) for s, (_, target) in TARGETS.items()}
    if not all(needed.values()):
        raise SystemExit('One target already reached. This one-time seeder expects room in all four tables.')
    if needed['user'] <= needed['company']:
        raise SystemExit('Not enough new users for employers and candidates.')
    backup = ROOT / ('backups-' + dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
    backup.mkdir(mode=0o700)
    for service in SERVICES:
        with (backup / f'{service}.sql').open('wb') as output:
            subprocess.run(['docker', 'exec', f'job-portal-{service}db', 'pg_dump',
                            '-U', 'postgres', f'job_portal_{service}'], stdout=output, check=True)
        (backup / f'{service}.sql').chmod(0o600)
    manifest = {'before': before, 'targets': TARGETS, 'backup': str(backup),
                'ids': {}, 'committed': [], 'verified': False}

    def save():
        MANIFEST.write_text(json.dumps(manifest, indent=2))
        MANIFEST.chmod(0o600)

    save()
    for service, (table, _) in TARGETS.items():
        manifest['ids'][service] = reserve(service, table, needed[service])
        save()
    rid = reserve('resume', 'resumes', needed['user'] - needed['company'])
    manifest['ids']['resume'] = rid
    save()
    return manifest, needed


def populate(manifest, needed, uid, employers, candidates, rid, now, cities, names,
             categories, skills, tags, commit):

    commit('user', copy_sql('users', 'id full_name email password role status created_at updated_at',
        ([u, f'{names[i % len(names)]} Demo {i+1}', f'scale-{u}@example.invalid',
          '!disabled-synthetic-account!', 'ROLE_EMPLOYER' if i < len(employers) else 'ROLE_JOB_SEEKER',
          'INACTIVE', now - dt.timedelta(days=i % 180), now] for i, u in enumerate(uid))))
    company_ids = manifest['ids']['company']
    commit('company', copy_sql('companies',
        'id name slug owner_id description tagline company_size company_type industry_type status active is_verified founded_year email website registration_number created_at updated_at',
        ([c, f'Demo {("Technology", "Finance", "Health", "Education", "Commerce")[i%5]} Group {c}',
          f'scale-company-{c}', employers[i], 'Synthetic company for portal demonstration and performance testing.',
          'Building opportunities together', ['SMALL','MEDIUM','LARGE','ENTERPRISE'][i%4], 'PRIVATE',
          ['TECHNOLOGY','FINANCE_BANKING','HEALTHCARE','EDUCATION','RETAIL_ECOMMERCE'][i%5],
          1, True, True, 1995+i%30, f'company-{c}@example.invalid',
          f'https://company-{c}.example.invalid', f'SCALE-{c}', now-dt.timedelta(days=200), now]
         for i,c in enumerate(company_ids))))
    commit('resume', copy_sql('resumes',
        'id candidate_id title template visibility is_default is_active completion_score first_name last_name email headline city country summary created_at updated_at',
        ([r, c, 'Demo professional resume', 'PROFESSIONAL', 'PUBLIC', True, True, 60,
          names[i%len(names)], f'Demo {i+1}', f'scale-{c}@example.invalid',
          'Professional seeking new opportunities', cities[i%5], 'Vietnam' if i%5<3 else ['Thailand','Singapore'][i%5-3],
          'Synthetic candidate with collaboration, analytical and technical skills.', now-dt.timedelta(days=60), now]
         for i,(r,c) in enumerate(zip(rid,candidates)))))
    titles = ['Java Developer','Data Analyst','UX Designer','Product Manager','DevOps Engineer',
              'Frontend Developer','QA Engineer','Business Analyst','Marketing Specialist','Project Manager']
    jid = manifest['ids']['job']
    job_rows = []
    for i,j in enumerate(jid):
        ci = i % len(company_ids)
        created = now-dt.timedelta(days=i%60)
        job_rows.append([j, titles[i%10], 'Join our demo team to build useful products and deliver excellent customer experiences.',
            'Relevant experience, clear communication and strong problem solving skills.',
            'Collaborate with the team, deliver quality work and improve processes.',
            'Health insurance, paid leave, training budget and flexible hours.', company_ids[ci], employers[ci],
            categories[i%len(categories)]['id'], cities[i%5], 'Vietnam' if i%5<3 else ['Thailand','Singapore'][i%5-3],
            ['FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP'][i%4], ['REMOTE','HYBRID','ON_SITE'][i%3],
            ['ENTRY_LEVEL','JUNIOR','MID_LEVEL','SENIOR_LEVEL','LEAD'][i%5],
            'OPEN', True, 1+i%5, 800+(i%20)*100, 1500+(i%20)*150,
            (now+dt.timedelta(days=30+i%60)).date(), (now+dt.timedelta(days=90)).date(), created, created, now])
    script = copy_sql('jobs',
        'id title description requirements responsibilities benefits company_id employer_id category_id city country job_type work_mode experience_level status active openings min_salary max_salary application_deadline expires_at created_at published_at updated_at', job_rows)
    script += copy_sql('jobs_skills', 'job_id skills_id',
        ([j,skills[(i+k)%len(skills)]['id']] for i,j in enumerate(jid) for k in range(min(3,len(skills)))))
    script += copy_sql('jobs_tags', 'job_id tags_id', ([j,tags[i%len(tags)]['id']] for i,j in enumerate(jid)))
    commit('job', script)
    app_rows = []
    statuses = ['PENDING','PENDING','REVIEWING','SHORTLISTED','INTERVIEW_SCHEDULED','REJECTED','HIRED']
    for i,a in enumerate(manifest['ids']['application']):
        ji = i % len(jid)
        ci = ji % len(company_ids)
        candidate_index = (i + i//len(jid)) % len(candidates)
        applied = min(now, job_rows[ji][-3] + dt.timedelta(hours=1+i%12))
        app_rows.append([a,candidates[candidate_index],rid[candidate_index],jid[ji],company_ids[ci],employers[ci],
            statuses[i%len(statuses)], 'Synthetic application: interested in contributing my skills to your team.',
            1000+i%20*100, (now+dt.timedelta(days=14)).date(), i%20==0, None, 0, applied, applied])
    commit('application', copy_sql('applications',
        'id candidate_id resume_id job_id company_id employer_id status cover_letter expected_salary available_from is_starred ai_score ai_short_list_status applied_at updated_at', app_rows))


if __name__ == '__main__':
    main()
