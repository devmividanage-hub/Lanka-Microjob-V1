# Lanka MicroJob Platform

## Project Overview

Lanka MicroJob is a service-oriented, microservice-based platform for connecting employers, workers,
and brokers for short-term and daily-wage work in Sri Lanka. Employers publish jobs, workers apply for
them, and approved brokers can represent workers who do not use the application directly. An
administrator reviews user and broker registrations and moderates the platform.

This repository is intended as a university demonstration. It contains six Java services, a browser
frontend, PostgreSQL persistence, and deployment examples for Docker Compose, Jenkins, and Kubernetes.

## Main Actors

| Actor | Role in the platform |
| --- | --- |
| Admin | Approves or rejects worker, employer, and broker registrations; views platform records and statistics; flags jobs |
| Employer | Creates jobs, reviews applications to their own jobs, manages job status, and rates broker placements |
| Worker | Browses jobs, applies for work, tracks applications, withdraws pending applications, and completes accepted work |
| Broker | Applies for approval, registers offline workers, finds eligible jobs for them, and records placements |

Only `WORKER` and `EMPLOYER` accounts use `/auth/register`. Brokers have a separate application and
login flow in `broker-service`, and the bootstrap administrator is created by `user-service`.

## Main Features

- Worker and employer registration with administrator approval
- Separate broker application, approval, and login flow
- JWT-based login and role-based access control
- Job creation, filtering, lifecycle management, and administrator flagging
- Worker applications with accept, reject, cancel, and completion actions
- Rule-based job matching using skill coverage and district proximity
- Broker-managed offline workers, eligible-job lookup, placements, commissions, and ratings
- Persisted notification history using simulated email and SMS providers
- API routing through a Spring Cloud Gateway
- OpenAPI documents for the five domain services

## Microservices

| Service | Responsibility | Internal port |
| --- | --- | ---: |
| `user-service` | Worker/employer registration, authentication, profiles, user approval, and administration | 9001 |
| `job-service` | Jobs, applications, job lifecycle, broker placement reservations, and placement ratings | 9002 |
| `matching-service` | Deterministic skill and district scoring, plus stored single-match history | 9003 |
| `broker-service` | Broker applications, offline-worker management, eligible-job lookup, and placements | 9004 |
| `notification-service` | Notification persistence, retrieval, statistics, and simulated delivery providers | 9005 |
| `api-gateway` | Entry point and route configuration for backend APIs and OpenAPI documents | 9000 |

The `frontend` is not a microservice: it is a static HTML/CSS/JavaScript application built with Vite
and served by nginx.

## Architecture

```text
Browser
  |
  v
Frontend (Vite build served by nginx)
  |
  v
API Gateway
  |-- user-service
  |-- job-service
  |-- matching-service
  |-- broker-service ----> job-service (eligible jobs and placements)
  `-- notification-service <---- user/job/broker services
          |
          v
Shared PostgreSQL database <---- user/job/matching/broker services
```

The five domain services share one PostgreSQL database for this project, while keeping separate tables
and repository code. Browser requests are routed through the gateway. Internal notification calls and
broker-to-job-service calls use the existing synchronous HTTP clients.

## Technologies Used

- Java 21
- Spring Boot 3.3.5
- Spring Web, Spring Data JPA, Spring Security, and Bean Validation
- Spring Cloud Gateway
- JSON Web Tokens (JWT) using JJWT
- PostgreSQL 16 and H2 for tests
- Maven, JUnit 5, Mockito, MockMvc, WebTestClient, and JaCoCo
- HTML, CSS, JavaScript, Vite, and nginx
- Docker and Docker Compose
- Jenkins and SonarQube integration in the Jenkins pipeline
- Kubernetes manifests

## Authentication and Authorization

`user-service` issues JWTs for approved workers, employers, and the administrator. `broker-service`
issues JWTs for approved brokers. Services validate tokens using the same `JWT_SECRET` and enforce
role-based access for `ADMIN`, `EMPLOYER`, `WORKER`, and `BROKER`. Service-layer ownership checks also
prevent an employer or broker from managing another account's records.

The repository contains development-only fallback credentials and example Kubernetes secret values.
Replace them before any non-demonstration deployment; do not commit real secrets.

## Broker Flow

1. A prospective broker submits a public application, which starts in `PENDING` state.
2. An administrator approves or rejects it. Approval assigns an available reference such as
   `BRK-0001`.
3. An approved broker logs in and receives a `BROKER` JWT.
4. The broker registers offline workers. Each worker inherits the approved broker's district and city.
5. The broker requests real eligible jobs from `job-service` and places an `ACTIVE` worker into a job.
6. `job-service` validates the job and reserves a slot. The platform records commission at the configured
   rate (7.5% by default); it does not process a payment.

## Matching Logic

Matching is deterministic and rule based; it is not machine learning. Skill names are trimmed,
lower-cased, and de-duplicated before comparison. “No Experience Needed”, “No Experience Required”,
“No Experience”, and “None” count as satisfied requirements.

```text
coverage = matched required skills / real required skills
score = round(coverage * base weight) + same-district bonus
```

The default base weight is 90 and the same-district bonus is 10. Scores are capped between 0 and 100.
A job with no skill requirements receives a neutral score of 50. Scores of at least 70 are labelled
“Strong match”, scores of at least 40 are “Possible match”, and lower scores are “Weak match”. Batch
feed matching does not persist results; single-match requests do.

## Notification Service

Notifications are persisted and handled internally by `notification-service`. The included email and
SMS providers are simulations: they write messages to the application log and store the status as
`SIMULATED`. The repository does not connect to an SMTP server, SMS gateway, or other external delivery
provider.

## Testing

All six Maven services now contain automated tests. Tests are deterministic and do not require Docker,
PostgreSQL, the internet, or other running microservices.

| Service | Test coverage present |
| --- | --- |
| `user-service` | JWT validation plus HTTP registration, login, approval, authorization, profile, and statistics flows |
| `job-service` | Job lifecycle transitions, terminal states, normalization, and application-blocking reasons |
| `matching-service` | Skill coverage, district bonus, empty/null skills, score caps, and recommendation thresholds |
| `broker-service` | Context/public endpoint checks, authentication guard, broker lookup, ownership, worker lists, invalid status, and worker registration |
| `notification-service` | Context and secured HTTP checks, simulated creation/retrieval, provider failure handling, recipient authorization, statistics, and provider descriptions |
| `api-gateway` | Application startup, discovery response, and loading all configured service/documentation routes |

Run every suite from the repository root:

```bash
mvn -f user-service/pom.xml test
mvn -f job-service/pom.xml test
mvn -f matching-service/pom.xml test
mvn -f broker-service/pom.xml test
mvn -f notification-service/pom.xml test
mvn -f api-gateway/pom.xml test
```

There is no Maven wrapper in this repository, so a local Maven installation and Java 21 are required.

## How to Run

### Docker Compose

Copy the environment template, replace every development credential, then start the stack:

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps
```

