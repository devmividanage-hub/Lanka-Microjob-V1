# Troubleshooting

## Nothing works: is the gateway up?

```bash
curl -s http://localhost:9000/actuator/health      # {"status":"UP"}
curl -s http://localhost:9000/                      # lists the configured routes
docker compose ps                                   # or: kubectl get pods
```

If the gateway is up but a route returns 503, that downstream service is still booting or crashed — check
its log (`docker compose logs job-service`).

## Login says "pending approval"

By design: worker and employer registration creates a `PENDING` account and login is refused until an
admin approves it. Approve it in the Admin panel. `SEED_DEMO_DATA=true` seeds jobs and a broker with two
offline workers, but it does not seed worker or employer login accounts.

## 401 on every authenticated call

Almost always a `JWT_SECRET` mismatch: the service that issued the token and the service verifying it must
share the same secret.

```bash
docker compose exec job-service printenv JWT_SECRET
docker compose exec user-service printenv JWT_SECRET   # must be identical
```

Tokens also expire after `JWT_EXPIRATION` milliseconds (default `86400000`, or 24 hours; the Kubernetes
ConfigMap sets `28800000`, or 8 hours) — log in again.

## 403 when accepting an application / managing a worker

The ownership check is doing its job: only the employer who posted the job (or an admin) may decide on its
applications, and only the broker who registered a worker may modify them. Confirm you are signed in as the
owner; the UI shows the signed-in identity in the header chip.

## 409 when applying

Three possible causes, all reported in the response `message`:

1. You already applied for that job — the unique constraint on `(job_id, worker_id)` prevents duplicates.
2. The job is no longer `OPEN` (assigned, in progress, completed, cancelled, expired or flagged).
3. All slots are filled (`slotsRemaining = 0`), which also moves the job to `ASSIGNED`.

## The job feed is empty

- Jobs are filtered by district when one is selected — clear the filters.
- Past-date `OPEN` jobs are marked `EXPIRED` lazily when the feed is read, so they disappear. Post a job
  with a future date.
- With `SEED_DEMO_DATA=true`, `job-service` seeds six open jobs on first boot only; it will not re-add them
  after you delete them.

## Frontend cannot reach the API

The browser must call the **same origin** it was served from; nginx proxies to `api-gateway:9000`.

```bash
curl -sI http://localhost:3000/jobs                 # proxied through nginx
curl -s  http://localhost:3000/healthz              # "ok"
docker compose exec frontend wget -qO- http://api-gateway:9000/actuator/health
```

If nginx fails to start with `host not found in upstream "api-gateway"`, the gateway container is not on
the same Compose network (or is named differently).

In the Vite dev server (`npm run dev`, port 3000) the same paths are proxied by `vite.config.js`; if you
changed the gateway port, update `server.proxy` there.

## CORS errors in the browser console

Set `CORS_ALLOWED_ORIGINS` to the exact origin the browser uses (scheme + host + port), e.g.
`http://localhost:5173`. With the nginx deployment no CORS header is needed at all because the origin is
the same.

## notification-service rejects writes with 401/403

Callers must send `X-Internal-Token` matching `INTERNAL_SERVICE_TOKEN`, or be an `ADMIN`. The value must be
identical in `user-service`, `job-service`, `broker-service` and `notification-service`.

## Database errors after changing entities

`ddl-auto=update` adds columns but never drops or retypes them. If the schema is in a strange state during
development:

```bash
docker compose down -v && docker compose up -d --build     # destroys the postgres volume
```

## Kubernetes pods stuck in CrashLoopBackOff

```bash
kubectl describe pod <pod>          # look at Events and the last state
kubectl logs <pod> --previous
kubectl get secret lanka-microjob-secrets -o jsonpath='{.data.JWT_SECRET}' | base64 -d | wc -c
```

The usual causes are a missing/short `JWT_SECRET`, an unreachable PostgreSQL host, or the secret not being
created before `kubectl apply -f k8s/`.

## Jenkins image does not appear in the cluster

The repository has no image-name consistency script. Compare the Jenkins push tag with the exact image
in the relevant Kubernetes manifest. The manifests use
`nisa2003one/lanka-microjob-<component>:latest`, while Jenkins uses the Docker Hub username supplied by
its `dockerhub-new` credential.

Application deployments use `imagePullPolicy: Always`. Confirm that Jenkins pushed to the account named
in the manifest and that the cluster can access the repository. If another Docker Hub account is used,
update the manifest image names before deployment.
