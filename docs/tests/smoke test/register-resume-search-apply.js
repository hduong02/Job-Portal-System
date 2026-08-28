import { check, fail, group } from 'k6';
import http from 'k6/http';
import { Counter } from 'k6/metrics';

const completedFlows = new Counter('completed_flows');

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    completed_flows: ['count>=1'],
  },
};

const baseUrl = (__ENV.BASE_URL || 'http://localhost:5000').replace(/\/+$/, '');

function assertChecks(value, assertions) {
  if (!check(value, assertions)) fail('Smoke-test assertions failed.');
}

function request(method, path, headers = {}, body) {
  const response = http.request(method, `${baseUrl}${path}`, body === undefined ? null : JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json', ...headers },
    timeout: '30s',
    tags: { name: `${method} ${path.split('?')[0]}` },
  });
  if (!check(response, { 'API returns HTTP 200': (r) => r.status === 200 })) {
    fail(`${method} ${path.split('?')[0]} returned HTTP ${response.status}. Check the gateway and services.`);
  }
  try {
    return response.json();
  } catch (_) {
    fail(`${method} ${path.split('?')[0]} returned invalid JSON.`);
  }
}

export default function () {
  completedFlows.add(0);
  const keyword = __ENV.KEYWORD || 'Java';
  const runId = `${Date.now()}-${__VU}-${__ITER}-${Math.random().toString(36).slice(2)}`;
  const email = `smoke.${runId}@example.com`;
  const coverLetter = `Register/resume/search/apply k6 smoke test ${runId}`;
  let candidate;
  let headers;
  let resume;
  let job;
  let application;

  console.log(`Live smoke test against ${baseUrl}`);

  group('register', () => {
    const auth = request('POST', '/auth/signup', {}, {
      fullName: 'Smoke Test Candidate', email, password: `Smoke!${runId}`, role: 'ROLE_JOB_SEEKER',
    });
    assertChecks(auth, {
      'registration returns a JWT': (r) => typeof r.jwt === 'string' && r.jwt.length > 0,
      'registration returns the expected candidate': (r) => r.user.id > 0 && r.user.email === email && r.user.role === 'ROLE_JOB_SEEKER',
    });
    candidate = auth.user;
    headers = { Authorization: `Bearer ${auth.jwt}` };
  });

  group('create resume', () => {
    resume = request('POST', '/api/resumes', headers, {
      title: `Smoke Resume ${runId}`, template: 'CLASSIC', visibility: 'PRIVATE', isDefault: true,
    });
    assertChecks(resume, {
      'resume belongs to the new candidate': (r) => r.id > 0 && r.candidateId === candidate.id,
      'resume is private, classic, and default': (r) => r.visibility === 'PRIVATE' && r.template === 'CLASSIC' && r.isDefault === true,
    });
  });

  group('search', () => {
    let page = 0;
    let results;
    const today = new Date().toISOString().slice(0, 10);
    do {
      results = request('GET', `/api/jobs?keyword=${encodeURIComponent(keyword)}&status=OPEN&page=${page}&size=20`, headers);
      assertChecks(results, { 'search returns paginated content': (r) => Array.isArray(r.content) });
      job = results.content.find((item) => item.id > 0 && item.status === 'OPEN' && item.active
        && (!item.applicationDeadline || item.applicationDeadline >= today)
        && (!item.expiresAt || item.expiresAt >= today));
      page++;
    } while (!job && page < results.totalPages);
    assertChecks(job, { 'search finds a job accepting applications': (r) => Boolean(r) });
  });

  const applicationChecks = {
    'application has an ID': (r) => r.id > 0,
    'application belongs to the candidate': (r) => r.candidate.id === candidate.id,
    'application references the searched job': (r) => r.job.id === job.id,
    'application references the new resume': (r) => r.resumeId === resume.id,
    'application is pending': (r) => r.status === 'PENDING',
    'application preserves the cover letter': (r) => r.coverLetter === coverLetter,
  };
  group('apply', () => {
    application = request('POST', '/api/applications', headers, { jobId: job.id, resumeId: resume.id, coverLetter });
    assertChecks(application, applicationChecks);
  });

  group('verify stored application', () => {
    const results = request('GET', '/api/applications/my?page=0&size=20', headers);
    const saved = results.content.filter((item) => item.id === application.id);
    assertChecks(saved, { 'submitted application appears exactly once': (r) => r.length === 1 });
    assertChecks(saved[0], applicationChecks);
  });
  completedFlows.add(1);
  console.log(`PASS: register -> create resume -> search -> apply (candidate=${candidate.id}, resume=${resume.id}, job=${job.id}, application=${application.id})`);
}
