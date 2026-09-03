# Ramping VUs with MailHog

**Highest measured throughput meeting the existing latency and success thresholds: 356.23 successful RPS at 80 VUs** during a 30-second hold. Peak observed successful throughput: **356.23 RPS at 80 VUs**, p95 **342.64 ms**, p99 **423.99 ms**. These short holds measure capacity for this shortlist workload; they do not establish long-duration system-wide capacity.

Start: 22:23:30 Asia/Bangkok. Full 280-second profile completed: **True**. k6 exit: **99** (99 indicates threshold failures). Resource stop during load: **False**.

The user requested SSH access to root@201.18.216.184, MailHog cleanup, and a rerun with ramping VUs and recorded RPS. Uses [Grafana k6's ramping-vus executor](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/ramping-vus/), with active VUs increasing through **5, 10, 20, 40, 80, 160, 320**. Each level has a 10-second linear ramp and 30-second hold. No think time; no retries; five-second HTTP timeout and graceful stop; connection reuse enabled.

Windows k6 -> four pinned SSH connections -> VPS loopback gateway :5000. Each iteration PATCHes a distinct existing PENDING test-company application to SHORTLISTED. No application status resets. Current deployment is tested: gateway local/job-portal-gateway:pool10-total50-20261009 (10 backend connections per route / 50 total), 512 MiB / 0.5 CPU; application 1 GiB / 1 CPU. Exact images, lifecycle baseline, and resource settings are retained in JSON. Gateway and application configuration were not changed.

| Active VUs | Successful RPS | Total RPS | Success | p95 ms | p99 ms | Thresholds pass |
| ---: | ---: | ---: | ---: | ---: | ---: | :---: |
| 5 | 40.13 | 40.13 | 100.000% | 155.25 | 193.94 | Yes |
| 10 | 106.93 | 106.93 | 100.000% | 120.80 | 159.61 | Yes |
| 20 | 250.70 | 250.70 | 100.000% | 122.60 | 163.08 | Yes |
| 40 | 335.27 | 335.27 | 100.000% | 204.12 | 257.61 | Yes |
| 80 | 356.23 | 356.23 | 100.000% | 342.64 | 423.99 | Yes |
| 160 | 332.97 | 332.97 | 100.000% | 702.14 | 775.52 | No |
| 320 | 320.37 | 320.37 | 100.000% | 1391.70 | 1675.26 | No |

Passing means p95 <500 ms, p99 <1000 ms, and success >99.9%. Each row counts completed requests whose iterations started in that 30-second hold, including their completion tail, divided by 30 seconds. Ramps are excluded. Raw timestamped metrics are retained. This closed workload naturally reduces RPS when response latency rises; no offered RPS target or scheduled-arrival drop metric applies. SSH/network overhead is included.

Across ramps and holds: **68,826 requests**, **68,826 HTTP 200**, **0 failures**, **0 timeouts**, **0 server 5xx**. Whole-run average **245.32 RPS**, including ramps and completion tail; this average is not the capacity estimate.

After drain: **68,826 database updates**, **68,826 Kafka events**, **68,826 captured messages**, final Kafka lag **0**. Aggregate count reconciliation passed: **True**. This does not verify per-identity delivery or external inbox delivery. New captured messages are retained.

Authenticated health: **passed**. Gateway recovery restart: **False**. Minimum host available memory **1425.9 MiB**. Guard during notification drain: **MailHog RSS above 768 MiB**. Guards: host available memory >=1536 MiB (preflight >=1920), MailHog RSS <=768 MiB, bounded service memory <97% of limit, unchanged monitored service lifecycle. No VPS k6; owned PC test/transport/monitor processes stopped; private JWT files removed from both hosts.

The full load ran within its resource guards. During notification drain, MailHog RSS peaked at **915.0 MiB**, crossing 768 MiB, and host available memory fell below its 1536 MiB guard to **1425.9 MiB**. Minimum host available memory during load was **2056.0 MiB**. No further requests were generated after load ended. Captured test emails remain; another run with these guards requires mailbox memory to be addressed.

## Scripts used for this test

- [runner.py](scripts/runner.py): Windows orchestration, SSH checks, load generation, artifact collection, and cleanup.
- [remoteops.py](scripts/remoteops.py): VPS preflight, MailHog cleanup, fixture selection, notification reconciliation, and health checks.
- [guard.py](scripts/guard.py): Continuous VPS resource and lifecycle monitoring.
- [transport.cjs](scripts/transport.cjs): Pinned SSH forwarding and transport metrics.
- [stress-vus.js](scripts/stress-vus.js): k6 ramping-VU shortlist workload and per-hold measurements.
- [report.py](scripts/report.py): Report generation and raw/summary measurement reconciliation.
- [transport-config.json](scripts/transport-config.json): Non-secret SSH forwarding port and connection-count configuration.

These are the exact scripts/configuration used for the recorded run. Execute local helpers from the repository root; they use the original .loadtest/ramping-vus/ artifact directory, local k6 binary, and ssh2 installation. VPS helpers use the recorded remote directory and existing private test credentials. The runner prevents overwriting an existing run; another run requires separate artifact directories and sufficient pending fixtures. No credentials or application IDs were copied into this scripts folder.
