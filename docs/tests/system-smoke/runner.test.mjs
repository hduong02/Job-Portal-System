import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { Blocked, configFromEnv, exposesTrace, mailMatches, SmokeRun } from './lib.mjs';
import { runSuite } from './run.mjs';

const config = configFromEnv({ BASE_URL: 'https://gateway.example.test', MAILHOG_URL: 'http://mail.example.test',
  ADMIN_EMAIL: 'admin@example.test', ADMIN_PASSWORD: 'secret-admin-password', SMOKE_PASSWORD: 'secret-fixture-password' });

// Stateful HTTP contract fixture, independent of Spring internals. No network or real Gemini calls.
function fixture({ duplicate500 = false, duplicateSuccess = false, leak = false, noEmail = false, noScreening = false,
  failDeleteJob = false, denyMailbox = false, identityBug = false, aiTransportFailure = false, noAiEnvelope = false } = {}) {
  let nextId = 1, now = 0;
  const users = new Map([[1, { id: 1, email: config.adminEmail, role: 'ROLE_ADMIN', status: 'ACTIVE' }]]);
  const tokens = new Map([['admin-token', users.get(1)]]);
  const stores = Object.fromEntries(['companies', 'jobs', 'resumes', 'applications', 'saved-jobs', 'job-categories', 'job-skills', 'job-tags'].map(kind => [kind, new Map()]));
  const children = new Map(), notes = new Map(), mails = [], calls = [];
  const response = (status, json) => new Response(JSON.stringify(json), { status, headers: { 'Content-Type': 'application/json' } });
  const fail = status => response(status, { status, message: 'Contract fixture rejected request', ...(leak ? { trace: 'at org.example.Service.run(Service.java:42)' } : {}) });
  const create = (map, body, extra = {}) => { const value = { ...body, ...extra, id: ++nextId }; map.set(value.id, value); return value; };
  const page = items => ({ content: items, totalPages: items.length ? 1 : 0, totalElements: items.length });
  const fetchImpl = async (urlString, options) => {
    const url = new URL(urlString), path = url.pathname, method = options.method;
    const body = options.body ? JSON.parse(options.body) : {};
    const actor = tokens.get(options.headers.Authorization?.replace('Bearer ', ''));
    calls.push({ method, path, headers: options.headers });
    if (url.hostname === 'mail.example.test') {
      assert.equal(options.headers.Authorization, undefined, 'Gateway token leaked to MailHog');
      return denyMailbox ? fail(401) : response(200, { items: noEmail ? [] : mails });
    }
    if (path === '/actuator/health') return response(200, { status: 'UP' });
    if (path === '/auth/signup') {
      const user = create(users, body, { status: 'ACTIVE' });
      const jwt = `token-${user.id}`; tokens.set(jwt, user);
      return response(200, { user, jwt });
    }
    if (path === '/auth/login') {
      const user = [...users.values()].find(user => user.email === body.email);
      if (!user || body.password !== (user.role === 'ROLE_ADMIN' ? config.adminPassword : config.smokePassword)) return fail(401);
      if (user.status !== 'ACTIVE') return fail(403);
      const jwt = user.role === 'ROLE_ADMIN' ? 'admin-token' : `token-${user.id}`; tokens.set(jwt, user);
      return response(200, { user, jwt });
    }
    if (!actor) return fail(401);
    if (path === '/api/users/profile') {
      if (method === 'PUT') Object.assign(actor, body);
      return response(200, identityBug && options.headers['X-User-Id'] ? users.get(Number(options.headers['X-User-Id'])) : actor);
    }
    if (path === '/api/users') return actor.role === 'ROLE_ADMIN' ? response(200, page([...users.values()])) : fail(403);
    const userRoute = path.match(/^\/api\/users\/(\d+)(?:\/(suspend|activate))?$/);
    if (userRoute) {
      const user = users.get(Number(userRoute[1]));
      if (method === 'GET') return response(200, user);
      if (actor.role !== 'ROLE_ADMIN') return fail(403);
      user.status = method === 'DELETE' ? 'DELETED' : userRoute[2] === 'suspend' ? 'SUSPENDED' : 'ACTIVE';
      return response(200, user);
    }
    if (path === '/api/ai/resume/summary') {
      if (aiTransportFailure) throw new Error('Provider timeout');
      return response(200, { content: 'Experienced Java developer.' });
    }
    const segments = path.split('/').filter(Boolean);
    if (segments[1] === 'preferences') segments.splice(1, 1);
    const [, kind, idText, action, childText] = segments;
    const store = stores[kind], id = Number(idText), value = store?.get(id);
    if (!store) throw new Error(`Unrecognized fixture route ${method} ${path}`);
    if (['job-categories', 'job-skills', 'job-tags'].includes(kind) && method !== 'GET' && actor.role !== 'ROLE_ADMIN') return fail(403);
    if (kind === 'companies' && ['verify', 'deactivate'].includes(action) && actor.role !== 'ROLE_ADMIN') return fail(403);
    if (kind === 'companies' && idText === 'my') return response(200, [...store.values()].find(company => company.ownerId === actor.id));
    if (kind === 'jobs' && idText === 'company') return response(200, page([...store.values()].filter(job => job.company.id === Number(action))));
    if (kind === 'saved-jobs' && idText === 'check') return response(200, [...store.values()].some(item => item.candidateId === actor.id && item.jobId === Number(url.searchParams.get('jobId'))));
    if (kind === 'applications' && idText === 'job') {
      if (stores.jobs.get(Number(action)).employerId !== actor.id) return fail(403);
      return response(200, page([...store.values()].filter(app => app.job.id === Number(action))));
    }
    if (kind === 'applications' && idText === 'company') return response(200, page([...store.values()].filter(app => app.employerId === actor.id && app.isStarred)));
    if (kind === 'resumes' && action && !['personal-info', 'summary', 'set-default'].includes(action)) {
      if (value.candidateId !== actor.id) return fail(403);
      const key = `${id}/${action}`;
      if (!children.has(key)) children.set(key, new Map());
      const map = children.get(key);
      if (method === 'POST') return response(action === 'skills' ? 201 : 200, create(map, body));
      if (method === 'DELETE') { map.delete(Number(childText)); return response(200, { status: true }); }
      return response(200, [...map.values()]);
    }
    if (kind === 'applications' && action === 'notes') {
      if (value.employerId !== actor.id) return fail(403);
      if (!notes.has(id)) notes.set(id, new Map());
      const map = notes.get(id);
      if (method === 'POST') return response(201, create(map, body));
      if (method === 'DELETE') { map.delete(Number(childText)); return response(200, { status: true }); }
      return response(200, [...map.values()]);
    }
    if (method === 'POST' && !idText) {
      if (kind === 'companies') return response(201, create(store, body, { ownerId: actor.id, status: 'PENDING_VERIFICATION', active: true }));
      if (kind === 'jobs') {
        const company = [...stores.companies.values()].find(company => company.ownerId === actor.id);
        return response(201, create(store, body, { company, employerId: actor.id, status: 'DRAFT', active: true,
          category: stores['job-categories'].get(body.categoryId), skills: body.skillIds.map(id => stores['job-skills'].get(id)), tags: body.tagIds.map(id => stores['job-tags'].get(id)) }));
      }
      if (kind === 'resumes') return response(200, create(store, body, { candidateId: actor.id, active: true }));
      if (kind === 'saved-jobs') return response(200, create(store, body, { candidateId: actor.id }));
      if (kind === 'applications') {
        if (!body.jobId || !body.resumeId) return fail(400);
        if (!duplicateSuccess && [...store.values()].some(app => app.candidate.id === actor.id && app.job.id === body.jobId)) return fail(duplicate500 ? 500 : 409);
        const job = stores.jobs.get(body.jobId);
        if (job.status !== 'OPEN') return fail(400);
        return response(200, create(store, body, { candidate: actor, job, company: job.company,
          employerId: job.employerId, status: 'PENDING', isStarred: false }));
      }
      return response(201, create(store, body));
    }
    if (method === 'GET' && (!idText || idText === 'my')) {
      let items = [...store.values()];
      if (kind === 'resumes') items = items.filter(item => item.candidateId === actor.id && item.active);
      if (kind === 'applications') items = items.filter(item => item.candidate.id === actor.id);
      if (kind === 'saved-jobs') items = items.filter(item => item.candidateId === actor.id);
      if (kind === 'jobs') items = items.filter(item => !url.searchParams.get('keyword') || item.title.includes(url.searchParams.get('keyword')));
      return response(200, kind.startsWith('job-') ? items : page(items));
    }
    if (!value) return fail(404);
    const owner = kind === 'resumes' || kind === 'saved-jobs' ? value.candidateId
      : kind === 'companies' ? value.ownerId : value.employerId;
    if (kind === 'applications') {
      const canAccess = method === 'GET' ? actor.id === owner || actor.id === value.candidate.id
        : method === 'DELETE' || action === 'withdraw' ? actor.id === value.candidate.id : actor.id === owner;
      if (!canAccess) return fail(403);
    } else if ((kind === 'resumes' || method !== 'GET') && !kind.startsWith('job-')
      && !['verify', 'deactivate'].includes(action) && owner !== actor.id) return fail(403);
    if (method === 'DELETE') {
      if (kind === 'jobs' && failDeleteJob) return fail(500);
      if (kind === 'resumes') value.active = false; else store.delete(id);
      return response(200, { status: true });
    }
    if (method === 'PUT') {
      if (action === 'personal-info') value.personalInfo = body; else Object.assign(value, body);
    }
    if (method === 'PATCH') {
      if (action === 'verify') value.status = 'ACTIVE';
      if (action === 'deactivate') value.status = 'SUSPENDED';
      if (action === 'publish') value.status = 'OPEN';
      if (action === 'close') { value.status = 'CLOSED'; value.active = false; }
      if (action === 'summary') value.summary = url.searchParams.get('summary');
      if (action === 'set-default') value.isDefault = true;
      if (action === 'star') value.isStarred = !value.isStarred;
      if (action === 'withdraw') value.status = 'WITHDRAWN';
      if (action === 'status') {
        value.status = body.status;
        const email = value.candidate.email.split('@');
        mails.push({ ID: `mail-${id}`, To: [{ Mailbox: email[0], Domain: email[1] }],
          Content: { Body: `<p>${value.job.title} at ${value.company.name}: Shortlisted</p>` } });
        return response(200, { id, status: value.status, updatedAt: '2026-10-12T00:00:00' });
      }
    }
    if (kind === 'resumes') for (const [suffix, field] of [['educations', 'educations'], ['work-experiences', 'workExperiences'], ['skills', 'skills'], ['projects', 'projects'], ['languages', 'languages']])
      value[field] = [...(children.get(`${id}/${suffix}`)?.values() || [])];
    if (kind === 'applications' && method === 'GET' && !noScreening) {
      value.aiScore = 80; value.aiShortListStatus = 'REVIEW_RECOMMENDED';
      value.screening = { id: 900 + id, overallScore: 80, skillsMatchScore: 85, experienceMatchScore: 80,
        educationMatchScore: 75, shortListStatus: 'REVIEW_RECOMMENDED', summary: 'Good fit',
        matchedSkills: ['Java'], missingSkills: [], strengths: ['Backend'], concerns: [] };
      if (noAiEnvelope) { value.aiScore = null; value.aiShortListStatus = null; }
    }
    return response(200, value);
  };
  return { fetchImpl, stores, users, calls, now: () => now, sleep: async ms => { now += ms; },
    allowJobDeletion: () => { failDeleteJob = false; } };
}

