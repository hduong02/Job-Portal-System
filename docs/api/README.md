# API Reference

Common request and response examples for the Job Portal gateway. Send `Authorization: Bearer <token>` for protected endpoints.

## Auth — `POST /auth/signup`

```json
{
  "fullName": "John Smith",
  "email": "john@example.com",
  "password": "secret123",
  "phone": "+84901234567",
  "role": "ROLE_JOB_SEEKER"
}
```

**Returns:** `AuthResponse` containing `title`, `message`, `jwt`, and `user`. Log in with `POST /auth/login` and `{ "email": "john@example.com", "password": "secret123" }`.

## Job Search — `GET /api/jobs`

```text
?keyword=Java
&status=OPEN
&location=Bangkok          (optional)
&categoryId=1              (optional)
&skillIds=1,2              (optional)
&tagIds=1,2                (optional)
&companyId=1               (optional)
&minSalary=1000            (optional)
&maxSalary=5000            (optional)
&jobType=FULL_TIME         (optional)
&workMode=REMOTE           (optional)
&experienceLevel=MID_LEVEL       (optional)
&minOpenings=1             (optional)
&sortBy=createdAt
&sortDirection=DESC
&page=0&size=20
```

Send a bearer token for this and all protected endpoints.

## Create Resume — `POST /api/resumes`

```json
{
  "title": "Java Developer Resume",
  "template": "CLASSIC",
  "visibility": "PRIVATE",
  "isDefault": true
}
```

**Returns:** `ResumeResponse`; use its `id` when applying.

## Create Application — `POST /api/applications`

```json
{
  "jobId": 101,
  "resumeId": 5,
  "coverLetter": "I am interested in this Java developer position.",
  "expectedSalary": 3000,
  "availableFrom": "2026-11-01"
}
```

**Returns:** `ApplicationResponse` with status `PENDING`. AI screening runs asynchronously, so scores may not be available in the initial response. The selected job must be open and accepting applications, and the resume must belong to the candidate.

## Update Application Status — `PATCH /api/applications/{id}/status`

```json
{"status":"SHORTLISTED"}
```

The owning employer updates the status. The service publishes a Kafka event for email notification. Candidates can retrieve their applications with `GET /api/applications/my`.

## Common Response Shapes

**Paginated list:**

```json
{
  "content": [],
  "totalElements": 0,
  "totalPages": 0,
  "number": 0,
  "size": 20
}
```

Controllers return Spring Data `Page` objects; additional pagination metadata may be present.

**Operation result (`ApiResponse`):**

```json
{
  "message": "Application deleted successfully",
  "status": true
}
```

[Back to the project README](../../README.md#api-reference)
