# Lanka MicroJob

A district-aware micro job marketplace for Sri Lanka's informal labour market, built as a Spring Boot
microservice system with a static Vite frontend, PostgreSQL, Docker Compose, Kubernetes manifests and a
Jenkins pipeline.

Employers post day jobs, workers apply, employers accept or reject applicants, and **brokers** register
offline workers who cannot use the app themselves and earn commission on placements. Every worker and
employer account must be approved by an administrator before it can transact.

> **Honesty note for markers.** Two things in this project are deliberately *not* what they look like,
> and both are labelled as such in the UI and the API:
> - **Notifications are simulated.** There is no SMS gateway and no mail server. Messages are persisted in
>   PostgreSQL, logged by `notification-service`, and returned with status `SIMULATED`. See
>   `docs/SECURITY.md`.
> - **Matching is rule-based, not machine learning.** The score is a transparent skill-coverage formula
>   (`round(coverage × 90) + 10` for the same district, capped at 100). See `matching-service`.

---

## 1. Architecture

| Service              | Port | Responsibility |
|----------------------|------|----------------|
| `api-gateway`        | 9000 | Single entry point, routing, CORS, header de-duplication |
| `user-service`       | 9001 | Registration, login, JWT issuing, admin account, approvals |
| `job-service`        | 9002 | Jobs, job lifecycle, **job applications** |
| `matching-service`   | 9003 | Rule-based skill/district scoring |
| `broker-service`     | 9004 | Broker applications, approval, offline workers, placements |
| `notification-service` | 9005 | Notification log + provider abstraction (simulated providers) |
| `frontend`           | 80/3000 | Static UI served by nginx, which reverse-proxies the API paths to the gateway |
| `postgres`           | 5432 | One shared PostgreSQL 16 database, `lanka_microjob` |

Requests from the browser go to nginx on the **same origin**, which proxies `/auth`, `/jobs`,
`/applications`, `/matches`, `/brokers`, `/notifications` and `/api-docs` to the gateway. No CORS
problems, no `localhost` calls from browser code.

```
browser ──▶ nginx (frontend) ──▶ api-gateway:9000 ──▶ user-service:9001
                                                 └──▶ job-service:9002
                                                 └──▶ matching-service:9003
                                                 └──▶ broker-service:9004
                                                 └──▶ notification-service:9005
                                                            │
                                                     PostgreSQL 5432
```

Service-to-service calls (e.g. `job-service` announcing an acceptance) carry an `X-Internal-Token`
header; `notification-service` rejects writes without it.

## 2. Quick start (Docker Compose)

```bash
cp .env.example .env        # then change every password/secret
docker compose up -d --build
```

| URL | What |
|-----|------|
| http://localhost:3000 | Frontend |
| http://localhost:9000/actuator/health | Gateway health |
| http://localhost:9000/api-docs/job-service | OpenAPI document for job-service |

Stop with `docker compose down` (add `-v` to also drop the database volume).

### Without Docker (local JVM)

Start PostgreSQL, create the database, export the variables from `.env.example`, then per service:

```bash
cd user-service && mvn spring-boot:run     # repeat for job/matching/broker/notification/api-gateway
cd frontend && npm ci && npm run dev       # Vite dev server on :3000, proxies the API to :9000
```

## 3. Demo accounts

Created by the seeders when `SEED_DEMO_DATA=true` (the default in `.env.example`).

| Role | Login | Password |
|------|-------|----------|
| Admin | `admin@lanka.lk` | value of `ADMIN_DEFAULT_PASSWORD` |
| Employer | `demo.employer@lanka.lk` | `SEED_DEMO_PASSWORD` (`demo1234`) |
| Worker | `demo.worker@lanka.lk` | `SEED_DEMO_PASSWORD` (`demo1234`) |
| Broker | `demo.broker@lanka.lk` | `SEED_DEMO_PASSWORD` (`demo1234`) — reference `BRK-0001` |

Both demo user accounts are seeded **APPROVED** so the flows below can be demonstrated immediately.
Set `SEED_DEMO_DATA=false` for anything resembling a real deployment.
This prevents new demo rows from being inserted. Existing seeded rows remain in the named PostgreSQL
volume until they are deleted explicitly or the volume is intentionally removed.

## 4. End-to-end demo script

1. **Admin approves accounts** — log in at *Admin* → the pending-user and pending-broker queues are read
   from `GET /auth/users/pending` and `GET /brokers/pending`. Approve one of each and watch the counters move.