async function simulate(options = {}, extra = {}) {
  const fake = fixture(options);
  const result = await runSuite({ ...config, asyncMs: 10, pollMs: 2 }, {
    ...fake, runId: 'smoke-offline-fixture', writeReports: false, log: () => {}, ...extra });
  return { ...result, fake };
}

test('complete contract journey passes, cleans records, and retains soft deletions', async () => {
  const { exitCode, report, fake } = await simulate();
  assert.equal(exitCode, 0, JSON.stringify(report.stages.filter(stage => stage.status !== 'PASS')));
  assert.equal(report.status, 'PASS');
  assert.ok(report.stages.some(stage => stage.name === 'Kafka to captured email' && stage.status === 'PASS'));
  assert.ok(report.resources.every(resource => ['DELETED', 'SOFT_DELETED'].includes(resource.cleanup)));
  for (const [name, store] of Object.entries(fake.stores)) if (name !== 'resumes') assert.equal(store.size, 0, name);
  assert.ok([...fake.users.values()].filter(user => user.role !== 'ROLE_ADMIN').every(user => user.status === 'DELETED'));
});

test('server errors and trace leaks fail without hiding subsequent checks or cleanup', async () => {
  const { exitCode, report } = await simulate({ duplicate500: true, leak: true });
  assert.equal(exitCode, 1);
  assert.ok(report.stages.some(stage => stage.name === 'Security: Duplicate application' && stage.status === 'FAIL'));
  assert.ok(report.checks.some(check => check.name.includes('stack traces') && !check.passed));
  assert.ok(report.stages.some(stage => stage.name === 'Administrator moderation'));
  assert.ok(report.checks.some(check => check.name === 'Company deactivation persisted' && check.passed));
  assert.equal(report.leftovers.length, 0);
});

