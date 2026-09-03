# Shortlist load-test dataset

Created on `201.18.216.184` using `seed-vps-shortlist.py`.

| Resource | Result |
| --- | --- |
| Company | Shortlist Load Test, ID `100001` |
| Dedicated employer | ID `6` |
| Jobs | 30 open jobs, IDs `2500001 + 50*n` for `n=0..29` |
| Candidates | All 7,999 existing job seekers, including inactive synthetic accounts |
| Applications | 239,970 new rows, all `PENDING`, IDs `50001..289970` |
| Distribution | Every candidate applied once to each job; 7,999 applications per job |
| Resumes | Existing resumes reused; one missing resume supplied as a private test fixture |

The seed inserts applications directly, bypassing application creation and AI screening. It does not publish Kafka events, send emails, or run the stress test. Existing user roles and statuses remain unchanged. The employer is a new active account registered through the user service.

Backups of all five affected databases, a resumable manifest, private employer credentials/authentication, SQL scripts, and `applications.csv` are stored under `/root/job-portal-shortlist-loadtest-20261007` on the VPS. Credentials are not copied to this repository.

Verification checked candidate/resume ownership, job/company/employer relationships, duplicate candidate/job pairs, counts, and sampled company/job/application API reads. See `vps-shortlist-verification.json` for results. Database totals afterward: 10,001 users, 2,001 companies, 50,030 jobs, 8,001 resumes, and 289,970 applications.

For recovery, run `python3 /root/seed-vps-shortlist.py` on the VPS. Keep its manifest and backups; reruns verify/recover existing phases. They do not reset statuses after a shortlist test.

