import { mkdir, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { performance } from 'node:perf_hooks';

export class Blocked extends Error {}

export function configFromEnv(env = process.env) {
  const config = {
    baseUrl: env.BASE_URL, mailhogUrl: env.MAILHOG_URL,
    adminEmail: env.ADMIN_EMAIL, adminPassword: env.ADMIN_PASSWORD,
    smokePassword: env.SMOKE_PASSWORD, emailDomain: env.SMOKE_EMAIL_DOMAIN || 'example.com',
    requestMs: 30000, aiMs: 120000, pollMs: 2000, asyncMs: 180000, readinessMs: 60000,
  };
  const missing = ['BASE_URL', 'MAILHOG_URL', 'ADMIN_EMAIL', 'ADMIN_PASSWORD', 'SMOKE_PASSWORD']
    .filter(key => !env[key]);
  if (missing.length) throw new Blocked(`Missing configuration: ${missing.join(', ')}`);
  for (const key of ['baseUrl', 'mailhogUrl']) {
    let url;
    try { url = new URL(config[key]); } catch { throw new Blocked(`${key} must be an absolute HTTP(S) URL`); }
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash)
      throw new Blocked(`${key} must use HTTP(S), without embedded credentials, query, or fragment`);
    config[key] = url.href.replace(/\/+$/, '');
  }
  if (config.smokePassword.length < 8) throw new Blocked('SMOKE_PASSWORD must contain at least eight characters');
  if (!/^[a-z0-9.-]+\.[a-z]{2,}$/i.test(config.emailDomain)) throw new Blocked('Invalid SMOKE_EMAIL_DOMAIN');
  return config;
}

export function shortlist(score) {
  return score >= 90 ? 'AUTO_SHORTLISTED' : score >= 75 ? 'REVIEW_RECOMMENDED'
    : score >= 50 ? 'PENDING_REVIEW' : 'LOW_MATCH';
}

export function exposesTrace(text, json) {
  const walk = value => value && typeof value === 'object' && Object.entries(value).some(([key, child]) =>
    (/^(trace|stack|stacktrace)$/i.test(key) && Boolean(child) && JSON.stringify(child) !== '[]') || walk(child));
  return Boolean(walk(json) || /\bat\s+(?:[\w$]+\.)+[\w$]+\([^\n]*\.java:\d+\)/.test(text));
}

// Only inspect messages for this run, and decode MIME transfer encodings before matching HTML.
export function mailMatches(message, { email, title, company }) {
  const recipients = message.To || [];
  const recipient = recipients.some(to => `${to.Mailbox}@${to.Domain}`.toLowerCase() === email.toLowerCase());
  const decode = part => {
    const headers = part?.Headers || {};
    let body = part?.Body || '';
    const encoding = String(headers['Content-Transfer-Encoding']?.[0] || '').toLowerCase();
    if (encoding === 'base64') body = Buffer.from(body, 'base64').toString('utf8');
    if (encoding === 'quoted-printable') {
      body = body.replace(/=\r?\n/g, '').replace(/=([0-9a-f]{2})/gi, (_, hex) => String.fromCharCode(parseInt(hex, 16)));
    }
    return body;
  };
  const parts = part => part ? [decode(part), ...(part.Parts || []).flatMap(parts), ...(part.MIME?.Parts || []).flatMap(parts)] : [];
  const html = [...parts(message.Content), ...parts(message.MIME)].join('\n')
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&#39;|&#x27;/g, "'").replace(/&quot;/g, '"');
  return recipient && html.includes(title) && html.includes(company) && /\bShortlisted\b/i.test(html);
}

export class SmokeRun {
  constructor(config, { fetchImpl = fetch, sleep = ms => new Promise(resolve => setTimeout(resolve, ms)),
    now = Date.now, output, runId, log = console.log } = {}) {
    this.config = config;
    this.fetchImpl = fetchImpl; this.sleep = sleep; this.now = now; this.output = output; this.log = log;
    this.actors = {}; this.secrets = new Set([config.adminPassword, config.smokePassword].filter(Boolean));
    this.report = { runId: runId || `smoke-${new Date().toISOString().replace(/[:.]/g, '-')}-${randomUUID().slice(0, 8)}`,
      baseUrl: config.baseUrl, startedAt: new Date().toISOString(), status: 'RUNNING',
      stages: [], checks: [], requests: [], resources: [], retained: [], uncertainMutations: [] };
    this.currentStage = 'setup';
  }

