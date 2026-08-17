# MedOS Production Readiness Plan (gpt5.3codex)

## Project Health Summary

### Current strengths
- Backend builds successfully (`mvn test`, `mvn -DskipTests package`).
- Frontend production build succeeds (`npm run build`).
- Dockerized stack is available (`backend`, `frontend`, `postgres`, `redis`) with health checks.

### Key production gaps
1. **Automated testing is effectively missing**
   - `backend/src/test/java/com/medos` is empty.
   - No frontend test setup/files found.
2. **No CI workflows**
   - No `.github/workflows/*` present.
3. **Security hardening gaps**
   - Default JWT secret fallback in config.
   - Sensitive default credentials in prod-oriented config.
   - `/actuator/**` publicly permitted.
   - JWT filter swallows exceptions silently.
4. **Code quality issue**
   - Frontend lint warning: unused import in `frontend/src/pages/Encounters.jsx`.
5. **Documentation/release readiness gaps**
   - `frontend/README.md` is still template-level.
   - Missing release checklist/runbook/versioning process docs.
6. **API contract gaps**
   - No OpenAPI/Swagger artifacts discovered.

---

## Prioritized Task Plan

## P0 (must-have before production release)

### 1) Set up CI pipeline for backend + frontend
**Goal:** Enforce quality gates on every PR and main branch.

**Scope:**
- Backend: `mvn test`, package.
- Frontend: `npm ci`, `npm run lint`, `npm run build`.
- Dependency caching and clear status checks.

**Acceptance Criteria:**
- PRs auto-run all checks.
- Main branch protected by required status checks.
- Failed checks block merge.

---

### 2) Implement backend test baseline (unit + integration)
**Goal:** Validate critical workflows and reduce regression risk.

**Scope:**
- Auth and RBAC authorization paths.
- Core flows: patients, encounters, pharmacy, billing.
- Integration tests with Postgres/Redis (Testcontainers where relevant).

**Acceptance Criteria:**
- Minimum baseline coverage on core services/controllers.
- Integration tests cover successful and failure paths.
- Tests run in CI and are stable.

---

### 3) Implement frontend test baseline
**Goal:** Ensure UI reliability on critical user journeys.

**Scope:**
- Auth/route guard behavior.
- Critical pages smoke tests (Dashboard, Patients, Encounters, Billing, Pharmacy).
- Add one E2E happy-path flow (login → patient lookup → encounter/billing action).

**Acceptance Criteria:**
- Component/integration tests runnable locally and in CI.
- At least one E2E smoke test gate in CI (or nightly if runtime is high).

---

### 4) Secrets and configuration hardening
**Goal:** Eliminate insecure defaults and enforce env-driven config.

**Scope:**
- Remove hardcoded fallback secrets in production profile.
- Require critical env vars (`JWT_SECRET`, DB creds, etc.) at startup.
- Add `.env.example` with safe placeholders.
- Document configuration matrix (dev/stage/prod).

**Acceptance Criteria:**
- App fails fast when required secrets are missing in prod.
- No real secret defaults in committed configs.
- Setup docs allow reproducible environment provisioning.

---

### 5) Security hardening pass
**Goal:** Tighten runtime security posture.

**Scope:**
- Restrict/secure actuator endpoints.
- Improve JWT filter error handling and security logging.
- Re-validate CORS policy for least privilege.
- Add baseline protections plan (rate limiting, brute-force protection, audit enhancement).

**Acceptance Criteria:**
- Security-sensitive endpoints are access-controlled.
- Token/auth failures are observable without leaking internals.
- Security posture documented and testable.

---

## P1 (high-value next)

### 6) Add OpenAPI documentation and API contract checks
**Goal:** Stabilize backend-client contract and improve integration quality.

**Scope:**
- Generate OpenAPI spec from controllers.
- Publish docs endpoint or artifact.
- Add contract drift checks in CI where feasible.

**Acceptance Criteria:**
- OpenAPI spec available and up to date.
- API docs referenced in root README.

---

### 7) Add observability baseline
**Goal:** Improve operability and incident response.

**Scope:**
- Structured logging + request correlation IDs.
- Expand metrics/health visibility for DB/Redis/app.
- Define alert triggers for key failure symptoms.

**Acceptance Criteria:**
- Logs are query-friendly and traceable by request.
- Health/metrics expose release-critical signals.

---

### 8) Create release process + operational runbook
**Goal:** Make deployments predictable and recoverable.

**Scope:**
- Versioning and release checklist.
- DB migration rollout and rollback process.
- Backup/restore SOP and incident playbook basics.

**Acceptance Criteria:**
- Release checklist is actionable and used for staging/prod.
- Rollback and backup/restore procedures are documented and tested.

---

## P2 (important for scale/compliance)

### 9) Performance and load testing
**Goal:** Validate capacity and identify bottlenecks.

**Scope:**
- Load test critical workflows (auth, patient search, encounters, dispense, invoice).
- Define latency/error SLO targets.

**Acceptance Criteria:**
- Baseline load profile and results documented.
- Bottlenecks prioritized with remediation actions.

---

### 10) Data governance and compliance hardening
**Goal:** Improve regulatory readiness for healthcare data handling.

**Scope:**
- Audit trail completeness review.
- Consent/PII handling and retention policy checks.
- Access review process and least-privilege matrix.

**Acceptance Criteria:**
- Data handling controls mapped to policy requirements.
- Compliance gaps tracked with owners and timelines.

---

## Suggested Labels for GitHub Tracking
- `priority:P0`, `priority:P1`, `priority:P2`
- `type:security`, `type:testing`, `type:devops`, `type:docs`, `type:api`, `type:performance`, `type:compliance`

## Suggested Milestones
1. `Release Readiness - Phase 1 (P0)`
2. `Release Readiness - Phase 2 (P1)`
3. `Release Readiness - Phase 3 (P2)`
