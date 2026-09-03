# Whole-system API smoke suite

Run one sequential recruitment journey against the deployed VPS. All business requests go through the gateway. The full suite requires real Gemini generation and asynchronous screening, plus delivery of a matching status email to the VPS MailHog mailbox. Browser behavior and load/performance testing are outside this suite.

This folder is self-contained and uses Node.js built-ins. It does not change the existing smoke, cache, or stress tests.

## Run directly on your VPS

With this folder available on the VPS and either Node.js 22+ or Docker installed, run:

```bash
bash docs/tests/system-smoke/run-vps.sh
```

The launcher uses the VPS gateway at `http://127.0.0.1:5000` and MailHog at `http://127.0.0.1:8025`. It prompts for an existing admin email/password and a password for disposable test accounts; password input is hidden. Keep the disposable password for cleanup recovery. If host Node.js is unavailable, it runs an isolated `node:22-bookworm-slim` container with reports mounted back to this folder. This command makes real API calls and requires live Gemini and email delivery. It does not run `runner.test.mjs` or simulated responses.

To copy the self-contained folder from a local checkout if your VPS has no checkout:

```bash
scp -r docs/tests/system-smoke <ssh-user>@<vps-host>:~/
ssh <ssh-user>@<vps-host>
bash ~/system-smoke/run-vps.sh
```

Override the gateway if you want to test the public HTTPS entrypoint instead:

```bash
BASE_URL=https://<deployed-api-host> bash ~/system-smoke/run-vps.sh
```

Use `--preflight` to check Docker infrastructure before running the same live suite. Reports and recovery manifests appear in `results/<run-id>/` beside the launcher. Exit `0` means a full pass; `1` means a failure; `2` means blocked. Missing AI results or matching emails prevent a pass. A pass cannot be established by the offline runner tests below.

```bash
bash ~/system-smoke/run-vps.sh --preflight
bash ~/system-smoke/run-vps.sh --cleanup ~/system-smoke/results/<run-id>/manifest.json
```

For noninteractive runs, set `ADMIN_EMAIL`, `ADMIN_PASSWORD`, and `SMOKE_PASSWORD` in the environment first. `BASE_URL` and `MAILHOG_URL` retain their VPS defaults. The Docker fallback may pull its runtime image; it does not install software into the host or change deployment configuration. Docker cleanup requires the manifest to be inside this suite's folder.

## Files

| File | Purpose |
|---|---|
| `run-vps.sh` | VPS launcher with local defaults, hidden credential prompts, and optional preflight/recovery |
| `run.mjs` | Main CLI; configuration, orchestration, and reports |
| `workflow.mjs` | Recruitment journey and isolated negative/security checks |
| `lib.mjs` | HTTP, bounded polling, redaction, resource journal, cleanup, and reporting |
| `cleanup.mjs` | Retry cleanup from a trusted run manifest without repeating the journey |
| `preflight.sh` | Read-only infrastructure checks executed on the VPS |
| `runner.test.mjs` | Offline runner verification using a stateful HTTP contract fixture |

## Prerequisites

- Node.js 22 or newer for the Node CLI, or Docker for the VPS launcher. No `npm install`, k6, or third-party Node package is required.
- A reachable deployed gateway and an existing active `ROLE_ADMIN` account. The suite never self-registers an administrator.
- A working Gemini key on the deployed AI service; the runner does not need the key.
- Kafka and the notification service running, with SMTP directed to the VPS MailHog instance. Use synthetic email addresses whose messages are captured there.
- Permission to create four disposable users, one company, one job, three metadata records, two populated private resumes, and an application. It adds a note and saved job briefly. Defects may create additional applications, which are journaled when an ID is returned.

The suite is deliberately strict. Some current backend errors and authorization gaps will fail it. It never treats `500` as an acceptable duplicate/ownership rejection and never silently skips live AI or notifications.

## Connect to the VPS mailbox

MailHog in Compose binds to loopback on the VPS. `localhost:8025` on your laptop is a different server unless you establish a tunnel. In a separate terminal, keep this running:

```powershell
ssh -N -L 18025:127.0.0.1:8025 <ssh-user>@<vps-host>
```

The SSH host and login must be your deployment's actual values. The suite reads the mailbox API only; it never clears the mailbox or changes Kafka offsets.

## Configure and run

From the repository root in PowerShell:

```powershell
$env:BASE_URL = 'https://<deployed-api-host>'
$env:MAILHOG_URL = 'http://127.0.0.1:18025'
$env:ADMIN_EMAIL = '<existing-admin-email>'
# Enter secrets without placing their literal values in shell history.
$env:ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Admin password' -AsSecureString)).Password
$env:SMOKE_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Disposable-account password (8+ characters)' -AsSecureString)).Password

node docs/tests/system-smoke/run.mjs
$LASTEXITCODE
```

Keep `SMOKE_PASSWORD` available if cleanup recovery is needed. The same password is used only for this run's four disposable accounts. Optional `SMOKE_EMAIL_DOMAIN` defaults to `example.com`. Synthetic addresses include the unique run ID. There is no default gateway hostname, so configuration cannot accidentally target an unrelated deployment.

On the VPS itself, use shell environment variables with the same names and `MAILHOG_URL=http://127.0.0.1:8025`. Enter secrets using `read -r -s`, then export them. Never commit secrets or results. This folder ignores `.env` and `results/`; the runner does not load `.env` automatically.

For usage without network requests:

```powershell
node docs/tests/system-smoke/run.mjs --help
```

## Coverage and pass criteria

| Stage | Required evidence |
|---|---|
| Gateway/mailbox/admin prerequisites | Gateway `UP`, valid MailHog JSON API, active existing admin login |
| User service | Candidate/employer signup and login, correct JWT identity, persisted profile edits |
| Company service | Employer-owned company, verification persisted as `ACTIVE`, social links submitted, owner lookup, moderation persisted as `SUSPENDED` |
| Job service | Create/list category, skill, and tag; create/edit/publish a job with future deadlines and correct associations |
| Search/cache behavior | Exact run-owned job found with keyword/company/category/skill/tag/status/work-mode filters; repeated detail reads; edits visible after warm reads |
| Resume service | Private owner-linked resumes; personal information, summary, education, experience, skills, project, language, and default selection persisted |
| Preference service | Save, list, check, remove, and verify unsaved job |
| Application service | Correct candidate/job/resume/content and initial status; candidate and employer views; star/filter; add/list/delete note; compact status PATCH and persisted shortlist |
| AI service | Nonempty real summary; completed screening, integer component scores in 0–100, consistent score-derived shortlist and string arrays |
| Notification service/Kafka | MailHog email to the unique candidate address containing this job, this company, and `Shortlisted` |
| Closure/admin | Withdrawal persisted, closed job rejects a new applicant, suspend/activate persisted, suspended login rejected and reactivated login succeeds |
| Security | Missing/malformed JWT, bad login, invalid body, duplicate submission, cross-owner reads/writes, non-admin moderation/user listing/metadata editing, JWT identity overriding forged headers |

Security checks are separate stages. One failure does not hide later diagnostics. Failed fixture setup skips only dependent stages; a skipped required stage can never produce a full pass. No note-edit endpoint exists, so the supported add/list/delete operations are tested.

Cache checks establish correct observable reads and invalidation. They do not prove Redis hits or measure cache performance. Email delivery demonstrates the asynchronous business path but does not prove durable/exactly-once Kafka delivery. Do not interpret a smoke pass as a security audit or sustained-capacity result.

Ordinary requests time out after 30 seconds. AI generation allows 120 seconds. Screening/email poll every two seconds for up to 180 seconds each. Gateway readiness retries reads for up to 60 seconds. Mutations are never retried automatically. Redirects are rejected, TLS validation stays enabled, and gateway credentials are never forwarded to MailHog.

## Reports and exit codes

Each run creates `results/<unique-run-id>/` with UTC timestamps:

- `report.md`: stage results, failed assertions, cleanup leftovers, uncertain mutations, and retained artifacts.
- `report.json`: checks, expected/actual HTTP statuses, request timings, and resource outcomes.
- `manifest.json`: incremental recovery journal with synthetic emails, IDs, resource paths, and cleanup states. It contains no passwords or JWTs.

The journal is updated after each captured creation and each stage. Full response bodies and request headers are not stored. Known credentials and returned JWTs are redacted; server error bodies are inspected for stack traces without being copied into reports. Transport errors are reported generically. Reports still include fixture IDs, synthetic addresses, and deployment URLs; keep them private when appropriate.