  redact(value) {
    let text = String(value);
    for (const secret of this.secrets) if (secret) {
      if (text === secret || secret.length >= 8) text = text.split(secret).join('[REDACTED]');
      else {
        const escaped = secret.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        text = text.replace(new RegExp(`(\\b(?:password|token|secret|authorization)\\b\\s*(?:[=:]\\s*|\\s+))${escaped}\\b`, 'gi'), '$1[REDACTED]');
      }
    }
    return text.replace(/Bearer\s+\S+/gi, 'Bearer [REDACTED]')
      .replace(/\beyJ[\w-]+\.[\w-]+\.[\w-]+\b/g, '[REDACTED JWT]');
  }

  check(name, passed, details = '', fatal = true) {
    this.report.checks.push({ stage: this.currentStage, name, passed: Boolean(passed), details: this.redact(details) });
    if (!passed && fatal) throw new Error(name);
    return passed;
  }

  async stage(name, action, ready = true) {
    this.currentStage = name;
    const row = { name, status: ready ? 'RUNNING' : 'SKIPPED', startedAt: new Date().toISOString() };
    this.report.stages.push(row);
    if (!ready) { row.reason = 'Required earlier fixture or configuration unavailable'; await this.persist(); return; }
    const start = performance.now(), checkStart = this.report.checks.length;
    this.log(`RUN ${name}`);
    try {
      await action();
      row.status = this.report.checks.slice(checkStart).some(check => !check.passed) ? 'FAIL' : 'PASS';
    } catch (error) {
      row.status = error instanceof Blocked ? 'BLOCKED' : 'FAIL';
      row.error = this.redact(error.message);
    }
    row.durationMs = Math.round(performance.now() - start);
    this.log(`${row.status} ${name}`);
    await this.persist();
  }

