# Deployment

The repository contains Docker Compose configuration for a local demo, Kubernetes manifests for a
cluster, and a Windows-oriented Jenkins CI/CD pipeline. Image names are not identical in all three:
Compose builds `lanka-microjob/<service>:latest`, while the Kubernetes manifests reference
`nisa2003one/lanka-microjob-<service>:latest`. Jenkins first builds the Compose-style local name and then
tags images as `<DOCKER_USERNAME>/lanka-microjob-<service>:latest` before pushing them.

## Ports

| Component | Port |
|-----------|------|
| api-gateway | 9000 in the container; Compose host port 9010; Kubernetes NodePort 30900 |
| user-service | 9001 |
| job-service | 9002 |
| matching-service | 9003 |
| broker-service | 9004 |
| notification-service | 9005 |
| frontend (nginx) | 3000 on the host, 80 in the container (NodePort 30300) |
| postgres | 5432 |

The repository does not currently include an automated Kubernetes/Compose consistency checker. Verify
these values directly in `docker-compose.yml` and `k8s/*.yaml` when changing deployment configuration.

## 1. Docker Compose

```bash
cp .env.example .env      # change every secret
docker compose up -d --build
docker compose ps
```

- PostgreSQL waits for `pg_isready` before any Java service starts (`depends_on: condition: service_healthy`).
- Java services use their HTTP Actuator health endpoint, so `docker compose ps` reflects application
  startup rather than only container startup.
- The database lives in the named volume `postgres-data`; `docker compose down -v` destroys it.
- The frontend is built with `VITE_API_BASE` empty, so the browser calls the same origin and nginx proxies
  to `api-gateway:9000` (the Compose service name — not `localhost`, which would be the container itself).

Useful commands:

```bash
docker compose logs -f job-service
docker compose exec postgres psql -U lanka -d lanka_microjob -c '\dt'
docker compose restart notification-service
```

## 2. Kubernetes

```bash
# 1. create the secret from your real values (never commit it)
kubectl create secret generic lanka-microjob-secrets \
  --from-env-file=.env --dry-run=client -o yaml > k8s/secret.yaml

# 2. apply everything
kubectl apply -f k8s/

# 3. check
kubectl get pods -w
kubectl get svc
```

- `k8s/secret.yaml` in the repository is a **placeholder template**; overwrite it as shown above before
  deploying anywhere real.
- PostgreSQL uses a `PersistentVolumeClaim`, so job and application data survives pod restarts.
- Every Java deployment probes `/actuator/health` (liveness + readiness) with a generous
  `initialDelaySeconds`, and declares CPU/memory requests and limits.
- Access: gateway at `http://<node>:30900`, frontend at `http://<node>:30300`.
- Rollout a new image: `kubectl set image deployment/job-service job-service=lanka-microjob/job-service:latest`
  then `kubectl rollout status deployment/job-service`.

## 3. Jenkins

`jenkins/Jenkinsfile` currently runs the following stages:

1. Checks out the configured `main` branch.
2. Runs `mvn clean package` sequentially for all six Java services while the frontend build runs in
   parallel.
3. Runs a Maven SonarQube analysis for each Java service.
4. Builds local Docker images for the six services and frontend.
5. Logs in with the configured `dockerhub-new` credential, tags the images under the authenticated Docker
   Hub user, and pushes them.
6. Applies the Kubernetes manifests to the `docker-desktop` context, restarts all application
   deployments, and waits for their rollout status.

The current pipeline does not have optional push/deploy flags, a Compose/Kubernetes validation stage,
build-number image tags, or JUnit publishing. It expects Windows `bat`, Maven/JDK tool names, Docker,
SonarQube, Docker Hub credentials, and `kubectl` to be configured on the Jenkins agent.

## Environment variables

See [`.env.example`](../.env.example) for the complete list with explanations. The critical ones:

- `JWT_SECRET` — identical in every service that validates JWTs, and at least 32 characters.
- `INTERNAL_SERVICE_TOKEN` — identical in the callers and in notification-service.
- `SPRING_DATASOURCE_*` — in Kubernetes the host is the `postgres` Service name; in Compose the same.
- `JWT_EXPIRATION` — token lifetime in milliseconds.
- `CORS_ALLOWED_ORIGINS` — comma-separated browser origins.
- `SEED_DEMO_DATA=false` for anything that is not a demonstration.
