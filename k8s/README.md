# Kubernetes Deployment

These manifests deploy the complete Lanka MicroJob stack using images published by the Jenkins
pipeline to the `nisa2003one` Docker Hub account.

## Resources

- Eight Deployments: PostgreSQL, six Java services, and the frontend
- Eight Services: internal ClusterIP services plus frontend and gateway NodePorts
- One PostgreSQL PersistentVolumeClaim
- One shared ConfigMap for non-secret application settings
- One shared Secret for credentials and signing keys

## Before deployment

1. Confirm that all seven application images exist in Docker Hub.
2. Replace every development value in `secret.yaml`.
3. Update `CORS_ALLOWED_ORIGINS` in `configmap.yaml` if the application will not be accessed through
   `localhost:30300`.
4. Set `SEED_DEMO_DATA` to `false` in `configmap.yaml` for a non-demo environment.

For a private Docker Hub repository, create an image pull secret and add it to each pod template:

```powershell
kubectl create secret docker-registry dockerhub-registry `
  --docker-server=https://index.docker.io/v1/ `
  --docker-username=<username> `
  --docker-password=<access-token>
```

```yaml
spec:
  imagePullSecrets:
    - name: dockerhub-registry
```

## Deploy

```powershell
kubectl apply -f k8s/
kubectl get pods
kubectl get services
```

Wait for all workloads:

```powershell
kubectl rollout status deployment/postgres --timeout=180s
kubectl rollout status deployment/user-service --timeout=300s
kubectl rollout status deployment/job-service --timeout=300s
kubectl rollout status deployment/matching-service --timeout=300s
kubectl rollout status deployment/broker-service --timeout=300s
kubectl rollout status deployment/notification-service --timeout=300s
kubectl rollout status deployment/api-gateway --timeout=300s
kubectl rollout status deployment/frontend --timeout=180s
```

## Access

| Component | URL |
|---|---|
| Frontend | http://localhost:30300 |
| API gateway | http://localhost:30900 |

For Minikube, replace `localhost` with the value returned by `minikube ip` and update the ConfigMap's
CORS origins before applying it.

## Update images

Every application Deployment uses `imagePullPolicy: Always`. After Jenkins publishes new `latest`
images, restart the deployments:

```powershell
kubectl rollout restart deployment/user-service deployment/job-service deployment/matching-service
kubectl rollout restart deployment/broker-service deployment/notification-service deployment/api-gateway deployment/frontend
```

## Troubleshooting

```powershell
kubectl get pods -o wide
kubectl describe pod <pod-name>
kubectl logs <pod-name>
kubectl logs deployment/api-gateway
```

If a pod reports `ImagePullBackOff`, verify the Docker Hub image name and configure
`imagePullSecrets` when the repository is private.
