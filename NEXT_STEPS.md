# MedOS HMS — Next Steps Roadmap

Tracked as GitHub issues & milestones in this repo.
Milestones: `Release Readiness - Phase 1 (P0)` → `Phase 4 (P3)`.

View: https://github.com/gaurav-amritkar/med-os/issues?q=is%3Aissue+is%3Aopen

---

## Milestone: Release Readiness - Phase 1 (P0) — blocks any prod deploy 🚨

| # | Issue | Area | Status |
|---|-------|------|--------|
| 1 | ~~PII encryption at rest (patient demographics, clinical notes)~~ | security | ✅ Done (commit `7c105b6`) |
| 2 | ~~Return 401 (not 403) for unauthenticated API requests~~ | security/api | ✅ Done (commit `7c105b6`) |
| 3 | ~~Branch protection: require CI status checks on main~~ | devops | ✅ Documented (commit `7c105b6`) |

> **Done in the last pass** (commit `ae92e5c`): JWT_SECRET required + validated,
> demo accounts removed from Flyway (V3), admin bootstrap, actuator → `/manage`,
> WebSocket token enforcement + origin restriction, login rate limiting,
> non-root containers, prod compose override, 48 backend + 7 frontend tests, CI workflow.
>
> **Done in this pass** (commit `7c105b6`): PII encryption (AES-256-GCM), 401 auth entry point, branch protection docs.

---

## Milestone: Release Readiness - Phase 2 (P1) — reliability & data integrity

| # | Issue | Area | Status |
|---|-------|------|--------|
| 4 | ~~Optimistic locking + row-level locking (stock/billing/admissions)~~ | reliability | ✅ Done (commit `32a4766`) |
| 5 | ~~Centralize money math (Money util, GST consistency)~~ | reliability | ✅ Done (commit `32a4766`) |
| 6 | ~~Idempotency keys (payments / dispense / invoices)~~ | reliability | ✅ Done (commit `33da4d8`) |
| 7 | ~~Pagination for all list endpoints~~ | reliability/api | ✅ Done (commit `8d74513`) |
| 8 | Graceful shutdown + readiness health group | devops | ✅ Done (commit `7c105b6`) |

---

## Milestone: Release Readiness - Phase 3 (P2) — observability, API, infra

| # | Issue | Area |
|---|-------|------|
| 9 | Prometheus metrics, structured logs, correlation IDs | devops/performance |
| 10 | Backup/restore runbook + Redis persistence policy | docs/devops |
| 11 | OpenAPI docs (springdoc) + API versioning plan | api/docs |
| 12 | ~~Replace JPA-entity responses with dedicated DTOs~~ | api | ✅ Done (commit `185414f`) |
| 13 | Standardize error contract + RBAC matrix test | api/testing |
| 14 | ~~Frontend hardening: token storage, error boundaries, states~~ | security/testing | ✅ Done (commit `e5fdc0e`) |
| 15 | CI hardening: Trivy image scan + Flyway deploy gate | devops |
| 16 | TLS termination + HSTS + docker secrets | devops |

---

## Milestone: Release Readiness - Phase 3 (P2) — observability, API, infra

| # | Issue | Area |
|---|-------|------|
| 9 | Prometheus metrics, structured logs, correlation IDs | devops/performance |
| 10 | Backup/restore runbook + Redis persistence policy | docs/devops |
| 11 | OpenAPI docs (springdoc) + API versioning plan | api/docs |
| 12 | ~~Replace JPA-entity responses with dedicated DTOs~~ | api | ✅ Done (commit `185414f`) |
| 13 | Standardize error contract + RBAC matrix test | api/testing |
| 14 | ~~Frontend hardening: token storage, error boundaries, states~~ | security/testing | ✅ Done (commit `e5fdc0e`) |
| 15 | CI hardening: Trivy image scan + Flyway deploy gate | devops |
| 16 | TLS termination + HSTS + docker secrets | devops |

### Testing Sub-Steps (P2)
| # | Issue | Area | Status |
|---|-------|------|--------|
| T1 | **End-to-End API Testing** — validate all REST endpoints with valid/invalid payloads, cover all 6 roles (admin, doctor, nurse, receptionist, pharmacist, billing) | api/testing | 🟡 In Progress |
| T2 | **Negative Scenario Testing** — test malformed requests, missing auth, unauthorized access, boundary values, duplicate submissions; verify 400/401/403 responses | api/testing | 🟡 In Progress |
| T3 | **Security Penetration Testing** — OWASP Top 10 checks: injection, broken auth, sensitive data exposure, XML external entity, insecure session management; use OWASP ZAP or Burp Suite | security/testing | ⬜ Not Started |
| T4 | **Docker Integration Testing** — full stack test with `docker compose up --build`; verify service health checks, env var propagation, network isolation between medos-private and medos-edge networks | devops/testing | ⬜ Not Started |
| T5 | **Environment Variable Validation** — pre-flight check script to verify all required env vars (JWT_SECRET, DB_PASSWORD, CORS_ORIGINS, REDIS_PASSWORD) are set and non-empty before startup; fail fast if missing | devops/testing | ⬜ Not Started |
| T6 | **PII Encryption End-to-End Test** — encrypt patient demographics, verify AES-256-GCM decryption, test re-encryption on key rotation, ensure no plaintext leakage in logs or responses | security/testing | ⬜ Not Started |
| T7 | **Redis Persistence & Failover Test** — validate RDB/AOF persistence, test Redis restart without data loss, verify cache invalidation on token revocation | devops/testing | ⬜ Not Started |
| T8 | **Flyway Migration Validation** — `mvn flyway:validate` on fresh DB, test migration rollforward, verify baseline-on-migrate behavior, checksum validation after schema changes | database/testing | ⬜ Not Started |
| T9 | **Role-Based Access Control Test** — verify each of the 6 roles can access only their authorized endpoints; test privilege escalation attempts; validate 403 for cross-role access | api/testing | ⬜ Not Started |
| T10 | **Disaster Recovery Drill** — `docker compose down --volumes` + `docker compose up --build`, restore from pg_dump, verify data integrity, test Redis cache warm-up, confirm audit log completeness | devops/testing | ⬜ Not Started |

---

## Suggested working order

1. **P0 items first** — #1 (PII), #2 (401 contract), #3 (branch protection). PII encryption is the biggest; scope it with a design pass.
2. **Parallel-track P1** — #4/#5/#6 are backend-heavy; #8 is a 20-minute config change (quick win, do early).
3. **P2** — #10 and #16 are mostly docs/config; #9/#11/#13 unlock external clients.
4. **P3** — ongoing; needs legal/ops input for retention & consent policies.

## Quick wins (single sitting)

- ~~#8 graceful shutdown (`server.shutdown=graceful`, 30s timeout, readiness group)~~ ✅ Done
- ~~#13 error contract + RBAC matrix test (pure tests, no schema change)~~ ✅ Done
- ~~#10 backup runbook (docs + a cron sidecar yml)~~ ✅ Done (docs/operations.md)
