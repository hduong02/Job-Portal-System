import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join, resolve } from 'node:path';
import { Blocked, configFromEnv, SmokeRun } from './lib.mjs';
import { executeWorkflow } from './workflow.mjs';

export async function runSuite(config, options = {}) {
  const run = new SmokeRun(config, options);
  if (!options.output && options.writeReports !== false)
    run.output = join(dirname(fileURLToPath(import.meta.url)), 'results', run.report.runId);
  try { await executeWorkflow(run); }
  catch (error) { await run.stage('Runner failure', async () => { throw error; }); }
  const exitCode = await run.finish();
  return { exitCode, report: run.report, output: run.output };
}

export async function main() {
  if (process.argv.includes('--help')) {
    console.log('System API smoke suite (Node.js 22+).\nRequired environment: BASE_URL, MAILHOG_URL, ADMIN_EMAIL, ADMIN_PASSWORD, SMOKE_PASSWORD.\nOptional: SMOKE_EMAIL_DOMAIN.\nRun: node docs/tests/system-smoke/run.mjs\nExit: 0 pass, 1 failure, 2 blocked. See README.md for VPS setup and cleanup recovery.');
    return 0;
  }
  let config;
  try { config = configFromEnv(); }
  catch (error) {
    const run = new SmokeRun({ baseUrl: '[unconfigured]', adminPassword: process.env.ADMIN_PASSWORD,
      smokePassword: process.env.SMOKE_PASSWORD });
    run.output = join(dirname(fileURLToPath(import.meta.url)), 'results', run.report.runId);
    await run.stage('Configuration', async () => { throw error instanceof Blocked ? error : new Blocked('Invalid configuration'); });
    const code = await run.finish();
    console.log(`Report: ${run.output}`);
    return code;
  }
  const result = await runSuite(config);
  console.log(`${result.report.status}: ${result.output}`);
  return result.exitCode;
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  main().then(code => { process.exitCode = code; }).catch(() => {
    console.error('Unable to complete the runner or write reports. Inspect the latest manifest before retrying.');
    process.exitCode = 1;
  });
}
