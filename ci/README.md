# Continuous integration

`github-actions.yml` is a complete, ready-to-use GitHub Actions workflow. It is stored here rather than in
`.github/workflows/` for one reason only: the automation token used to open this change is not permitted to
create or modify files under `.github/workflows/`, so the push would be rejected.

## Enable it

```bash
mkdir -p .github/workflows
cp ci/github-actions.yml .github/workflows/ci.yml
git add .github/workflows/ci.yml
git commit -m "ci: enable the GitHub Actions workflow"
git push
```

Anyone with write access to the repository (or a token holding the `workflows` permission) can do this in
one commit; no content change is required.

## What it runs

| Job | Steps |
|-----|-------|
| `backend` | Matrix over the six Java services: `mvn -B verify` (compiles, runs the unit and MockMvc tests), then publishes the JUnit/Surefire reports |
| `frontend` | `npm ci`, `npm run build`, and a syntax check over every ES module in `frontend/src` |
| `config` | `python3 scripts/validate_k8s.py`, `python3 scripts/check_image_names.py` and `docker compose config` — the same three checks the Jenkins `config-validation` stage runs |

The `config` job is the one worth keeping even if you drop the rest: it is what prevents the port, image
name and environment variable drift that this project originally suffered from.

Jenkins remains the deployment pipeline (see `jenkins/Jenkinsfile`); this workflow is the fast feedback
loop on every push and pull request.