| Exit | Meaning |
|---|---|
| `0` | Every required stage passed, and every supported cleanup operation completed |
| `1` | Assertion, request, runner, or cleanup failure; or uncertain mutation requiring reconciliation |
| `2` | Required configuration/access unavailable, or a required stage skipped without a separate failure |

A business defect takes precedence over a blocked/skipped result. Readiness failures, external Gemini errors, screening timeouts, missing emails, exposed traces, and unexpected access all prevent a pass. There are no latency thresholds beyond operational timeouts.

## Cleanup and recovery

The runner attempts cleanup in `finally`, including after failed stages. It removes notes/applications, resume children, saved jobs, jobs, metadata, and the company using their actual APIs, and soft deletes users/resumes. Most deletions are verified using owner/child lists; company deletion is verified by its acknowledgment because `/companies/my` currently throws when the company no longer exists and the global list is not owner-filtered.

If a dependent record remains, cleanup preserves its parent and the owning account's login access for recovery. An uncertain creation blocks deletion until it has been reconciled, since it may reference otherwise removable fixtures. Failures and blocked parent deletions appear as leftovers. Only IDs registered by this run are targeted; existing admin accounts and unrelated records are never cleaned up.

Expected retained data is explicit:

- User and resume deletion APIs are soft deletes. Physical rows remain.
- Screening has no deletion/cancellation API; application deletion does not cascade to it. A screening row may remain or arrive later, especially after a timeout. Completed screening IDs are recorded when visible.
- Captured mail and Kafka events remain. No mailbox purge, database-wide deletion, cache flush, or offset reset occurs.

Retry supported cleanup using the saved manifest and the original configuration/password:

```powershell
node docs/tests/system-smoke/cleanup.mjs 'docs/tests/system-smoke/results/<run-id>/manifest.json'
```

Recovery validates the target URL, paths, fixture account identities, and run marker, then obtains fresh login tokens. It does not repeat registration or application submission. It writes a new `cleanup-<timestamp>/` subfolder, preserving the original report. For another retry use the **latest recovery manifest**, which contains updated cleanup states. Already completed deletions are skipped. Suspended/deleted fixture accounts or inaccessible APIs may require admin intervention before recovery can obtain an owner's token.

Treat manifests as trusted local operator artifacts; do not use one from an untrusted source. If a mutation times out or creates a record without an ID, its outcome is uncertain and the suite fails. Reconcile that specific operation by run marker, unique fixture email, and owner lists before retrying; never replay the entire workflow blindly. Hard termination or power loss can interrupt automatic cleanup, so use the incremental manifest to recover. A creation interrupted before its ID was captured also requires reconciliation.

## Optional infrastructure preflight

Execute `preflight.sh` on the VPS using its existing Docker access. It reads container states, PostgreSQL readiness, Redis PING, Config Server property sources, all eight Eureka registrations, Kafka broker availability, a stable notification consumer group with active members, and the mailbox API. It does not restart services or expose container environment variables.

From PowerShell, without uploading the script:

```powershell
Get-Content -Raw docs/tests/system-smoke/preflight.sh | ssh <ssh-user>@<vps-host> 'bash -s'
```

From a VPS checkout:

```bash
bash docs/tests/system-smoke/preflight.sh
```

Defaults match this repository's Compose container names, including `job-portal-kafka-dev`. Set `KAFKA_CONTAINER` or `NOTIFICATION_GROUP` when deployment names differ; the default group is `notification-service`. The script requires Bash, Docker, curl, and Python 3; other container name changes require adjusting the container list. Infrastructure checks run separately and must be considered alongside the API report; the API report does not claim to have performed them.

## Verify the runner offline

```powershell
node --test docs/tests/system-smoke/runner.test.mjs
```

The stateful contract fixture exercises the full journey and cleanup; server/stack-trace defects; missing email/screening; blocked mailbox access before fixture creation; failed cleanup preserving parents and recovery access; identity spoofing; unexpected successful duplicate creation; report redaction; uncertain mutation handling; configuration rejection; and MIME matching. Local HTTP tests verify redirect refusal, timeouts, and the cleanup CLI using fresh tokens. Tests use no real deployment, email delivery, or Gemini quota. They validate the runner, not deployed backend behavior.

The current source contains likely smoke failures: several user/metadata administration endpoints lack role checks, private resume ownership errors can become `500`, private resume education listing lacks an owner check, and duplicate application rejection throws a generic exception. This suite reports those defects; it does not modify the application to make them pass.
