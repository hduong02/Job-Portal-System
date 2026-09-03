# Cached endpoint benchmark

Run the deployed cached detail endpoints at a fixed arrival rate of approximately 50 requests per second. Uses Node's built-in HTTP/HTTPS client; no k6 or npm packages are needed.

For a 150 RPS run, set `RPS=150` and `PRECISE_SCHEDULING=yes`. Precise pacing avoids Windows timer granularity limits, yields to I/O, and shifts late arrivals without catch-up bursts. Reported offered RPS uses the actual arrival window; warm phases can extend slightly beyond 20 seconds. The default remains 50 RPS.

```powershell
node --test 'docs/tests/cache-performance/cache-performance.test.cjs'
$env:BASE_URL = 'https://api.jobportal.tech'
$env:RPS = '50'
$env:ID_LIMIT = '1000'
$env:WARM_SECONDS = '20'
node 'docs/tests/cache-performance/cache-performance.cjs'
```

Use `API_TOKEN` or `TEST_EMAIL` plus `TEST_PASSWORD` environment variables for existing authentication. Without them, the benchmark creates one labelled candidate account. Credentials are not logged or saved. The account remains; no job, company, resume, or application is created.

The source-cached endpoints are `GET /api/companies/{id}` (`companies::{id}`) and `GET /api/jobs/{id}` (`jobs::{id}`), with 24-hour TTLs. First reads use up to 1000 distinct existing IDs. Repeated reads cycle through the same IDs for 20 seconds. Each phase targets 50 RPS independently; they do not run simultaneously. Company phases precede job discovery because job-list enrichment may populate company caches.

The API-only test does **not** establish confirmed Redis misses or hits. Existing keys can be warm before the first read. HTTP query parameters do not change cache keys. See the report's limitations. SSH access to the server can support a stronger experiment by inspecting exact Redis keys and gathering statistics, with authorization for targeted benchmark-key removal if cold reads are required. Do not use `FLUSHALL` or `FLUSHDB` on the deployment.