for (const [option, stage] of [['noEmail', 'Kafka to captured email'], ['noScreening', 'Live asynchronous screening']]) {
  test(`${option} times out and cannot produce a full pass`, async () => {
    const { exitCode, report } = await simulate({ [option]: true });
    assert.equal(exitCode, 1);
    assert.ok(report.stages.some(row => row.name === stage && row.error.includes('timed out')));
    assert.equal(report.leftovers.length, 0);
  });
}

test('missing mailbox access blocks before creating any fixture', async () => {
  const { exitCode, report, fake } = await simulate({ denyMailbox: true });
  assert.equal(exitCode, 2);
  assert.equal(report.resources.length, 0);
  assert.equal(fake.users.size, 1);
});

test('failed cleanup preserves dependent parents and employer recovery access', async () => {
  const { exitCode, report, fake } = await simulate({ failDeleteJob: true });
  assert.equal(exitCode, 1);
  assert.ok(report.leftovers.some(item => item.kind === 'job' && item.cleanup === 'FAILED'));
  assert.ok(report.leftovers.some(item => item.kind === 'company' && item.cleanup === 'BLOCKED'));
  assert.ok([...fake.users.values()].some(user => user.role === 'ROLE_EMPLOYER' && user.status === 'ACTIVE'));
});

test('forged header identity bug is detected', async () => {
  const { exitCode, report } = await simulate({ identityBug: true });
  assert.equal(exitCode, 1);
  assert.ok(report.checks.some(check => check.name === 'Profile identity follows JWT' && !check.passed));
});

