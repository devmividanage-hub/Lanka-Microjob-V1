# Deployment

Three supported paths: Docker Compose (local demo), Kubernetes (cluster), Jenkins (CI/CD). All three use
the **same image names** — `lanka-microjob/<service>:latest` — and `scripts/check_image_names.py` fails the
build if they ever drift apart again.

## Ports

| Component | Port |
|-----------|------|
| api-gateway | 9000 (Kubernetes NodePort 30900) |
| user-service | 9001 |
| job-service | 9002 |
| matching-service | 9003 |
| broker-service | 9004 |
| notification-service | 9005 |
| frontend (nginx) | 3000 on the host, 80 in the container (NodePort 30300) |
| postgres | 5432 |

These values are asserted by `scripts/validate_k8s.py`, which also checks that every deployment declares
matching `containerPort`, `SPRING_DATASOURCE_*`, `JWT_SECRET`, probes and resource limits.

## 1. Docker Compose

```bash
cp .env.example .env      # change every secret
docker compose up -d --build
docker compose ps
```

- PostgreSQL waits for `pg_isready` before any Java service starts (`depends_on: condition: service_healthy`).
- Java services use a TCP healthcheck against their own port, so `docker compose ps` reflects reality.
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

`jenkins/Jenkinsfile` runs:

1. **Build & test** — `mvn verify` for all six Java services in parallel, JUnit results published.
2. **Config validation** — `scripts/validate_k8s.py`, `scripts/check_image_names.py` and
   `docker compose config` so a broken manifest never reaches a cluster.
3. **Frontend** — `npm ci && npm run build`.
4. **Images** — builds and tags `lanka-microjob/<service>:latest` (plus `:${BUILD_NUMBER}`) for the six
   services and the frontend.
5. **Push** *(optional)* — only when `PUSH_IMAGES=true` and `DOCKER_REGISTRY_CREDENTIALS_ID` is set.
6. **Deploy** *(optional)* — only when `DEPLOY_K8S=true` on the `main` branch: `kubectl apply -f k8s/`,
   then `kubectl set image` + `kubectl rollout status` per deployment.

Set these as Jenkins parameters or environment variables. `post { always { ... } }` archives the frontend
build and publishes test reports whether or not the pipeline succeeded.

## Environment variables

See [`.env.example`](../.env.example) for the complete list with explanations. The critical ones:

- `JWT_SECRET` — identical in user, job, matching and broker services, ≥ 32 characters.
- `INTERNAL_SERVICE_TOKEN` — identical in the callers and in notification-service.
- `SPRING_DATASOURCE_*` — in Kubernetes the host is the `postgres` Service name; in Compose the same.
- `SEED_DEMO_DATA=false` for anything that is not a demonstration.
