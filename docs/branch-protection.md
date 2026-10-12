# Branch Protection Setup for `main`

**Status: enforced, not aspirational.** The rules below were applied to `main`
on 30 Sep 2026 via the GitHub API. This document is kept as the record of what
was applied and how to verify it.

Until 30 Sep 2026 this file described a manual setup step that had never been
carried out — the API reported `main` as unprotected, so the rule existed only
on paper.

## Applied configuration

| Setting | Value |
|---|---|
| Required status checks | `backend`, `frontend`, `containers`, **`security`** |
| Strict (checks must be current) | yes |
| Enforce for administrators | yes |
| Required approving reviews | 1, stale reviews dismissed |
| Required conversation resolution | yes |
| Linear history | yes |
| Force pushes | blocked |
| Deletion | blocked |

> ⚠️ **Corrected 12 Oct 2026 — verify this against the live repo before trusting
> it.** Two problems with the original version of this table:
>
> 1. **CI now has four jobs, not three.** The `security` job (Trivy image scan) was
>    added and is **not** listed as required. A failing image scan therefore does
>    **not** block merge. The job names in `ci.yml` are `backend`, `frontend`,
>    `containers`, `security` — the required checks are keyed on **job names**,
>    not on the workflow name (`CI`), which is how this document originally
>    described them.
> 2. **`CODEOWNERS` does not exist.** The "Require review from Code Owners"
>    setting below has no effect without a `.github/CODEOWNERS` file, and there is
>    none in this repository.
>
> Check the real state with `gh api` (below) rather than trusting this table.

## Verify

```
gh api repos/:owner/:repo/branches/main/protection
```

## Re-applying after a settings change

If the rules are ever lost, the equivalent settings UI is:

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
| **Require review from Code Owners** | ⚠️ No effect | **No `.github/CODEOWNERS` exists** — add one or turn the setting off |
| **Require status checks to pass before merging** | ✅ Enforced | **Critical for P0** |
| **Status checks required** | `backend`, `frontend`, `containers`, `security` | Must match the **job names** in `.github/workflows/ci.yml`, not the workflow name |
| **Require branches to be up to date before merging** | ✅ Enforced | Prevents merge conflicts |
| **Require linear history** | ✅ Enforced | No merge commits |
| **Require conversation resolution before merging** | ✅ Enforced | All comments resolved |
| **Require signed commits** | ⚠️ Optional | If using GPG signing |
| **Require deployments to succeed before merging** | ❌ Disabled | Not applicable |
| **Lock branch** | ✅ Enforced | Prevents force pushes |
| **Do not allow bypassing the above settings** | ✅ Enforced | Applies to admins too |

### Status Checks (Critical)

The required checks are the **job names** in `.github/workflows/ci.yml`:

```yaml
name: CI          # workflow name — this is NOT what you select as a check

jobs:
  backend:    ...
  frontend:   ...
  containers: ...
  security:   ...   # Trivy image scan
```

GitHub matches required status checks against the **job name** (and optionally a
`matrix` suffix). Selecting the workflow name `CI` is what older revisions of this
document advised, and it does not work the way it was described — `ci.yml` names
its workflow `CI` and its jobs as listed above.

### Example: Verify Status Check Name

After pushing a PR, check the "Checks" tab — the job names shown there must
exactly match the status check names configured in branch protection
(`backend`, `frontend`, `containers`, `security`).

## Verification

After enabling:
1. Create a test PR
2. Verify CI runs automatically
3. Verify merge is blocked until CI passes
4. Verify merge is blocked without approval
5. Verify force push to `main` is rejected

## Current CI Workflow

`.github/workflows/ci.yml` runs four jobs:

| Job | Does |
|---|---|
| `backend` | `mvn test`, then `mvn -DskipTests package` (JDK 21 builder) |
| `frontend` | `npm ci` → `npm test` → `npm run lint` → `npm run build` (Node 22) |
| `containers` | compose validation, image builds, live Flyway `validate` against Postgres 16, `tools/verify-migrations.sh` + its self-test, and invariant assertions over the resolved prod compose model |
| `security` | Trivy scan of the backend, frontend and Caddy images (HIGH/CRITICAL, ratcheted against `.trivyignore`), SARIF uploaded |

⚠️ All four should pass for merge to proceed, but only the three the branch
protection rule actually lists are enforced. Verify with `gh api` whether
`security` is required; if it is not, add it.