test('unexpected successful duplicate creation is captured and cleaned', async () => {
  const { exitCode, report, fake } = await simulate({ duplicateSuccess: true });
  assert.equal(exitCode, 1);
  assert.equal(report.resources.filter(item => item.kind === 'application').length, 2);
  assert.equal(fake.stores.applications.size, 0);
  assert.equal(report.leftovers.length, 0);
});

test('AI transport failure fails the run while allowing fixture cleanup', async () => {
  const { exitCode, report } = await simulate({ aiTransportFailure: true });
  assert.equal(exitCode, 1);
  assert.equal(report.leftovers.length, 0);
  assert.ok(report.uncertainMutations.some(item => item.path === '/api/ai/resume/summary'));
});

test('completed screening with missing top-level AI fields reports inconsistency immediately', async () => {
  const { exitCode, report } = await simulate({ noAiEnvelope: true });
  assert.equal(exitCode, 1);
  const stage = report.stages.find(stage => stage.name === 'Live asynchronous screening');
  assert.equal(stage.error, 'AI shortlist agrees with score');
  assert.ok(report.retained.some(item => item.kind === 'screening'));
  assert.equal(report.leftovers.length, 0);
});

test('uncertain record creation blocks deletion until reconciliation', async () => {
  const run = new SmokeRun(config, { fetchImpl: async () => { throw new Error('Must not delete'); }, log: () => {} });
  run.actors.candidate = { id: 4, email: 'smoke-test.candidate@example.com', jwt: 'private-token' };
  await run.remember('resume', 3, '/api/resumes/3', 'candidate');
  run.report.uncertainMutations.push({ method: 'POST', path: '/api/applications', actor: 'candidate' });
  await run.cleanup();
  assert.equal(run.report.resources[0].cleanup, 'BLOCKED');
  assert.equal(run.report.requests.length, 0);
  assert.equal(await run.finish(), 1);
});

