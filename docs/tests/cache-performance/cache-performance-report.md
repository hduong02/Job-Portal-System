# Cached endpoint performance report

Target: https://api.jobportal.tech

## Results

All latency values are milliseconds, measured at the client from request dispatch through full response body receipt. Percentiles use nearest rank and include successful responses passing ID and payload checks. Error counts include HTTP, transport, ID, and payload failures.

| Endpoint / phase | Requests | Unique IDs | Offered RPS | Completed RPS | Mean | Min | p50 | p95 | p99 | Max | Errors | Dropped arrivals |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| /api/companies/{id}: cold after key eviction | 1000 | 1000 | 149.59 | 148.67 | 55.99 | 44.96 | 51.00 | 93.66 | 152.96 | 174.19 | 0 | 0 |
| /api/companies/{id}: warmed repeated read | 3000 | 1000 | 148.49 | 148.19 | 51.22 | 44.37 | 50.36 | 59.33 | 67.66 | 107.90 | 0 | 0 |
| /api/jobs/{id}: cold after key eviction | 1000 | 1000 | 149.37 | 148.41 | 69.49 | 46.35 | 59.48 | 114.91 | 126.58 | 153.27 | 0 | 0 |
| /api/jobs/{id}: warmed repeated read | 3000 | 1000 | 149.36 | 149.05 | 50.15 | 44.63 | 48.90 | 56.80 | 63.40 | 111.21 | 0 | 0 |

## Observed latency change

Positive percentages indicate lower latency after warming; negative percentages indicate higher latency. These are observed phase differences, not isolated Redis speedups.

| Endpoint | p50 reduction | p95 reduction | p99 reduction |
|---|---:|---:|---:|
| /api/companies/{id} | 1.26% | 36.65% | 55.76% |
| /api/jobs/{id} | 17.79% | 50.57% | 49.92% |

## Cache interpretation

Source has @Cacheable jobs keyed by ID on GET /api/jobs/{id}, and companies keyed by ID on GET /api/companies/{id}. Both TTLs are 24 hours. List/search endpoints are not cached.

**Redis cache state verified at phase boundaries via SSH.** Only the selected companies::ID and jobs::ID keys were removed using UNLINK, with user authorization. All selected keys were absent before cold reads and present after cold and warm reads. No databases were flushed; no jobs/companies were edited. Redis keyspace counters corroborate activity but are global: concurrent users and job-to-company enrichment can add hits/misses. Individual HTTP responses have no hit/miss indicator, so boundary evidence cannot exclude a concurrent request warming a key first.

| Endpoint | Selected keys | Removed | Present before cold | Present after cold | Present after warm | Cold misses delta | Cold hits delta | Warm misses delta | Warm hits delta | Minimum warm TTL s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| /api/companies/{id} | 1000 | 1000 | 0 | 1000 | 1000 | 1000 | 0 | 0 | 3000 | 86371 |
| /api/jobs/{id} | 1000 | 991 | 0 | 1000 | 1000 | 1000 | 1000 | 0 | 3000 | 86371 |

Cold phases request each selected ID once. Warm phases reuse the same IDs for 20 seconds. Cold sample counts depend on the available distinct IDs; p99 is effectively the maximum for samples smaller than 100. Refer to the request counts above when assessing tail percentiles. Short cold phases versus longer warm phases, network/connection effects, and concurrent traffic limit causal comparisons. Job reads also call company-service, whose cache remains warm. These are end-to-end deployment timings, not isolated Redis timings.

## Load method and correctness

Fixed arrival scheduling targets 150 RPS per phase; phases run sequentially, so total target load is 150 RPS. HTTPS certificate validation is enabled, keep-alive sockets are reused, and at most 100 requests are in flight. Requests time out after 10 seconds. Scheduling delays beyond one interval or concurrency saturation drop arrivals instead of issuing catch-up bursts. Load stops if more than 5% of at least 50 completed requests fail. Completed RPS includes the final drain; offered RPS uses the planned arrival window.

Every response must be HTTP 200 with the requested ID. Warmed payloads must match first-read canonical SHA-256 hashes. Raw samples include dispatch offsets and scheduling lag for auditing achieved load. Passwords, JWTs, and response payloads are not saved. Setup/discovery requests are excluded from measurements.

Test account created: 10, cache.perf.1791570316104-14fb1fe7@example.com. The test account persists. No resumes, applications, jobs, or companies are created by this test.

Run failure: none.

See README.md for reproduction. Raw measurements: results/2026-10-09T18-25-16-103Z/measurements.json.

Counter deltas above subtract each ending snapshot's own EXISTS/TTL lookups. Raw JSON counters are unchanged. Remaining deltas may include unrelated production traffic.

## Dataset

Discovery uses stable id ASC pagination. Job discovery uses the public list defaults (active OPEN jobs), so its API total can differ from the full jobs table.

- /api/companies: 2001 API-visible records; 1000 distinct IDs sampled.
- /api/jobs: 50020 API-visible records; 1000 distinct IDs sampled.

## Validation outcome

8000 measured requests, 0 response/correctness errors, 0 dropped scheduled arrivals. No scheduled arrivals were dropped.


