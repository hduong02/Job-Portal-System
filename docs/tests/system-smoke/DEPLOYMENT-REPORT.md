# Backend fixes, deployment, and live verification

**Result: PASS.** The backend fixes are deployed on `201.18.216.184`. Two unmodified whole-system smoke runs passed **464/464** and **462/462** assertions, including real AI screening, Kafka notification delivery, authorization, ownership, and fixture cleanup.

## Fixes and resulting behavior

The deployed backend enforces administrator permissions on protected user and job-metadata operations, checks ownership of private resume sections and application operations, and returns sanitized error responses with appropriate client rejection statuses. The live suite verifies these behaviors through the gateway.

Application responses now keep top-level `aiScore` and `aiShortListStatus` consistent with detailed screening. Response assembly loads the application, calls the job/company/user services, then reads its screening record. If the asynchronous worker commits during those calls, the loaded application can contain older AI fields while the screening query returns the completed result. The mapper uses the included screening record for both top-level AI fields whenever screening exists, preventing a response that combines null or outdated application fields with completed screening data.

When screening is absent, application AI fields retain their existing behavior. Response assembly does not mutate or save the loaded entity. Screening persistence and score thresholds remain unchanged.

## Automated verification

| Verification | Result |
|---|---|
| Full backend Maven reactor | 256 tests passed; zero failures, errors, or skips |
| Application-service suite covering the screening fix | 75 tests passed; zero failures, errors, or skips |
| Database-backed screening read/commit regression | Passed |
| Detail, candidate-list, job-list, and company-list AI consistency | Passed |

`ScreeningReadPersistenceTest` uses real repositories, an isolated H2 database, and the transactional screening worker. It forces the worker to commit between the application read and the screening read, verifies the application fields were persisted, and checks that the assembled response remains consistent. A synchronous test executor pins this interleaving without sleeps or scheduling luck.

`ApplicationAiResponseTest` covers null and outdated application fields alongside completed screening across all four read paths, plus pending-screening behavior and preservation of the loaded entity.

Run the application-service checks with:

```powershell
mvn -o -f job-portal-system/pom.xml -pl services/job-portal-application-service test
```

## Deployment

All eleven backend images were rebuilt from the workspace and published to Docker Hub under `andyy02`. The deployed Compose configuration selects these versions:

| Services | Image tag |
|---|---|
| Application service | `screening-fix-20261012-1535` |
| Discovery, Config Server, gateway, user, company, job, resume, preference, AI, and notification | `rebuild-20261012-0200` |

Both published image sets also updated their corresponding `latest` tags. The [combined deployment manifest](deployment-20261012-0200.json) records the final image and image ID for all eleven services.

Existing environment variables, memory reservations and limits, CPU limits, ports, mounts, restart policies, logging settings, and networks were retained. Database containers were not replaced. Discovery and Config Server became healthy; the business services and gateway passed readiness checks. Notification readiness was verified through its stable Kafka consumer group with active members.

Previous image IDs, private container snapshots, and Compose backups remain under `/root/job-portal-rebuild-20261012-0200` and `/root/job-portal-screening-fix-20261012-1535` for rollback. Runtime snapshots contain secrets and remain on the VPS.

## Live smoke verification

Infrastructure preflight passed for the databases, Redis, Config Server, all eight business-service registrations, Kafka, the notification consumer group, and MailHog. The public HTTPS gateway health endpoint returned `UP`.

| Run | Time on Asia/Bangkok | Assertions | Requests | Cleanup leftovers | Uncertain mutations |
|---|---|---|---|---|---|
| Validation run | 15:35:22–15:35:37 | 464/464 passed | 170 | 0 | 0 |
| [Repeat validation](results/smoke-2026-10-12T08-35-44-881Z-6b69e18e/report.md) | 15:35:44–15:35:53 | 462/462 passed | 169 | 0 | 0 |

Check and request counts vary with bounded readiness and screening polling. Both runs exercised the recruitment workflow, real AI summary generation and asynchronous screening, consistent AI response fields, matching Kafka-to-MailHog shortlist emails, authorization and ownership rejection checks, sanitized errors, and supported cleanup. Temporary diagnostic instrumentation was removed before verification; the smoke assertions were unchanged.

Disposable test passwords were removed after cleanup. User and resume records remain soft deleted; screening rows, captured emails, and Kafka events remain as documented by the suite. No global mailbox purge, cache reset, or unrelated data cleanup occurred.
