# MedOS Production Readiness — GitHub Issue Templates (gpt5.3codex)

Use each section below directly with `gh issue create` or copy into GitHub UI.

---

## Issue 1 — CI: Add backend/frontend build, lint, and test workflows

**Suggested labels:** `priority:P0`, `type:devops`, `type:testing`

### Title
`CI: Add backend/frontend build, lint, and test workflows`

### Body
```md
## Goal
Create GitHub Actions workflows to enforce quality gates on pull requests and main branch.

## Scope
- Add `.github/workflows/ci.yml` (or split by backend/frontend).
- Backend checks:
  - `mvn test`
  - `mvn -DskipTests package`
- Frontend checks:
  - `npm ci`
  - `npm run lint`
  - `npm run build`
- Cache Maven and npm dependencies.
- Ensure workflow runs on `pull_request` and `push` to `main`.
- Configure branch protection to require CI checks before merge.

## Acceptance Criteria
- PRs automatically run backend and frontend checks.
- CI status appears in PR and blocks merge on failure.
- Pipeline runtime is reasonable and documented.

## Notes
Start simple first; split into multiple workflows only if needed for speed/maintainability.
```

---

## Issue 2 — Backend: Add baseline unit and integration tests for critical flows

**Suggested labels:** `priority:P0`, `type:testing`, `backend`

### Title
`Backend: Add baseline unit and integration tests for critical flows`

### Body
```md
## Goal
Establish automated backend test coverage for business-critical paths.

## Scope
- Create initial tests under `backend/src/test/java/com/medos`.
- Cover:
  - Auth login success/failure paths.
  - RBAC-protected endpoint behavior.
  - Patients CRUD/search core behavior.
  - Encounters/pharmacy/billing critical business cases.
- Add integration tests for DB-backed logic (use Testcontainers for PostgreSQL/Redis where needed).
- Ensure tests are deterministic and CI-friendly.

## Acceptance Criteria
- Meaningful test suite exists and runs with `mvn test`.
- Tests include both happy path and failure path cases.
- CI executes test suite and fails on regression.

## Notes
Prioritize high-risk modules first; coverage can grow incrementally after baseline.
```

---

## Issue 3 — Frontend: Add baseline tests + one end-to-end smoke flow

**Suggested labels:** `priority:P0`, `type:testing`, `frontend`

### Title
`Frontend: Add baseline tests + one end-to-end smoke flow`

### Body
```md
## Goal
Create frontend test safety net for key user journeys.

## Scope
- Add frontend testing setup (unit/component/integration).
- Add tests for:
  - Auth and protected route behavior.
  - Core pages load/render behavior (Dashboard, Patients, Encounters, Billing, Pharmacy).
- Add one E2E smoke test flow:
  - Login -> navigate -> perform one core action -> verify expected UI response.
- Integrate tests in CI (PR gate or scheduled/nightly for heavier E2E).

## Acceptance Criteria
- Frontend tests run locally with documented command(s).
- CI runs baseline frontend tests reliably.
- At least one E2E smoke scenario is automated.

## Notes
Keep test setup minimal and maintainable; avoid flaky selectors.
```

---

## Issue 4 — Security: Remove insecure defaults and enforce required production env vars

**Suggested labels:** `priority:P0`, `type:security`, `type:devops`

### Title
`Security: Remove insecure defaults and enforce required production env vars`

### Body
```md
## Goal
Eliminate insecure configuration defaults and enforce secure runtime configuration.

## Scope
- Remove production fallback secrets/defaults (e.g., JWT secret fallback).
- Enforce required env vars at startup in production profile.
- Add `.env.example` with safe placeholders only.
- Document required env vars and example values in root README or dedicated config doc.
- Verify Docker Compose and deployment docs align with secure config strategy.

## Acceptance Criteria
- Production profile does not run with weak/default secrets.
- App fails fast with clear message when required secrets are missing.
- `.env.example` and docs are present and accurate.

## Notes
Do not commit real secrets. Validate local/dev UX remains smooth.
```

---

## Issue 5 — Security hardening: Actuator access, JWT error handling, CORS review

**Suggested labels:** `priority:P0`, `type:security`, `backend`

### Title
`Security hardening: Actuator access, JWT error handling, CORS review`

