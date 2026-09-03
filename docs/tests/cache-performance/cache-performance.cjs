const https = require('node:https');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawn } = require('node:child_process');
const { performance } = require('node:perf_hooks');

function percentile(values, p) {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.max(0, Math.ceil(p * sorted.length) - 1)];
}
function digest(value) {
  function canonical(v) {
    if (Array.isArray(v)) return v.map(canonical);
    if (v && typeof v === 'object') return Object.fromEntries(Object.keys(v).sort().map(k => [k, canonical(v[k])]));
    return v;
  }
  return crypto.createHash('sha256').update(JSON.stringify(canonical(value))).digest('hex');
}
async function discoverIds(required, route, limit) {
  const ids = new Set();
  let totalElements;
  for (let page = 0; ids.size < limit; page++) {
    // Bulk seed timestamps can tie. Sorting by the unique primary key keeps
    // pages stable and prevents duplicate IDs from reducing the sample.
    const data = await required(`${route}?page=${page}&size=100&sortBy=id&sortDirection=ASC`);
    if (!Array.isArray(data.content)) throw new Error(`${route}: invalid pagination`);
    totalElements = data.totalElements;
    for (const item of data.content) {
      const id = Number(item.id);
      if (Number.isSafeInteger(id) && id > 0) ids.add(id);
    }
    if (!data.content.length || page + 1 >= data.totalPages) break;
  }
  return { ids: [...ids].slice(0, limit), totalElements, sort: 'id ASC' };
}
function createClient(base, tokenProvider) {
  const url = new URL(base);
  const transport = url.protocol === 'https:' ? https : http;
  const agent = new transport.Agent({ keepAlive: true, maxSockets: 100, maxFreeSockets: 100 });
  async function request(route, method = 'GET', body) {
    const started = performance.now();
    return new Promise(resolve => {
      const data = body ? JSON.stringify(body) : undefined;
      const token = tokenProvider();
      const req = transport.request(new URL(route, url), {
        method, agent, headers: { Accept: 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(data ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } : {}) },
      }, res => {
        const chunks = [];
        res.on('data', chunk => chunks.push(chunk));
        res.on('error', e => resolve({ status: 0, durationMs: performance.now() - started, error: e.code || 'response error' }));
        res.on('end', () => {
          let json;
          try { json = JSON.parse(Buffer.concat(chunks).toString()); } catch {}
          resolve({ status: res.statusCode, durationMs: performance.now() - started, json });
        });
      });
      req.setTimeout(10000, () => req.destroy(new Error('request timeout')));
      req.on('error', e => resolve({ status: 0, durationMs: performance.now() - started, error: e.code || 'transport error' }));
      req.end(data);
    });
  }
  return { request, close: () => agent.destroy() };
}
async function phase(name, ids, count, rps, read, reference) {
  const samples = [], pending = new Set();
  const started = performance.now();
  const precise = process.env.PRECISE_SCHEDULING === 'yes';
  let lastLaunch = null;
  let dropped = 0, aborted = false, failures = 0;
  for (let i = 0; i < count; i++) {
    const due = Math.max(started + i * 1000 / rps,
      precise && lastLaunch !== null ? lastLaunch + 1000 / rps : started);
    const delay = due - performance.now();
    if (delay > 0) {
      if (precise) {
        // Windows timer granularity can exceed the 6.67 ms interval at 150 RPS.
        // Yield to I/O while checking the deadline instead of blocking the loop.
        if (delay > 20) await new Promise(resolve => setTimeout(resolve, delay - 16));
        while (performance.now() < due) await new Promise(resolve => setImmediate(resolve));
      } else {
        await new Promise(resolve => setTimeout(resolve, delay));
      }
    }
    if (samples.length >= 50 && failures / samples.length > 0.05) { aborted = true; break; }
    // Do not queue an unbounded backlog or issue a catch-up burst.
    if (pending.size >= 100 || (!precise && performance.now() - due > 1000 / rps)) { dropped++; continue; }
    const id = ids[i % ids.length];
    const launch = performance.now();
    lastLaunch = launch;
    const promise = read(id).then(res => {
      const valid = res.status === 200 && Number(res.json?.id) === id;
      const hash = valid ? digest(res.json) : null;
      const matches = !reference || reference.get(id) === hash;
      const passed = valid && matches;
      if (!passed) failures++;
      samples.push({ id, status: res.status, durationMs: res.durationMs, launchOffsetMs: launch - started,
        schedulingLagMs: launch - due, passed, matchesReference: matches, ...(res.error ? { error: res.error } : {}), ...(hash ? { sha256: hash } : {}) });
    }).finally(() => pending.delete(promise));
    pending.add(promise);
  }
  await Promise.all(pending);
  const elapsedSeconds = (performance.now() - started) / 1000;
  const values = samples.filter(x => x.passed).map(x => x.durationMs);
  const summary = { name, planned: count, requests: samples.length, distinctIds: new Set(samples.map(x => x.id)).size,
    errors: failures, dropped, aborted, targetRps: rps,
    offeredRps: samples.length / (precise && lastLaunch !== null
      ? Math.max(count / rps, (lastLaunch - started + 1000 / rps) / 1000) : count / rps),
    completedRps: samples.length / elapsedSeconds,
    elapsedSeconds, meanMs: values.length ? values.reduce((a, b) => a + b, 0) / values.length : null,
    minMs: values.length ? Math.min(...values) : null, p50Ms: percentile(values, .5), p95Ms: percentile(values, .95),
    p99Ms: percentile(values, .99), maxMs: values.length ? Math.max(...values) : null,
    p95SchedulingLagMs: percentile(samples.map(x => x.schedulingLagMs), .95) };
  console.log(JSON.stringify(summary));
  return { summary, samples };
}
async function redisControl(cache, ids, evict = false) {
  const host = process.env.SSH_TARGET;
  const container = process.env.REDIS_CONTAINER || 'job-portal-redis';
  if (!/^[a-zA-Z0-9_.@-]+$/.test(host || '') || !/^[a-zA-Z0-9_.-]+$/.test(container)) throw new Error('Invalid SSH target or Redis container');
  if (!['jobs', 'companies'].includes(cache) || !ids.every(x => Number.isSafeInteger(x) && x > 0)) throw new Error('Invalid benchmark keys');
  if (evict && process.env.EVICT_BENCHMARK_KEYS !== 'yes') throw new Error('Set EVICT_BENCHMARK_KEYS=yes only with authorization');
  const keys = ids.map(id => `${cache}::${id}`);
  // EXISTS/TTL increment keyspace counters. Report deltas subtract the next
  // snapshot's own lookups: before + 2*present hits, 2*N-before-present misses.
  const lua = `local before=0 local removed=0 local present=0 local minttl=2147483647
for _,k in ipairs(KEYS) do before=before+redis.call('EXISTS',k) end
${evict ? "for _,k in ipairs(KEYS) do removed=removed+redis.call('UNLINK',k) end" : ''}
for _,k in ipairs(KEYS) do local n=redis.call('EXISTS',k) present=present+n if n==1 then minttl=math.min(minttl,redis.call('TTL',k)) end end
local stats=redis.call('INFO','stats')
local hits=tonumber(string.match(stats,'keyspace_hits:(%d+)'))
local misses=tonumber(string.match(stats,'keyspace_misses:(%d+)'))
return {before,removed,present,minttl,hits,misses}`;
  return new Promise((resolve, reject) => {
    const args = ['-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10', '-o', 'StrictHostKeyChecking=yes'];
    if (process.env.SSH_PORT) args.push('-p', process.env.SSH_PORT);
    if (process.env.SSH_KEY_PATH) args.push('-i', process.env.SSH_KEY_PATH);
    args.push(host, `docker exec -i ${container} redis-cli --raw --eval /dev/stdin ${keys.join(' ')}`);
    const child = spawn('ssh', args, { stdio: ['pipe', 'pipe', 'pipe'] });
    let stdout = '', stderr = '';
    const timer = setTimeout(() => { child.kill(); reject(new Error('Redis SSH timeout')); }, 20000);
    child.stdout.on('data', x => { stdout += x; }); child.stderr.on('data', x => { stderr += x; });
    child.on('error', e => { clearTimeout(timer); reject(e); });
    child.on('close', code => {
      clearTimeout(timer);
      const values = stdout.trim().split(/\s+/).map(Number);
      if (code !== 0 || values.length !== 6 || values.some(x => !Number.isFinite(x))) return reject(new Error(`Redis inspection failed: ${stderr || stdout}`));
      const [before, removed, present, minimumTtlSeconds, hits, misses] = values;
      resolve({ timestamp: new Date().toISOString(), selectedKeys: keys.length, before, removed, present, minimumTtlSeconds, hits, misses });
    });
    child.stdin.end(lua);
  });
}
async function main() {
  const base = process.env.BASE_URL || 'https://api.vnairline.tech';
  const rps = Number(process.env.RPS || 50), limit = Number(process.env.ID_LIMIT || 1000);
  const seconds = Number(process.env.WARM_SECONDS || 20);
  if (!(rps > 0 && rps <= 150 && Number.isInteger(limit) && limit > 0 && limit <= 1000 && seconds > 0 && seconds <= 60)) throw new Error('Use RPS 1..150, ID_LIMIT 1..1000, WARM_SECONDS >0..60.');
  const dir = path.join(__dirname, 'results', new Date().toISOString().replace(/[:.]/g, '-'));
  fs.mkdirSync(dir, { recursive: true });
  let token = process.env.API_TOKEN;
  const client = createClient(base, () => token);
  const report = { baseUrl: base, startedAt: new Date().toISOString(), timezone: 'Asia/Bangkok', rps, phases: [], cacheState: 'Unverified: no Redis key inspection, eviction, or cache metrics access', setup: {} };
  report.preciseScheduling = process.env.PRECISE_SCHEDULING === 'yes';
  const controlled = !!process.env.SSH_TARGET && process.env.EVICT_BENCHMARK_KEYS === 'yes';
  report.cacheEvidence = [];
  if (controlled) report.cacheState = 'Selected keys absent after targeted UNLINK; keys resident after cold reads and through warm phases. Redis counters recorded at boundaries.';
  async function required(route, method, body) {
    const res = await client.request(route, method, body);
    if (![200, 201].includes(res.status) || !res.json) throw new Error(`${method || 'GET'} ${route}: HTTP ${res.status}; expected successful JSON`);
    return res.json;
  }
  try {
    if (!token) {
      if (process.env.TEST_EMAIL && process.env.TEST_PASSWORD) {
        token = (await required('/auth/login', 'POST', { email: process.env.TEST_EMAIL, password: process.env.TEST_PASSWORD })).jwt;
      } else {
        const runId = `${Date.now()}-${crypto.randomBytes(4).toString('hex')}`;
        const auth = await required('/auth/signup', 'POST', { fullName: 'Cache Performance Test Candidate',
          email: `cache.perf.${runId}@example.com`, password: crypto.randomBytes(24).toString('base64url'), role: 'ROLE_JOB_SEEKER' });
        token = auth.jwt;
        report.setup.createdCandidateId = auth.user.id;
        report.setup.createdEmail = auth.user.email;
      }
    }
    if (!token) throw new Error('Authentication returned no JWT');
    // The list routes are not @Cacheable. Job list enrichment can warm company
    // keys, so benchmark companies before discovering jobs.
    for (const endpoint of ['/api/companies', '/api/jobs']) {
      const discovery = await discoverIds(required, endpoint, limit);
      const ids = discovery.ids;
      report.setup.datasets ||= {};
      report.setup.datasets[endpoint] = { totalElements: discovery.totalElements, distinctSampleIds: ids.length, sort: discovery.sort };
      if (!ids.length) throw new Error(`No IDs available for ${endpoint}`);
      console.log(`${endpoint}: discovered ${ids.length} IDs`);
      const read = id => client.request(`${endpoint}/${id}`);
      // Warm the network connection using a noncached route, not detail IDs.
      await required('/api/users/profile');
      let evidence;
      if (controlled) {
        evidence = { endpoint, cleared: await redisControl(endpoint.split('/').pop(), ids, true) };
        report.cacheEvidence.push(evidence);
        if (evidence.cleared.present !== 0) throw new Error('Selected cache keys remain after removal');
      }
      const before = await phase(`${endpoint}/{id}: ${controlled ? 'cold after key eviction' : 'first read'}`, ids, ids.length, rps, read);
      report.phases.push(before);
      const hashes = new Map(before.samples.filter(x => x.passed).map(x => [x.id, x.sha256]));
      if (before.summary.errors || before.summary.dropped || before.summary.aborted) throw new Error('First-read phase incomplete; stopping before warming');
      if (controlled) {
        evidence.afterCold = await redisControl(endpoint.split('/').pop(), ids);
        if (evidence.afterCold.present !== ids.length) throw new Error('Cold responses did not populate all selected cache keys');
      }
      const warm = await phase(`${endpoint}/{id}: warmed repeated read`, ids, Math.ceil(rps * seconds), rps, read, hashes);
      report.phases.push(warm);
      if (controlled) {
        evidence.afterWarm = await redisControl(endpoint.split('/').pop(), ids);
        if (evidence.afterWarm.present !== ids.length) throw new Error('Selected keys missing after warm phase');
      }
      if (warm.summary.aborted) throw new Error('Error rate exceeded 5%; load stopped');
    }
  } catch (e) { report.failure = e.message; console.error(e.message); }
  finally { client.close(); }
  report.finishedAt = new Date().toISOString();
  report.warmSeconds = seconds;
  fs.writeFileSync(path.join(dir, 'measurements.json'), JSON.stringify(report, null, 2));
  writeReport(report, dir);
  process.exitCode = report.failure || report.phases.some(x => x.summary.errors || x.summary.dropped || Math.abs(x.summary.offeredRps - rps) > rps * .05) ? 1 : 0;
}
function redisDeltas(start, end) {
  if (!start || !end) return { hits: 'N/A', misses: 'N/A' };
  return { hits: end.hits - start.hits - end.before - 2 * end.present,
    misses: end.misses - start.misses - (2 * end.selectedKeys - end.before - end.present) };
}
function writeReport(report, dir) {
  const { rps, baseUrl: base } = report;
  const seconds = report.warmSeconds || 20;
  const controlled = report.cacheEvidence?.length > 0;
  const f = v => v === null || v === undefined ? 'N/A' : v.toFixed(2);
  const rows = report.phases.map(({ summary: s }) => `| ${s.name} | ${s.requests} | ${s.distinctIds} | ${f(s.offeredRps)} | ${f(s.completedRps)} | ${f(s.meanMs)} | ${f(s.minMs)} | ${f(s.p50Ms)} | ${f(s.p95Ms)} | ${f(s.p99Ms)} | ${f(s.maxMs)} | ${s.errors} | ${s.dropped} |`);
  const comparisons = [];
  for (let i = 0; i + 1 < report.phases.length; i += 2) {
    const a = report.phases[i].summary, b = report.phases[i + 1].summary;
    comparisons.push(`| ${a.name.split(':')[0]} | ${f((a.p50Ms - b.p50Ms) / a.p50Ms * 100)}% | ${f((a.p95Ms - b.p95Ms) / a.p95Ms * 100)}% | ${f((a.p99Ms - b.p99Ms) / a.p99Ms * 100)}% |`);
  }
  const localTime = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Bangkok', dateStyle: 'full', timeStyle: 'long' }).format(new Date(report.startedAt));
  const text = `# Cached endpoint performance report\n\nTarget: ${base}\n\nStarted: ${localTime} (Asia/Bangkok). Raw timestamps use UTC.\n\n## Results\n\nAll latency values are milliseconds, measured at the client from request dispatch through full response body receipt. Percentiles use nearest rank and include successful responses passing ID and payload checks. Error counts include HTTP, transport, ID, and payload failures.\n\n| Endpoint / phase | Requests | Unique IDs | Offered RPS | Completed RPS | Mean | Min | p50 | p95 | p99 | Max | Errors | Dropped arrivals |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n${rows.join('\n')}\n\n## Observed latency change\n\nPositive percentages indicate lower latency after warming; negative percentages indicate higher latency. These are observed phase differences, not isolated Redis speedups.\n\n| Endpoint | p50 reduction | p95 reduction | p99 reduction |\n|---|---:|---:|---:|\n${comparisons.join('\n')}\n\n## Cache interpretation\n\nSource has @Cacheable jobs keyed by ID on GET /api/jobs/{id}, and companies keyed by ID on GET /api/companies/{id}. Both TTLs are 24 hours. List/search endpoints are not cached.\n\n**Cache misses and hits are unverified.** First-read IDs may already be cached by production users or prior tests. Repeated reads follow successful reads of the same IDs and are expected to hit if the deployed code matches this workspace and Redis caching is active. The API exposes no cache-hit indicator. No Redis access was provided; no caches were flushed and no jobs/companies were edited. A query parameter cannot bypass caches keyed only by ID.\n\nThe first phase reads each ID once. The warmed phase cycles through the same IDs for ${seconds} seconds. Companies run first because job-list enrichment can populate company caches. Different phase lengths when fewer IDs are available and concurrent production traffic limit comparison. Small first-read samples make p99 weak. Redis cache statistics or key inspection plus controlled per-key eviction in an isolated environment are needed for a confirmed miss-versus-hit experiment.\n\n## Load method and correctness\n\nFixed arrival scheduling targets ${rps} RPS per phase; phases run sequentially, so total target load is ${rps} RPS. HTTPS certificate validation is enabled, keep-alive sockets are reused, and at most 100 requests are in flight. Requests time out after 10 seconds. Scheduling delays beyond one interval or concurrency saturation drop arrivals instead of issuing catch-up bursts. Load stops if more than 5% of at least 50 completed requests fail. Completed RPS includes the final drain; offered RPS uses the planned arrival window.\n\nEvery response must be HTTP 200 with the requested ID. Warmed payloads must match first-read canonical SHA-256 hashes. Raw samples include dispatch offsets and scheduling lag for auditing achieved load. Passwords, JWTs, and response payloads are not saved. Setup/discovery requests are excluded from measurements.\n\nTest account created: ${report.setup.createdCandidateId || 'none (existing authentication used)'}${report.setup.createdEmail ? `, ${report.setup.createdEmail}` : ''}. The test account persists. No resumes, applications, jobs, or companies are created by this test.\n\nRun failure: ${report.failure || 'none'}.\n\nSee README.md for reproduction. Raw measurements are in measurements.json beside this report.\n`;
  const evidenceRows = report.cacheEvidence.map(e => {
    const cold = redisDeltas(e.cleared, e.afterCold), warm = redisDeltas(e.afterCold, e.afterWarm);
    return `| ${e.endpoint}/{id} | ${e.cleared.selectedKeys} | ${e.cleared.removed} | ${e.cleared.present} | ${e.afterCold?.present ?? 'N/A'} | ${e.afterWarm?.present ?? 'N/A'} | ${cold.misses} | ${cold.hits} | ${warm.misses} | ${warm.hits} | ${e.afterWarm?.minimumTtlSeconds ?? 'N/A'} |`;
  });
  const finalText = controlled ? text.replace(/\*\*Cache misses and hits are unverified\.\*\*[\s\S]*?## Load method and correctness/, `**Redis cache state verified at phase boundaries via SSH.** Only the selected companies::ID and jobs::ID keys were removed using UNLINK, with user authorization. All selected keys were absent before cold reads and present after cold and warm reads. No databases were flushed; no jobs/companies were edited. Redis keyspace counters corroborate activity but are global: concurrent users and job-to-company enrichment can add hits/misses. Individual HTTP responses have no hit/miss indicator, so boundary evidence cannot exclude a concurrent request warming a key first.\n\n| Endpoint | Selected keys | Removed | Present before cold | Present after cold | Present after warm | Cold misses delta | Cold hits delta | Warm misses delta | Warm hits delta | Minimum warm TTL s |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n${evidenceRows.join('\n')}\n\nCold phases request each selected ID once. Warm phases reuse the same IDs for ${seconds} seconds. Cold sample counts depend on the available distinct IDs; p99 is effectively the maximum for samples smaller than 100. Refer to the request counts above when assessing tail percentiles. Short cold phases versus longer warm phases, network/connection effects, and concurrent traffic limit causal comparisons. Job reads also call company-service, whose cache remains warm. These are end-to-end deployment timings, not isolated Redis timings.\n\n## Load method and correctness`) : text;
  const datasetText = report.setup.datasets ? `\n## Dataset\n\nDiscovery uses stable id ASC pagination. Job discovery uses the public list defaults (active OPEN jobs), so its API total can differ from the full jobs table.\n\n${Object.entries(report.setup.datasets).map(([endpoint, data]) => `- ${endpoint}: ${data.totalElements} API-visible records; ${data.distinctSampleIds} distinct IDs sampled.`).join('\n')}\n` : '';
  const populationText = report.population ? `\n## Population context\n\n| Table | User-reported count | Verified database count |\n|---|---:|---:|\n${Object.entries(report.population.userReported).map(([name, count]) => `| ${name} | ${count} | ${report.population.verified[name] ?? 'Not checked'} |`).join('\n')}\n` : '';
  const totals = report.phases.reduce((sum, { summary: s }) => ({ requests: sum.requests + s.requests, errors: sum.errors + s.errors, dropped: sum.dropped + s.dropped }), { requests: 0, errors: 0, dropped: 0 });
  const validationText = `\n## Validation outcome\n\n${totals.requests} measured requests, ${totals.errors} response/correctness errors, ${totals.dropped} dropped scheduled arrivals. ${totals.dropped ? 'The runner exits nonzero under its strict zero-dropped-arrivals rule; latency measurements remain usable and achieved RPS is reported above.' : 'No scheduled arrivals were dropped.'}\n`;
  const pacingText = report.preciseScheduling ? '\n## Precise pacing\n\nPRECISE_SCHEDULING=yes yields to I/O while checking deadlines. Late launches shift subsequent arrivals to retain minimum request spacing without catch-up bursts. Offered RPS uses the actual arrival window, including these shifts; warm phases may extend slightly beyond the configured duration.\n' : '';
  const annotatedText = finalText + (controlled ? `\nCounter deltas above subtract each ending snapshot's own EXISTS/TTL lookups. Raw JSON counters are unchanged. ${report.counterNotes || 'Remaining deltas may include unrelated production traffic.'}\n` : '') + datasetText + populationText + validationText + pacingText;
  fs.writeFileSync(path.join(dir, 'report.md'), annotatedText);
  fs.writeFileSync(path.join(__dirname, 'cache-performance-report.md'), annotatedText.replace('Raw measurements are in measurements.json beside this report.', `Raw measurements: results/${path.basename(dir)}/measurements.json.`));
  console.log(`Report: ${path.join(__dirname, 'cache-performance-report.md')}`);
}
module.exports = { percentile, digest, phase, createClient, redisControl, redisDeltas, writeReport, discoverIds };
if (require.main === module) main().catch(e => { console.error(e.message); process.exitCode = 1; });
