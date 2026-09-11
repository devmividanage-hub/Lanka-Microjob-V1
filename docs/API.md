# API reference

Base URL in development: `http://localhost:9000` (the gateway). In Docker/Kubernetes the frontend nginx
proxies the same paths, so browser code uses relative URLs.

All error responses share one shape:

```json
{ "timestamp": "2026-09-09T10:12:00", "status": 409, "error": "Conflict",
  "message": "You have already applied for this job", "path": "/applications",
  "fieldErrors": { } }
```

`fieldErrors` is populated only for bean-validation failures (400).

Authentication is a JWT in `Authorization: Bearer <token>`. Claims: `uid`, `name`, `role`
(`ADMIN|EMPLOYER|WORKER|BROKER`) and, for workers, a `skills` snapshot. Broker tokens add `brokerId` and
`district`. Tokens are signed with the shared `JWT_SECRET` (HS256).

---

## user-service — `/auth` (port 9001)

| Method | Path | Access | Notes |
|--------|------|--------|-------|
| POST | `/auth/register` | public | Body: name, mobile, email, password, role, district, city, skills, nic, availability. Creates the user as `PENDING`. Re-registering an existing identifier **cannot** overwrite the password of an approved account (409) |
| POST | `/auth/login` | public | Worker/employer login. Only `APPROVED` accounts receive a token; `PENDING` → 403 with a clear message |
| POST | `/auth/admin/login` | public | Admin login against the `admins` table |
| GET | `/auth/me` | authenticated | Current profile from the token — used by the UI to refresh approval status |
| GET | `/auth/users/pending` | ADMIN | Approval queue |
| GET | `/auth/users` | ADMIN | All users, optional `?role=`/`?district=` |
| PUT | `/auth/users/{id}/approve` | ADMIN | Sets `APPROVED` |
| PUT | `/auth/users/{id}/reject` | ADMIN | Sets `REJECTED` |
| GET | `/auth/stats` | ADMIN | Counts by role and status for the admin dashboard |

## job-service — `/jobs` (port 9002)

| Method | Path | Access | Notes |
|--------|------|--------|-------|
| POST | `/jobs` | EMPLOYER | Employer identity is taken from the JWT, not the request body |
| GET | `/jobs` | public | Filters: `district`, `city`, `category`, `search`, `includeClosed`. Past-date jobs are expired lazily on read |
| GET | `/jobs/{id}` | public | Single job |
| GET | `/jobs/mine` | EMPLOYER | The caller's own jobs, optional `?status=` |
| GET | `/jobs/employer/{id}` | EMPLOYER (owner) or ADMIN | Ownership-checked |
| GET | `/jobs/stats` | authenticated | Platform counts used by dashboards |
| GET | `/jobs/admin` | ADMIN | Complete job directory, including closed/expired/flagged jobs and `applicationCount`; used so admin KPI and detail counts match |
| PUT | `/jobs/{id}/status` | EMPLOYER (owner) or ADMIN | `?status=ASSIGNED|IN_PROGRESS|COMPLETED|CANCELLED`. Illegal transitions → 409 |
| PUT | `/jobs/{id}/flag` | ADMIN | Moderation: hides the job from the feed |

Job lifecycle: `OPEN → ASSIGNED → IN_PROGRESS → COMPLETED`, plus `CANCELLED`, `EXPIRED`, `FLAGGED`.

## job-service — `/applications` (port 9002)

| Method | Path | Access | Notes |
|--------|------|--------|-------|
| POST | `/applications` | WORKER | Body `{jobId, message}`. Unique per `(job, worker)` → duplicates 409. Closed/full/expired/flagged jobs are rejected with 409 and a human reason |
| GET | `/applications/mine` | WORKER or EMPLOYER | Worker: own applications. Employer: applications received on own jobs |
| GET | `/applications/worker/{id}` | that worker or ADMIN | |
| GET | `/applications/employer/{id}` | that employer or ADMIN | |
| GET | `/applications/job/{jobId}` | job owner or ADMIN | |
| GET | `/applications/summary/worker` | WORKER | Dashboard counters |
| GET | `/applications/summary/employer` | EMPLOYER | Dashboard counters |
| PUT | `/applications/{id}/accept` | job owner | Decrements `slotsRemaining`; at zero the job becomes `ASSIGNED`; notifies the worker |
| PUT | `/applications/{id}/reject` | job owner | Notifies the worker |
| PUT | `/applications/{id}/complete` | job owner or that worker | When every accepted application is complete the job becomes `COMPLETED` |
| PUT | `/applications/{id}/cancel` | that worker | Withdrawal while still `APPLIED` |

Application statuses: `APPLIED → ACCEPTED → COMPLETED`, plus `REJECTED` and `CANCELLED`. Cancelling or
expiring a job cascades its `APPLIED` rows to `CANCELLED`.