| Component | URL |
| --- | --- |
| Frontend | `http://localhost:3000` |
| API gateway (direct Compose host port) | `http://localhost:9010` |
| Gateway health | `http://localhost:9010/actuator/health` |

The frontend uses same-origin nginx proxy routes, so normal browser use starts at port 3000. Stop the
stack with `docker compose down`. Adding `-v` also deletes the PostgreSQL volume and its data.

### Local development without Docker

Start PostgreSQL and configure the variables used in each service's `application.properties`. Then run
each service in a separate terminal:

```bash
cd user-service && mvn spring-boot:run
cd job-service && mvn spring-boot:run
cd matching-service && mvn spring-boot:run
cd broker-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
cd api-gateway && mvn spring-boot:run
cd frontend && npm ci && npm run dev
```

For local JVM execution, the gateway listens on port 9000 and the Vite frontend listens on port 3000.
Override `USER_SERVICE_URL`, `JOB_SERVICE_URL`, `MATCHING_SERVICE_URL`, `BROKER_SERVICE_URL`, and
`NOTIFICATION_SERVICE_URL` with the corresponding `http://localhost:9001` through
`http://localhost:9005` addresses; the gateway defaults use Docker service names.

### Demonstration data

With `SEED_DEMO_DATA=true`, `job-service` seeds six jobs and `broker-service` seeds one approved broker
with two offline workers when their tables are empty. `user-service` seeds only the bootstrap admin; it
does not seed demo worker or employer accounts. Register those accounts through the UI and approve them
as the administrator.

The Compose defaults use `admin@gmail.com` / `admin123` for the development administrator and
`demo.broker@lanka.lk` / `demo1234` for the demonstration broker unless overridden. These are not
production credentials.

## Configuration Notes

- Runtime JWT lifetime uses `JWT_EXPIRATION` in milliseconds (default `86400000`; Kubernetes sets
  `28800000`).
- Allowed browser origins use `CORS_ALLOWED_ORIGINS`.
- Provider modes use `notification.provider.email` and `notification.provider.sms`, or their Spring
  environment equivalents; both default to `SIMULATED`.
- `SEED_DEMO_BROKER_EMAIL` and `SEED_DEMO_BROKER_PASSWORD` configure the demonstration broker.

## Known Limitations

- Email and SMS delivery are simulated.
- Matching is a fixed rule-based score rather than machine learning.
- All domain services share one database, which is suitable for the demonstration but not independent
  microservice data ownership.
- Notifications and other service-to-service calls are synchronous; there is no message broker.
- JWTs cannot be revoked before expiry, and the gateway does not implement rate limiting.
- Schema management uses Hibernate `ddl-auto=update`; there are no versioned database migrations.
- The infrastructure and committed credentials are demonstration-level and require hardening for a real
  deployment.

## Additional Documentation

- [`docs/API.md`](docs/API.md) — endpoint reference
- [`docs/SECURITY.md`](docs/SECURITY.md) — authentication, authorization, and security limitations
- [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) — Compose, Kubernetes, and Jenkins behavior
- [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md) — common failures and diagnostic commands
- [`ci/README.md`](ci/README.md) — optional GitHub Actions workflow
- [`k8s/README.md`](k8s/README.md) — Kubernetes-specific deployment notes