test('reports and recovery manifest redact secrets, JWTs, and response bodies', async () => {
  const output = await mkdtemp(join(tmpdir(), 'system-smoke-test-'));
  try {
    const { report } = await simulate({}, { output });
    const text = (await Promise.all(['report.json', 'manifest.json', 'report.md'].map(name => readFile(join(output, name), 'utf8')))).join('\n');
    assert.ok(!text.includes(config.adminPassword));
    assert.ok(!text.includes(config.smokePassword));
    assert.ok(!text.includes('admin-token'));
    assert.ok(!text.includes('token-'));
    assert.ok(!text.includes('Experienced Java developer.'));
    assert.ok(report.requests.length > 100);
  } finally { await rm(output, { recursive: true, force: true }); }
});

test('common-word passwords do not corrupt actor names or recovery emails', async () => {
  const output = await mkdtemp(join(tmpdir(), 'system-smoke-redaction-'));
  try {
    const run = new SmokeRun({ ...config, adminPassword: 'admin', smokePassword: 'candidate' }, { output, log: () => {} });
    run.actors.admin = { id: 1, email: 'admin@example.test', jwt: 'never-save-this-token' };
    run.actors.candidate = { id: 2, email: 'smoke-test.candidate@example.test', jwt: 'private-token' };
    await run.remember('user', 2, '/api/users/2', 'admin');
    await run.stage('Non-admin access', async () => { throw new Error('password=admin'); });
    const manifest = JSON.parse(await readFile(join(output, 'manifest.json'), 'utf8'));
    const report = JSON.parse(await readFile(join(output, 'report.json'), 'utf8'));
    assert.equal(manifest.actors.admin.email, 'admin@example.test');
    assert.equal(manifest.actors.candidate.id, 2);
    assert.equal(manifest.resources[0].actor, 'admin');
    assert.equal(report.stages[0].name, 'Non-admin access');
    assert.equal(report.stages[0].error, 'password=[REDACTED]');
    assert.ok(!JSON.stringify(manifest).includes('never-save-this-token'));
  } finally { await rm(output, { recursive: true, force: true }); }
});

test('transport failure on a mutation is journaled and is never retried', async () => {
  let calls = 0;
  const run = new SmokeRun(config, { fetchImpl: async () => { calls++; throw new Error(config.adminPassword); }, log: () => {} });
  await run.stage('Apply', () => run.request('POST', '/api/applications', { body: { jobId: 1 } }));
  assert.equal(await run.finish(), 1);
  assert.equal(calls, 1);
  assert.equal(run.report.uncertainMutations.length, 1);
  assert.ok(!JSON.stringify(run.report).includes(config.adminPassword));
});

test('configuration rejects absent values and credential-bearing URLs', () => {
  assert.throws(() => configFromEnv({}), Blocked);
  assert.throws(() => configFromEnv({ BASE_URL: 'https://user:secret@example.test', MAILHOG_URL: config.mailhogUrl,
    ADMIN_EMAIL: config.adminEmail, ADMIN_PASSWORD: config.adminPassword, SMOKE_PASSWORD: config.smokePassword }), Blocked);
});