2. **Employer posts a job** — *Post Job* → `POST /jobs`. The employer identity comes from the JWT, never
   from the form. The job immediately appears in the worker feed.
3. **Worker applies** — *Feed* → *Apply* → `POST /applications`. Applying twice returns **409** (a unique
   constraint on `(job_id, worker_id)`), not a duplicate row.
4. **Employer decides** — *Dashboard* → *Accept* → `PUT /applications/{id}/accept`. `slots_remaining`
   decrements; when it reaches 0 the job becomes `ASSIGNED` and further applications are rejected with 409.
   The worker receives a notification (simulated) and sees the decision on their dashboard.
5. **Broker registers an offline worker** — *Broker* → log in as `BRK-0001` → *Add Worker*. The worker is
   city-locked to the broker's approved district. *Placement* records a job and derives real commission
   (7.5% of daily pay), which updates the broker's totals.

Dashboard refreshes are API-driven: opening a dashboard and every successful mutation re-fetches the
authoritative PostgreSQL-backed lists and counters. Admin KPI cards open one reusable detail modal; those
lists are loaded from the admin-protected user, broker, worker, job and notification endpoints.

## 5. Configuration

Every secret comes from the environment. Nothing sensitive is committed.

| Variable | Used by | Purpose |
|----------|---------|---------|
| `JWT_SECRET` | user, job, matching, broker | HS256 signing key — **must be identical** across services |
| `JWT_EXPIRATION_MINUTES` | user, broker | Token lifetime (default 480) |
| `INTERNAL_SERVICE_TOKEN` | user, job, broker, notification | Service-to-service authorisation |
| `BROKER_COMMISSION_RATE` | job, broker | Server-side commission rate for real job placements |
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | all Java services | PostgreSQL connection |
| `POSTGRES_DB/USER/PASSWORD` | postgres container | Database bootstrap |
| `ADMIN_DEFAULT_EMAIL/PASSWORD/NAME` | user-service | Admin created **only if absent** |
| `SEED_DEMO_DATA`, `SEED_DEMO_PASSWORD` | user, job, broker | Demo seeding |
| `NOTIFICATION_PROVIDERS` | notification-service | `simulated-email,simulated-sms` |
| `APP_CORS_ALLOWED_ORIGINS` | gateway + services | Comma-separated allowed origins |

Full template with explanations: [`.env.example`](.env.example).

## 6. Tests

```bash
mvn -f user-service/pom.xml verify          # JWT claims/expiry + registration/login/approval flow (H2)
mvn -f matching-service/pom.xml test        # scoring rules: coverage, district bonus, caps, labels
mvn -f job-service/pom.xml test             # lifecycle transitions that gate applications
```

| Module | Tests | What they prove |
|--------|-------|-----------------|
| `user-service` | `JwtUtilTest` (5), `AuthIntegrationTest` (11) | Tokens carry `uid`/`role`/`skills` and expire; duplicate registration cannot hijack an approved account; login is rejected before approval; admin approval/rejection works over HTTP |
| `matching-service` | `MatchingServiceTest` (11) | The published scoring formula, normalisation, wildcards, 0–100 cap and label thresholds |
| `job-service` | `JobLifecycleTest` (11) | Only legal status transitions succeed; terminal states are frozen; `COMPLETE` legacy values normalise; blocked-application reasons are correct |

Integration tests run against H2 in PostgreSQL mode (`src/test/resources/application.properties`) with the
notification client disabled, so no external service is required.

## 7. Deploying

- **Kubernetes:** `kubectl apply -f k8s/` — see [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md). Create
  `k8s/secret.yaml` from `.env.example` first; the manifests reference it via `envFrom`.
- **Jenkins:** [`jenkins/Jenkinsfile`](jenkins/Jenkinsfile) — builds and tests every service in parallel,
  validates the Compose/Kubernetes configuration, builds images as `lanka-microjob/<service>:latest`, and
  optionally pushes and rolls out (`PUSH_IMAGES=true`, `DEPLOY_K8S=true`, `main` branch only).

Two repository scripts keep the infrastructure honest and run in CI:

```bash
python3 scripts/validate_k8s.py       # ports, env vars, probes, image names across all manifests
python3 scripts/check_image_names.py  # compose == Jenkins == Kubernetes image names
```

## 8. Documentation

- [`docs/API.md`](docs/API.md) — endpoint reference for all six services
- [`docs/SECURITY.md`](docs/SECURITY.md) — authentication, authorisation, ownership checks, fixed vulnerabilities
- [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) — Compose, Kubernetes and Jenkins
- [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md) — common failures and how to diagnose them
