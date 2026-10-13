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
| Required status checks | `Backend (build + tests)`, `Frontend (lint + tests + build)`, `Container definitions`, `Image vulnerability scan` |
| Strict (checks must be current) | yes |
| Enforce for administrators | yes |
| Required approving reviews | **0 — none required**, stale reviews dismissed |
| Required conversation resolution | yes |
| Linear history | yes |
| Force pushes | blocked |
| Deletion | blocked |

> ✅ **Verified against the live repo on 12 Oct 2026** via
> `gh api repos/:owner/:repo/branches/main/protection`. The four contexts above
> are the `name:` values of the four `ci.yml` jobs, and all four are enforced
> including for administrators — a failing push is rejected outright with
> *"4 of 4 required status checks are expected"*.
>
> One correction to the original version of this table, which listed only three:
> **CI grew a fourth job** (`security`, the Trivy image scan) and it *is*
> required. An earlier correction here wrongly said it was not — the API
> confirms otherwise.
>
> Two real problems remain, both verified against the live rule on 13 Oct 2026:
>
> 1. **No review is required.** `required_approving_review_count` is 0, so a PR
>    merges once the four status checks pass and conversations are resolved. The
>    only gate on landing code on `main` is CI, not a human — which is a
>    deliberate single-maintainer setup, but it should be a decision rather than
>    an accident documented as the opposite.
> 2. **There is no `.github/CODEOWNERS` file**, so the "Require review from Code
>    Owners" setting has no effect.

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
| **Require approvals** | ⚠️ **0** | **No human review is required to merge.** Corrected 13 Oct 2026: this document claimed `1`; `gh api` reports `required_approving_review_count: 0`. An earlier revision of this file asserted branch protection was "enforced, not aspirational", which was true of the checks and not of the review count. |
| **Dismiss stale PR approvals when new commits are pushed** | ✅ Enforced | Prevents stale approvals |
| **Require review from Code Owners** | ⚠️ No effect | **No `.github/CODEOWNERS` exists** — add one or turn the setting off |
| **Require status checks to pass before merging** | ✅ Enforced | **Critical for P0** |
| **Status checks required** | `Backend (build + tests)`, `Frontend (lint + tests + build)`, `Container definitions`, `Image vulnerability scan` | Must match each job's `name:` in `ci.yml`, not the workflow name |
| **Require branches to be up to date before merging** | ✅ Enforced | Prevents merge conflicts |
| **Require linear history** | ✅ Enforced | No merge commits |
| **Require conversation resolution before merging** | ✅ Enforced | All comments resolved |
| **Require signed commits** | ⚠️ Optional | If using GPG signing |
| **Require deployments to succeed before merging** | ❌ Disabled | Not applicable |
| **Lock branch** | ✅ Enforced | Prevents force pushes |
| **Do not allow bypassing the above settings** | ✅ Enforced | Applies to admins too |

### Status Checks (Critical)

A required status check is matched against a job's **`name:`** (its display name),
falling back to the job ID only when `name:` is absent. The workflow's own
`name: CI` is never what you select:

```yaml
name: CI          # workflow name — NOT a status check

jobs:
  backend:
    name: Backend (build + tests)          # <- this is the required context
  frontend:
    name: Frontend (lint + tests + build)  # <- this one too
  containers:
    name: Container definitions
  security:
    name: Image vulnerability scan        # Trivy
```

Original revisions of this document told you to name the workflow `ci` and
select `ci`. That is wrong twice over: the workflow is already named `CI`, and
the contexts that matter are the four `name:` values above. Rename a job's
`name:` and the required check silently stops matching — which is the failure
mode worth knowing about here.

### Example: Verify Status Check Name

After pushing a PR, check the "Checks" tab — the labels shown there must exactly
match the four contexts configured in branch protection.

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

✅ All four are required, so a red Trivy scan **does** block merge. Verified
against the live branch-protection rule on 12 Oct 2026.