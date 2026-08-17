# Branch Protection Setup for `main`

This document describes the required branch protection rules for the `main` branch.

## Required Configuration (GitHub Repository Settings)

Go to: **Settings → Branches → Branch protection rules → Add rule**

### Branch pattern
```
main
```

### Protection Rules (Required)

| Setting | Value | Description |
|---------|-------|-------------|
| **Require a pull request before merging** | ✅ Enforced | All changes must go through PR |
| **Require approvals** | 1 | At least 1 approving review |
| **Dismiss stale PR approvals when new commits are pushed** | ✅ Enforced | Prevents stale approvals |
| **Require review from Code Owners** | ✅ Enforced | If CODEOWNERS file exists |
| **Require status checks to pass before merging** | ✅ Enforced | **Critical for P0** |
| **Status checks required** | `ci` | Must match GitHub Actions workflow name |
| **Require branches to be up to date before merging** | ✅ Enforced | Prevents merge conflicts |
| **Require linear history** | ✅ Enforced | No merge commits |
| **Require conversation resolution before merging** | ✅ Enforced | All comments resolved |
| **Require signed commits** | ⚠️ Optional | If using GPG signing |
| **Require deployments to succeed before merging** | ❌ Disabled | Not applicable |
| **Lock branch** | ✅ Enforced | Prevents force pushes |
| **Do not allow bypassing the above settings** | ✅ Enforced | Applies to admins too |

### Status Checks (Critical)

The CI workflow must be named `ci` in `.github/workflows/ci.yml`:

```yaml
name: ci

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
```

The workflow must include these jobs that pass before merge:
- `backend-test` - Maven test + compile
- `frontend-test` - npm test + lint + build

### Example: Verify Status Check Name

After pushing a PR, check the "Checks" tab - the workflow name shown there must exactly match the status check name configured in branch protection (e.g., `ci`).

## Verification

After enabling:
1. Create a test PR
2. Verify CI runs automatically
3. Verify merge is blocked until CI passes
4. Verify merge is blocked without approval
5. Verify force push to `main` is rejected

## Current CI Workflow

The existing `.github/workflows/ci.yml` runs:
- Backend: `mvn test` (compiles, runs 70 tests)
- Frontend: `npm test` + `npm run lint` + `npm run build`

All must pass for merge to proceed.