  async request(method, path, { actor, body, expected = [200], headers = {}, mailbox = false,
    timeoutMs = this.config.requestMs, probe = false } = {}) {
    const base = mailbox ? this.config.mailhogUrl : this.config.baseUrl;
    const started = performance.now();
    const row = { stage: this.currentStage, method, path: path.split('?')[0], actor,
      expectedStatuses: expected, target: mailbox ? 'mailhog' : 'gateway' };
    this.report.requests.push(row);
    // Never forward gateway tokens to the mailbox or a redirect target.
    const requestHeaders = { 'Content-Type': 'application/json', ...headers };
    if (actor) {
      if (!this.actors[actor]?.jwt || mailbox) throw new Error('Authenticated actor unavailable for request');
      requestHeaders.Authorization = `Bearer ${this.actors[actor].jwt}`;
    }
    let response, text, json;
    try {
      response = await this.fetchImpl(`${base}${path}`, {
        method, headers: requestHeaders, body: body === undefined ? undefined : JSON.stringify(body),
        signal: AbortSignal.timeout(Math.max(1, timeoutMs)), redirect: 'error',
      });
      row.status = response.status;
      text = await response.text();
      try { json = JSON.parse(text); } catch {}
      if (json?.jwt) this.secrets.add(json.jwt);
      if (exposesTrace(text, json)) this.check('Response does not expose stack traces', false,
        `${method} ${row.path} exposes a stack trace`, false);
    } catch {
      row.error = 'Transport failure, redirect, or request timeout';
      if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) this.report.uncertainMutations.push({
        method, path: row.path, actor, reason: 'Response unavailable; mutation may have persisted. Do not replay blindly.' });
      throw new Error(`${method} ${row.path}: ${row.error}`);
    } finally { row.durationMs = Math.round(performance.now() - started); }
    if (!probe) {
      this.check(`${method} ${row.path}: expected HTTP ${expected.join('/')}`, expected.includes(response.status),
        `actual HTTP ${response.status}`);
      if (response.status !== 204) this.check(`${method} ${row.path}: valid JSON`, json !== undefined);
    }
    return { status: response.status, json };
  }

  async json(method, path, options) { return (await this.request(method, path, options)).json; }

  async poll(name, action, predicate, timeoutMs = this.config.asyncMs) {
    const deadline = this.now() + timeoutMs;
    while (this.now() < deadline) {
      const value = await action(Math.max(1, deadline - this.now()));
      if (predicate(value)) { this.check(name, true); return value; }
      await this.sleep(Math.min(this.config.pollMs, Math.max(0, deadline - this.now())));
    }
    throw new Error(`${name}: timed out after ${timeoutMs} ms`);
  }

  async remember(kind, id, path, actor, extra = {}) {
    this.check(`${kind} returns a positive ID`, Number.isSafeInteger(id) && id > 0);
    this.report.resources.push({ kind, id, path, actor, cleanup: 'PENDING', ...extra });
    await this.persist();
  }

  async create(kind, path, actor, body, expected = [200]) {
    // Capture the ID before semantic assertions, including a creation with an unexpected success code.
    const response = await this.request('POST', path, { actor, body, expected, probe: true });
    if (response.json?.id > 0) await this.remember(kind, response.json.id, `${path}/${response.json.id}`, actor);
    else if (response.status >= 200 && response.status < 300) this.report.uncertainMutations.push({
      method: 'POST', path, actor, reason: 'Successful creation without a usable ID; reconcile using the run marker.' });
    this.check(`Create ${kind}: expected HTTP ${expected.join('/')}`, expected.includes(response.status), `actual HTTP ${response.status}`);
    this.check(`Create ${kind}: returns ID`, response.json?.id > 0);
    return response.json;
  }

  async cleanup() {
    // Reverse registration order: note/application and resume children precede parents; users are last.
    for (const resource of [...this.report.resources].reverse()) {
      if (['DELETED', 'SOFT_DELETED'].includes(resource.cleanup)) continue;
      const pending = this.report.resources.filter(item => item !== resource && !['DELETED', 'SOFT_DELETED'].includes(item.cleanup));
      const uncertainCreation = this.report.uncertainMutations.find(item => item.method === 'POST'
        && /^(?:\/auth\/signup|\/api\/(?:companies|jobs|resumes|applications|preferences\/saved-jobs|job-categories|job-skills|job-tags)|\/api\/resumes\/\d+\/(?:educations|work-experiences|skills|projects|languages)|\/api\/applications\/\d+\/notes)$/.test(item.path));
      if (uncertainCreation) {
        resource.cleanup = 'BLOCKED';
        await this.stage(`Cleanup ${resource.kind} ${resource.id}`, async () => {
          throw new Blocked(`Uncertain creation at ${uncertainCreation.path}; reconcile before removing possible dependencies`);
        });
        continue;
      }
      const userActor = resource.kind === 'user'
        ? Object.entries(this.actors).find(([name, actor]) => name !== 'admin' && actor.id === resource.id)?.[0] : undefined;
      const dependent = pending.find(item =>
        (userActor && item.actor === userActor) ||
        (['resume', 'application'].includes(resource.kind) && item.path.startsWith(`${resource.path}/`)) ||
        (resource.kind === 'resume' && item.kind === 'application' && item.resumeId === resource.id) ||
        (resource.kind === 'job' && item.kind === 'application' && item.jobId === resource.id) ||
        (resource.kind === 'company' && item.kind === 'job' && item.companyId === resource.id) ||
        (['category', 'skill', 'tag'].includes(resource.kind) && item.kind === 'job'));
      if (dependent) {
        resource.cleanup = 'BLOCKED';
        await this.stage(`Cleanup ${resource.kind} ${resource.id}`, async () => {
          throw new Blocked(`Dependent ${dependent.kind} ${dependent.id} remains; preserve parent and recovery access`);
        });
        continue;
      }
      await this.stage(`Cleanup ${resource.kind} ${resource.id}`, async () => {
        try {
          const response = await this.request('DELETE', resource.path, { actor: resource.actor, expected: [200, 204] });
          if (resource.kind === 'user') {
            this.check('Account deletion returns DELETED status', response.json?.status === 'DELETED');
            const stored = await this.json('GET', resource.path, { actor: 'admin' });
            this.check('Account soft deletion persisted', stored.status === 'DELETED');
            resource.cleanup = 'SOFT_DELETED';
          } else if (resource.kind === 'company') {
            // /companies/my throws after deletion and the global list is not owner-filtered.
            this.check('Company deletion acknowledged', response.json?.status === true);
            resource.cleanup = 'DELETED';
          } else {
            // Missing-item detail endpoints currently sometimes emit 500. Verify via bounded owner lists instead.
            const lists = { application: '/api/applications/my?page=0&size=100',
              resume: '/api/resumes/my?page=0&size=100', job: `/api/jobs/company/${resource.companyId}?page=0&size=100`,
              company: '/api/companies/my', savedJob: '/api/preferences/saved-jobs?page=0&size=100',
              category: '/api/job-categories', skill: '/api/job-skills', tag: '/api/job-tags' };
            const listPath = lists[resource.kind] || resource.path.slice(0, resource.path.lastIndexOf('/'));
            const value = await this.json('GET', listPath, { actor: resource.actor });
            const items = Array.isArray(value) ? value : Array.isArray(value?.content) ? value.content : value ? [value] : [];
            this.check('Deleted record absent from owner list', !items.some(item => item.id === resource.id));
            resource.cleanup = resource.kind === 'resume' ? 'SOFT_DELETED' : 'DELETED';
          }
        } catch (error) { resource.cleanup = 'FAILED'; throw error; }
      });
    }
  }

  async finish() {
    this.report.finishedAt = new Date().toISOString();
    this.report.leftovers = this.report.resources.filter(resource => !['DELETED', 'SOFT_DELETED'].includes(resource.cleanup));
    const failed = this.report.stages.some(stage => stage.status === 'FAIL') || this.report.checks.some(check => !check.passed)
      || this.report.leftovers.length || this.report.uncertainMutations.length;
    this.report.status = failed ? 'FAIL' : this.report.stages.some(stage => ['BLOCKED', 'SKIPPED'].includes(stage.status)) ? 'BLOCKED' : 'PASS';
    await this.persist();
    return this.report.status === 'PASS' ? 0 : this.report.status === 'BLOCKED' ? 2 : 1;
  }

  async persist() {
    if (!this.output) return;
    await mkdir(this.output, { recursive: true });
    // Explicit actor allowlist: the journal can be used for recovery, but contains no passwords or tokens.
    const journal = { version: 1, runId: this.report.runId, baseUrl: this.config.baseUrl,
      actors: Object.fromEntries(Object.entries(this.actors).map(([name, actor]) => [name, { id: actor.id, email: actor.email }])),
      resources: this.report.resources, retained: this.report.retained, uncertainMutations: this.report.uncertainMutations };
    // Preserve structural keys and actor/email identifiers, even when a password is a common word.
    const serialize = value => JSON.stringify(value, (key, child) =>
      /^(password|jwt|token|authorization)$/i.test(key) ? '[REDACTED]'
        : typeof child === 'string' && ['error', 'details', 'reason'].includes(key) ? this.redact(child) : child, 2);
    await writeFile(`${this.output}/manifest.json`, serialize(journal) + '\n');
    await writeFile(`${this.output}/report.json`, serialize(this.report) + '\n');
    const escape = value => String(value || '').replace(/\|/g, '\\|').replace(/[\r\n]+/g, ' ');
    const rows = this.report.stages.map(stage => `| ${escape(stage.name)} | ${stage.status} | ${stage.durationMs ?? ''} | ${escape(stage.error || stage.reason)} |`);
    const failures = this.report.checks.filter(check => !check.passed).map(check => `- ${check.stage}: ${check.name} (${check.details})`);
    const retained = this.report.retained.map(item => `- ${JSON.stringify(item)}`);
    const pending = this.report.resources.filter(item => !['DELETED', 'SOFT_DELETED'].includes(item.cleanup));
    await writeFile(`${this.output}/report.md`, [
      `# System smoke: ${this.report.status}`, '', `Run: ${this.report.runId}`, `Target: ${this.config.baseUrl}`, '',
      '| Stage | Result | Duration ms | Details |', '|---|---|---:|---|', ...rows, '',
      '## Failed assertions', '', ...(failures.length ? failures : ['None.']), '',
      '## Cleanup leftovers', '', '```json', JSON.stringify(pending, null, 2), '```', '',
      '## Uncertain mutations', '', '```json', JSON.stringify(this.report.uncertainMutations, null, 2), '```', '',
      '## Expected retained artifacts', '', ...retained,
      '- User accounts and resumes are soft deleted; captured emails and Kafka events remain. No global mailbox purge is performed.', '',
    ].join('\n'));
  }
}
