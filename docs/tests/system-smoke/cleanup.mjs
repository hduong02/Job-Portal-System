import { readFile } from 'node:fs/promises';
import { dirname, resolve, join } from 'node:path';
import { configFromEnv, SmokeRun } from './lib.mjs';

// Explicit recovery for a saved manifest. Never rerun the recruitment journey to clean up fixtures.
async function main() {
  const filename = process.argv[2];
  if (!filename || filename === '--help') {
    console.log('Usage: node docs/tests/system-smoke/cleanup.mjs <results/run-id/manifest.json>\nUse the same BASE_URL, ADMIN_EMAIL, ADMIN_PASSWORD, SMOKE_PASSWORD, and MAILHOG_URL as the original run.');
    return filename ? 0 : 2;
  }
  const config = configFromEnv();
  const manifest = JSON.parse(await readFile(resolve(filename), 'utf8'));
  if (manifest.version !== 1 || manifest.baseUrl !== config.baseUrl || !/^smoke-[\w-]+$/.test(manifest.runId)
    || !Array.isArray(manifest.resources) || !manifest.actors) throw new Error('Invalid manifest or BASE_URL mismatch');
  const allowed = {
    user: /^\/api\/users\/\d+$/, company: /^\/api\/companies\/\d+$/, job: /^\/api\/jobs\/\d+$/,
    resume: /^\/api\/resumes\/\d+$/, application: /^\/api\/applications\/\d+$/,
    savedJob: /^\/api\/preferences\/saved-jobs\/\d+$/, note: /^\/api\/applications\/\d+\/notes\/\d+$/,
    category: /^\/api\/job-categories\/\d+$/, skill: /^\/api\/job-skills\/\d+$/, tag: /^\/api\/job-tags\/\d+$/,
    education: /^\/api\/resumes\/\d+\/educations\/\d+$/, experience: /^\/api\/resumes\/\d+\/work-experiences\/\d+$/,
    resumeSkill: /^\/api\/resumes\/\d+\/skills\/\d+$/, project: /^\/api\/resumes\/\d+\/projects\/\d+$/,
    language: /^\/api\/resumes\/\d+\/languages\/\d+$/,
  };
  for (const resource of manifest.resources) {
    if (!allowed[resource.kind]?.test(resource.path) || Number(resource.path.split('/').at(-1)) !== resource.id
      || !['admin', 'candidate', 'employer', 'otherCandidate', 'otherEmployer'].includes(resource.actor))
      throw new Error('Manifest contains an invalid cleanup resource');
    if (resource.kind === 'user' && !Object.entries(manifest.actors).some(([name, actor]) => name !== 'admin'
      && actor.id === resource.id && actor.email?.startsWith(`${manifest.runId}.`))) throw new Error('Account is not a run-owned fixture');
  }
  const run = new SmokeRun(config, { runId: manifest.runId, output: join(dirname(resolve(filename)), `cleanup-${Date.now()}`) });
  run.report.resources = manifest.resources;
  run.report.retained = manifest.retained || [];
  run.report.uncertainMutations = manifest.uncertainMutations || [];
  try {
    const auth = await run.json('POST', '/auth/login', { body: { email: config.adminEmail, password: config.adminPassword } });
    run.check('Recovery uses an existing administrator', auth.user?.role === 'ROLE_ADMIN' && Boolean(auth.jwt));
    run.actors.admin = { ...auth.user, jwt: auth.jwt };
    for (const [name, actor] of Object.entries(manifest.actors)) {
      if (name === 'admin') continue;
      run.actors[name] = { id: actor.id, email: actor.email };
      if (!actor.email?.startsWith(`${manifest.runId}.`)) throw new Error('Fixture email is not run-owned');
      if (!manifest.resources.some(resource => resource.actor === name && !['DELETED', 'SOFT_DELETED'].includes(resource.cleanup))) continue;
      await run.stage(`Recovery login ${name}`, async () => {
        const login = await run.json('POST', '/auth/login', { body: { email: actor.email, password: config.smokePassword } });
        run.check('Recovery login matches saved identity', login.user?.id === actor.id && Boolean(login.jwt));
        run.actors[name].jwt = login.jwt;
      });
    }
    await run.cleanup();
  } catch (error) { await run.stage('Recovery failure', async () => { throw error; }); }
  const code = await run.finish();
  console.log(`${run.report.status}: ${run.output}`);
  return code;
}

main().then(code => { process.exitCode = code; }).catch(() => {
  console.error('Cleanup could not start. Check configuration, manifest, and target.'); process.exitCode = 2;
});