### Body
```md
## Goal
Harden backend security posture before production release.

## Scope
- Restrict `/actuator/**` exposure to least privilege.
- Improve JWT authentication filter behavior:
  - No silent swallow of errors.
  - Safe, actionable logging for auth failures.
- Review and tighten CORS allowed origins policy.
- Define baseline plan for anti-abuse controls (e.g., auth rate limiting/brute-force mitigation).

## Acceptance Criteria
- Security-sensitive endpoints are not publicly exposed by default.
- Auth token failures are observable without leaking internals.
- CORS policy is explicit and environment-safe.

## Notes
Include tests for security config paths where possible.
```

---

## Issue 6 — API: Add OpenAPI/Swagger documentation and contract checks

**Suggested labels:** `priority:P1`, `type:api`, `type:docs`, `backend`

### Title
`API: Add OpenAPI/Swagger documentation and contract checks`

### Body
```md
## Goal
Standardize API contract visibility and reduce frontend/backend integration drift.

## Scope
- Add OpenAPI generation for backend APIs.
- Expose API docs endpoint (dev/stage as appropriate).
- Generate/version spec artifact in repository or build pipeline.
- Add contract sanity checks in CI where feasible.

## Acceptance Criteria
- OpenAPI spec exists and is kept up to date.
- API docs are discoverable from project documentation.
- CI catches major contract breakage patterns.

## Notes
Keep secured/internal endpoints documented appropriately.
```

---

## Issue 7 — Observability: Structured logs, request IDs, and metrics baseline

**Suggested labels:** `priority:P1`, `type:devops`, `type:backend`

### Title
`Observability: Structured logs, request IDs, and metrics baseline`

### Body
```md
## Goal
Improve operability with actionable logging and health signals.

## Scope
- Add structured logging format for backend.
- Add request correlation IDs in logs.
- Expand health/metrics visibility for app, DB, Redis.
- Define basic alert recommendations for critical symptoms.

## Acceptance Criteria
- Logs are searchable and traceable per request.
- Health endpoints expose release-relevant dependencies.
- Minimal observability runbook includes what to monitor.

## Notes
Prefer low-complexity, production-friendly defaults.
```

---

## Issue 8 — Operations: Release checklist, rollback, and backup/restore runbook

**Suggested labels:** `priority:P1`, `type:docs`, `type:devops`

### Title
`Operations: Release checklist, rollback, and backup/restore runbook`

### Body
```md
## Goal
Create a repeatable and safe release/operations process.

## Scope
- Add release checklist (pre-deploy, deploy, post-deploy validation).
- Define rollback procedure (app + migration-aware strategy).
- Document backup and restore process for PostgreSQL.
- Add incident response basics and ownership matrix.

## Acceptance Criteria
- Checklist is actionable and used for staging/prod.
- Rollback and restore steps are documented and validated.
- Team can execute release process without tribal knowledge.

## Notes
Keep docs concise and practical.
```

---

## Issue 9 — Performance: Baseline load tests and SLO-aligned targets

**Suggested labels:** `priority:P2`, `type:performance`, `backend`, `frontend`

### Title
`Performance: Baseline load tests and SLO-aligned targets`

### Body
```md
## Goal
Measure and improve system behavior under realistic load.

## Scope
- Define key scenarios:
  - Login/auth
  - Patient search/list
  - Encounter creation/sign
  - Pharmacy dispense
  - Invoice/payment paths
- Run baseline load tests and capture latency/error profiles.
- Identify bottlenecks and track remediation tasks.

## Acceptance Criteria
- Load test plan and baseline results are documented.
- Initial SLO targets proposed for critical endpoints.
- Follow-up optimization issues created for bottlenecks.

## Notes
Start with representative traffic and realistic DB state.
```

---

## Issue 10 — Compliance: Audit, consent, and PII/data retention hardening

**Suggested labels:** `priority:P2`, `type:compliance`, `type:security`, `backend`

### Title
`Compliance: Audit, consent, and PII/data retention hardening`

### Body
```md
## Goal
Strengthen governance and compliance readiness for healthcare data workflows.

## Scope
- Review audit log completeness across mutation endpoints.
- Verify consent capture/use flows and edge cases.
- Define PII handling and data retention policies.
- Create role/access review checklist for least privilege.

## Acceptance Criteria
- Compliance control matrix documented (what is covered vs pending).
- Gaps tracked as actionable follow-up issues.
- Data governance decisions are explicit and reviewable.

## Notes
Coordinate with legal/compliance stakeholders for policy alignment.
```

---

## Optional: suggested milestones
- `Release Readiness - Phase 1 (P0)`
- `Release Readiness - Phase 2 (P1)`
- `Release Readiness - Phase 3 (P2)`
