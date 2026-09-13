# Continuous Integration

The repository includes `ci/github-actions.yml` as a workflow template. It is stored outside
`.github/workflows`, so GitHub does not run it automatically.

## Current Workflow Contents

| Job | Current steps |
| --- | --- |
| `backend` | Runs `mvn -B -ntp verify` for each of the six Java services and uploads Surefire reports |
| `frontend` | Runs `npm ci`, `npm run build`, and the optional `test:ui` script if it exists |
| `config` | Validates Docker Compose and attempts to run two Python configuration checks |

The referenced files `scripts/validate_k8s.py` and `scripts/check_image_names.py` are not present in this
repository. Consequently, the `config` job cannot currently pass as written. This documentation does not
claim those checks are available, and no replacement production or infrastructure scripts have been
added as part of the documentation/test work.

## Enabling the Workflow

After either adding the missing validation scripts or removing those two steps from the workflow, copy
the template into GitHub's workflow directory:

```bash
mkdir -p .github/workflows
cp ci/github-actions.yml .github/workflows/ci.yml
git add .github/workflows/ci.yml
git commit -m "ci: enable GitHub Actions"
git push
```

The Jenkins pipeline in `jenkins/Jenkinsfile` remains a separate Windows-oriented build, analysis,
image-publishing, and Kubernetes deployment pipeline.
