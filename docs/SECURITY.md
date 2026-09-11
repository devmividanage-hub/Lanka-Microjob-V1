# Security

## Authentication

- Passwords are stored as **BCrypt** hashes (`PasswordEncoder` bean in `user-service` and `broker-service`).
  No plaintext password is ever persisted or logged.
- `user-service` issues an HS256 JWT on login with claims `uid`, `name`, `role` and (for workers) a
  `skills` snapshot. `broker-service` issues its own tokens for approved brokers with `role=BROKER` plus
  `brokerId` and `district`.
- All services verify the token with the **same** `JWT_SECRET` from the environment. There is no default
  secret in committed configuration: without `JWT_SECRET` the services fail to start rather than silently
  falling back to a known key.
- Token lifetime is `JWT_EXPIRATION_MINUTES` (default 480). The frontend stores the token in
  `localStorage`, shows the remaining time, and clears the session on the first 401.
- Sessions are stateless: no server-side session, `SessionCreationPolicy.STATELESS` everywhere.

## Authorisation (RBAC)

| Role | Can |
|------|-----|
| `ADMIN` | Approve/reject users and brokers, read every queue and statistic, flag jobs, read all notifications |
| `EMPLOYER` | Post jobs, manage **own** jobs, accept/reject/complete applications **on own jobs** |
| `WORKER` | Browse the feed, apply, withdraw **own** applications, complete **own** assignments |
| `BROKER` | Log in once approved, manage **own** offline workers and placements |
| (public) | Browse open jobs, register, log in, apply to become a broker |

Method-level rules are declared in each `SecurityConfig`; ownership is enforced **again** in the service
layer, because a role check alone cannot tell one employer's job from another's.

## Ownership checks (IDOR prevention)

Every resource that belongs to a user is resolved from the token, never trusted from the request:

- `POST /jobs` takes the employer id/email from the principal — a caller cannot post a job "for" someone else.
- `PUT /jobs/{id}/status` and `/flag` require the job's employer (or an admin).
- `GET /applications/employer/{id}` and `/worker/{id}` require the caller to *be* that user (or an admin).
- `PUT /applications/{id}/accept|reject` loads the application, then the job, then compares the job's
  employer with the principal.
- `PUT /applications/{id}/cancel|complete` requires the applicant themself.
- Broker endpoints resolve the broker from the token and call `findOwnedWorker(brokerId, workerId)`, so
  broker A cannot read, re-status or place broker B's workers.

Violations return **403** with a message the UI can display, not a silent 404.

## Vulnerabilities found in the prototype and fixed

| # | Issue | Fix |
|---|-------|-----|
| S1 | `POST /auth/register` overwrote the password of an **existing approved** account (account takeover) | Registration now rejects a known email/mobile with 409 and never mutates an existing row |
| S2 | Admin seeder re-hashed and reset `admin123` on **every boot** | Admin is created only when absent; credentials come from `ADMIN_DEFAULT_*` |
| S3 | Hardcoded `jwt.secret` in every `application.properties` | `${JWT_SECRET}` with no committed default |
| S4 | Hardcoded DB credentials (`lanka/lanka`) in committed config | `${SPRING_DATASOURCE_*}` / `${POSTGRES_*}`; Kubernetes uses a Secret |
| S5 | `broker-service` was fully `permitAll`, and login returned **no token** | JWT issued on login; every mutating endpoint requires `BROKER` or `ADMIN` |
| S6 | `notification-service` accepted writes from anyone | Requires `ROLE_SYSTEM` (internal token) or `ADMIN`; reads limited to self/admin |
| S7 | Broker reference `BRK-%04d` derived from `count()+1` → collisions after deletions | Allocation loop that skips existing references, inside the approval transaction |
| S8 | `job-service` had no role restrictions and no employer ownership on jobs | `SecurityConfig` roles + ownership checks in `JobService`/`ApplicationService` |
| S9 | `ddl-auto=update` on a shared production-like database | Kept `update` for the demo but documented; manifests pin one schema owner and seeders are gated |
| S10 | CORS `allowedOriginPatterns=*` with credentials | Origins come from `APP_CORS_ALLOWED_ORIGINS`; `*` is documented as dev-only |
| S11 | No error contract — stack traces and Spring's default whitelabel leaked internals | `ApiError` + `@RestControllerAdvice` in every service; `server.error.include-*` set to `never` |
| S12 | Stored XSS: server text was injected with `innerHTML` in the frontend | All rendered values pass through `esc()` in `frontend/src/utils/dom.js` |

## Input validation

Every request body is a `record` DTO annotated with `@NotBlank`, `@Size`, `@Email`, `@Pattern`, `@Min`,
`@Positive` and `@Valid`-ated at the controller. Examples: mobile must match a Sri Lankan pattern, pay per
worker must be positive, `workersNeeded` must be ≥ 1, job date must not be in the past, broker NIC must be
12 characters. Validation failures return 400 with a `fieldErrors` map the UI prints next to the field.

## Secrets management

- `.env` is git-ignored; `.env.example` documents every variable with placeholder values only.
- `docker-compose.yml` injects secrets through a shared `x-security-env` anchor — no secret is written in
  the Compose file.
- `k8s/secret.yaml` is the only place Kubernetes reads secrets from (`envFrom.secretRef`); the manifests
  contain no literal credentials.
- Jenkins reads registry credentials from the Jenkins credential store
  (`DOCKER_REGISTRY_CREDENTIALS_ID`), never from the repository.

## Known limitations (accepted for a university project)

- Tokens cannot be revoked before expiry (no refresh/blacklist store).
- Rate limiting is not implemented; a real deployment would put it in the gateway.
- One shared PostgreSQL database rather than a database per service.
- Notification delivery is **simulated** (see below) — by design, and labelled everywhere it is shown.

## Simulated notifications — what is and is not claimed

`notification-service` defines a `NotificationProvider` interface with two implementations,
`SimulatedEmailProvider` and `SimulatedSmsProvider`. They:

1. validate the channel and recipient,
2. write the rendered message to the service log,
3. return a `DeliveryResult` marked simulated.

The notification row is persisted **before** delivery is attempted, so a provider failure never loses the
event, and its status is stored as `SIMULATED` (or `FAILED`). `GET /notifications/providers` returns the
active provider names so the UI can print, for example, "email: SimulatedEmailProvider" rather than
implying a real gateway. No SMS API, SMTP server or third-party account is contacted anywhere in this
repository, and no code claims otherwise.
