# VPS sample data

Seeded `root@201.18.216.184`. Existing records were preserved.

| Data | Before | Added | Final |
| --- | ---: | ---: | ---: |
| Users | 75 | 9,925 | 10,000 |
| Companies | 21 | 1,979 | 2,000 |
| Jobs | 101 | 49,899 | 50,000 |
| Applications | 152 | 49,848 | 50,000 |
| Resumes | 54 | 7,946 | 8,000 |

New users comprise 1,979 employers and 7,946 candidates. Synthetic users have
`INACTIVE` status and an unusable password; emails and websites use
`example.invalid`. Companies are active and jobs are open, with varied titles,
locations, salaries, categories, skills, tags and employment types. Applications
have varied statuses and consistent candidate, resume, job, company and employer
references. Resumes include personal information and a summary.

## Scripts and results

- `seed-vps-scale.py`: one-time top-up with backups, live sequence allocation,
  separate service transactions, recovery manifest and relationship verification.
- `verify-vps-scale.py`: repeated sampled service API reads, pagination checks,
  and expected rejection of unauthenticated gateway requests.
- `vps-scale-verification.json`: successful verification results.

The deployed scripts and generated SQL are in `/root/job-portal-scale/docs/`.
Private database backups are in its timestamped `backups-*` directory.
`scale-manifest.json` records the reserved IDs and committed phases. Backups and
the manifest remain on the VPS and were not copied into the repository.

Run on the VPS:

```bash
python3 /root/job-portal-scale/docs/seed-vps-scale.py --verify-only
python3 /root/job-portal-scale/docs/verify-vps-scale.py
```

The initial run stopped after the resume phase because the tag table has no
`active` column. The query was corrected, and `--resume` completed jobs and
applications using the original reserved IDs. Resume mode checks every reserved
ID against the manifest before skipping committed phases. An ambiguous commit
requires manual inspection. Do not delete the manifest to retry.

The script inserts directly into PostgreSQL without emitting notification or AI
events. No services were restarted. Table statistics were refreshed with
`ANALYZE`. All new relationships and totals were checked. Company and job detail
reads passed twice for records near the beginning, middle and end of the dataset.
Company pagination reports 2,000 records; job pagination reports 49,990 because
the endpoint filters out existing non-open jobs. Gateway requests require a JWT
and correctly returned HTTP 401 without one; authenticated gateway reads were
not tested in this run. No load test was performed.

For selective recovery, consult the manifest IDs and remove dependent seed rows
before their parents. Full dumps must not overwrite subsequent real activity.