## matching-service — `/matches` (port 9003)

| Method | Path | Access | Notes |
|--------|------|--------|-------|
| POST | `/matches` | WORKER, EMPLOYER, ADMIN, BROKER | Scores one job and **persists** a `MatchResult` audit row |
| POST | `/matches/batch` | same | Scores up to 200 jobs for the feed; does **not** persist |
| GET | `/matches/{jobId}` | same | Stored match history for a job |

Scoring rule (deterministic, no ML):

```
coverage = matchedSkills / requiredSkills          # "No Experience Needed" counts as matched
score    = round(coverage × baseWeight) + (sameDistrict ? districtBonus : 0)   # capped 0..100
label    = score ≥ 70 "Strong match" | ≥ 40 "Possible match" | else "Weak match"
```

Defaults `baseWeight=90`, `districtBonus=10` (`app.matching.*`). A job with no required skills scores a
neutral 50.

## broker-service — `/brokers` (port 9004)

| Method | Path | Access | Notes |
|--------|------|--------|-------|
| POST | `/brokers` | public | Duplicate email/phone → 409. Status `PENDING`; an admin notification is raised |
| POST | `/brokers/login` | public | Only `APPROVED` brokers get a JWT (`role=BROKER`, `brokerId`, `district`) |
| GET | `/brokers` | ADMIN | Complete broker application directory |
| GET | `/brokers/pending` | ADMIN | Review queue |
| PUT | `/brokers/{id}/approve` | ADMIN | Assigns the next free `BRK-%04d` reference, skipping collisions |
| PUT | `/brokers/{id}/reject` | ADMIN | |
| GET | `/brokers/stats` | ADMIN | Totals + per-district breakdown |
| GET | `/brokers/workers` | ADMIN | Complete offline-worker directory for admin details |
| GET | `/brokers/{brokerId}/dashboard` | that broker or ADMIN | Real counters + the broker's offline workers |
| GET | `/brokers/{brokerId}/workers` | that broker or ADMIN | Current offline workers managed by this broker |
| POST | `/brokers/{brokerId}/workers` | that broker | Registers an offline worker, **city-locked** to the broker's district |
| PUT | `/brokers/{brokerId}/workers/{workerId}/status` | that broker | `ACTIVE|ON_JOB|INACTIVE` |
| POST | `/brokers/{brokerId}/workers/{workerId}/placements` | that broker | Records a placement; commission = `round(payPerDay × 0.075)` |

Every broker-owned resource is ownership-checked: a broker cannot read or modify another broker's
workers (403, not 404, so the UI can explain the failure).

## notification-service — `/notifications` (port 9005)

| Method | Path | Access | Notes |
|--------|------|--------|-------|
| POST | `/notifications` | SYSTEM (`X-Internal-Token`) or ADMIN | Persists first, then "delivers" via a provider |
| GET | `/notifications` | ADMIN | Full log, newest first (the same complete set counted by `/notifications/stats`) |
| GET | `/notifications/stats` | ADMIN | Counts by status |
| GET | `/notifications/mine` | authenticated | The caller's own notifications |
| GET | `/notifications/recipient/{value}` | self or ADMIN | Lookup by email or mobile |
| GET | `/notifications/providers` | authenticated | Declares which providers are active |

Providers implement `NotificationProvider`; the shipped implementations are `SimulatedEmailProvider` and
`SimulatedSmsProvider`. They **never** open a network connection. Resulting rows have status `SIMULATED`
and the message text is written to the service log. `/notifications/providers` exists so the UI can state
this honestly instead of implying real delivery.

## api-gateway (port 9000)

Routes: `/auth/**` → user-service, `/jobs/**` and `/applications/**` → job-service, `/matches/**` →
matching-service, `/brokers/**` → broker-service, `/notifications/**` → notification-service,
`/api-docs/{service}` → that service's `/v3/api-docs`. Duplicate CORS headers are removed with
`DedupeResponseHeader`, and allowed origins come from `APP_CORS_ALLOWED_ORIGINS`.

`GET /` returns a small JSON document describing the routes (useful during a demo).

---

## Design notes

- **One shared database.** Six services sharing `lanka_microjob` is a deliberate simplification for a
  university project; each service still owns its own tables and never queries another service's tables.
- **Synchronous notifications.** `job-service` calls `notification-service` directly with a short timeout
  and tolerates failure (an acceptance still succeeds if notifications are down). No broker/queue needed at
  this scale.
- **Lazy expiry.** A scheduled cleaner would need another moving part; instead `GET /jobs` marks past-date
  `OPEN` jobs `EXPIRED` as it reads them, which is enough for the demo and keeps behaviour observable.
- **No payments.** Commission is *recorded*, never charged.