test('MIME matching checks recipient, job, company, status and encoded bodies', () => {
  const context = { email: 'candidate@example.com', title: 'Java Job', company: 'Smoke Company' };
  const mail = { To: [{ Mailbox: 'candidate', Domain: 'example.com' }], MIME: { Parts: [{
    Headers: { 'Content-Transfer-Encoding': ['base64'] }, Body: Buffer.from('Java Job at Smoke Company: Shortlisted').toString('base64') }] } };
  assert.equal(mailMatches(mail, context), true);
  assert.equal(mailMatches(mail, { ...context, email: 'other@example.com' }), false);
  assert.equal(mailMatches(mail, { ...context, title: 'Other Job' }), false);
  assert.equal(exposesTrace('ordinary error', { error: { stackTrace: ['secret'] } }), true);
});

async function withServer(handler, action) {
  const server = createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { return await action(`http://127.0.0.1:${server.address().port}`); }
  finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
}

test('real fetch refuses redirects and enforces request timeouts', async () => {
  let forwarded = false;
  await withServer((request, response) => {
    if (request.url === '/redirect') { response.writeHead(302, { Location: '/capture' }); response.end(); }
    else if (request.url === '/capture') { forwarded = true; response.end('{}'); }
    // /hang deliberately holds the response open.
  }, async baseUrl => {
    const run = new SmokeRun({ ...config, baseUrl, requestMs: 25 }, { log: () => {} });
    run.actors.candidate = { jwt: 'private-token' };
    await run.stage('Redirect', () => run.request('GET', '/redirect', { actor: 'candidate' }));
    await run.stage('Timeout', () => run.request('GET', '/hang'));
    assert.equal(forwarded, false);
    assert.ok(run.report.stages.every(stage => stage.status === 'FAIL'));
  });
});

test('cleanup CLI recovers real HTTP fixtures using fresh tokens and an incremental manifest', async () => {
  const output = await mkdtemp(join(tmpdir(), 'system-smoke-recovery-'));
  const fake = fixture({ failDeleteJob: true });
  try {
    await withServer(async (request, response) => {
      try {
        const chunks = [];
        for await (const chunk of request) chunks.push(chunk);
        const headers = Object.fromEntries(Object.entries(request.headers).map(([key, value]) => [key === 'authorization' ? 'Authorization' : key, value]));
        const result = await fake.fetchImpl(`${config.baseUrl}${request.url}`, {
          method: request.method, headers, body: chunks.length ? Buffer.concat(chunks).toString() : undefined });
        response.writeHead(result.status, { 'Content-Type': 'application/json' }); response.end(await result.text());
      } catch { response.writeHead(500); response.end('{"error":"fixture failure"}'); }
    }, async baseUrl => {
      // First produce a failed run journal using the contract fixture; target is the real local server for recovery.
      const initial = await runSuite({ ...config, baseUrl, asyncMs: 10, pollMs: 2 }, {
        ...fake, fetchImpl: (url, options) => fake.fetchImpl(url.replace(baseUrl, config.baseUrl), options),
        runId: 'smoke-recovery-fixture', output, log: () => {} });
      assert.equal(initial.exitCode, 1);
      fake.allowJobDeletion();
      const script = fileURLToPath(new URL('./cleanup.mjs', import.meta.url));
      const child = spawn(process.execPath, [script, join(output, 'manifest.json')], { env: {
        ...process.env, BASE_URL: baseUrl, MAILHOG_URL: config.mailhogUrl, ADMIN_EMAIL: config.adminEmail,
        ADMIN_PASSWORD: config.adminPassword, SMOKE_PASSWORD: config.smokePassword } });
      let stdout = '', stderr = '';
      child.stdout.on('data', data => { stdout += data; }); child.stderr.on('data', data => { stderr += data; });
      const code = await new Promise((resolve, reject) => { child.on('close', resolve); child.on('error', reject); });
      assert.equal(code, 0, stdout + stderr);
      assert.equal(fake.stores.jobs.size, 0);
      assert.equal(fake.stores.companies.size, 0);
      assert.ok([...fake.users.values()].filter(user => user.role !== 'ROLE_ADMIN').every(user => user.status === 'DELETED'));
    });
  } finally { await rm(output, { recursive: true, force: true }); }
});
