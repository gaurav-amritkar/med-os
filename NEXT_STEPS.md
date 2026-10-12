# MedOS HMS — Next Steps Roadmap

Tracked as GitHub issues & milestones in this repo.
Milestones: `Release Readiness - Phase 1 (P0)` → `Phase 4 (P3)`.

View: https://github.com/gaurav-amritkar/med-os/issues?q=is%3Aissue+is%3Aopen

> **This file is a summary, not a source of truth.** The code is the source of
> truth. Several entries below were stale for months and have been corrected
> against `main`; if you find another mismatch, fix it rather than trusting this
> table.
>
> **Product feature requests live in a separate document:**
> [`docs/feature-releases.md`](docs/feature-releases.md) — the requested feature
> releases (landing page, tenant name in the header, staff accounts, printable
> receipts, configurable GST, wards/rooms, super-admin tenant management, payment
> gateway), each verified against the current code.
>
> ⚠️ **P0 found and fixed 30 Sep 2026** (#73): a **public** repository carried
> live credentials for 36 commits — the JWT signing secret, the PII encryption
> key, the database, Redis and bootstrap-admin passwords, plus a second set in a
> tracked `.env.supabase`. `application.yml` also shipped a real PII key as an
> inline default, so a deployment that forgot the variable would have encrypted
> patient records with a published key. All values rotated, files untracked,
> history purged, and the app now fails to start rather than fall back to a
> known key.
>
> The three security findings from that audit are now fixed on `main`: tenant
> provisioning is gated by a platform secret (blank ⇒ deny, constant-time
> compare), the staff listing is tenant-scoped, and a deactivated tenant is
> refused at login **and on every request** (not just at login).
>
> A gap analysis against the original v3.0 requirements is in
> [`docs/requirements-traceability.md`](docs/requirements-traceability.md). Note
> that `requirements/BRD` and `requirements/PRD` describe a **Node/Express/SQLite
> single-tenant** product and are stale as architecture documents; the largest
> unmet requirement is the Claude AI integration they specify, which the code does
> not implement.

---

## Milestone: Release Readiness - Phase 1 (P0) — blocks any prod deploy 🚨

| # | Issue | Area | Status |
|---|-------|------|--------|
| 1 | ~~PII encryption at rest (patient demographics, clinical notes)~~ | security | ✅ Done (commit `7c105b6`) |
| 2 | ~~Return 401 (not 403) for unauthenticated API requests~~ | security/api | ✅ Done (commit `7c105b6`) |
| 3 | ~~Branch protection: require CI status checks on main~~ | devops | ✅ Enforced |
| — | `BOOTSTRAP_ADMIN_PASSWORD` not wired into `docker-compose.prod.yml` | devops | 🔴 **Open — blocks first prod boot** |
| — | `/api/v1/users/me` returns the password hash (#106) | security | 🔴 **Open, P0** |

> **Done (commit `ae92e5c`):** JWT_SECRET required + validated, demo accounts
> removed from Flyway (V3), admin bootstrap, actuator → `/manage`, WebSocket token
> enforcement + origin restriction, login rate limiting, non-root containers, prod
> compose override, 48 backend + 7 frontend tests, CI workflow.
>
> ✅ **Correction to the note that used to sit here.** This file previously said
> the prod compose override, Caddy TLS config and docker secrets were "deleted in
> `177de04`" and therefore "still outstanding, tracked in #16". **That is no longer
> true.** The following are all implemented and on `main`:
>
> - `docker-compose.prod.yml` — 5 services (`migrate`, `redis`, `backend`,
>   `frontend`, `caddy`), managed PostgreSQL over the pooler with no in-host `db`,
>   one-shot `migrate` job gating backend startup, bounded log rotation, and an
>   `internal: true` network that only Caddy bridges to the edge.
> - `caddy/Caddyfile` — ACME certificates, HTTP→HTTPS redirect, HSTS
>   (`max-age=31536000; includeSubDomains`), catch-all `:80 { abort }`. There is
>   deliberately no `tls internal` fallback.
> - Docker secrets — 5 file-backed secrets under `/run/secrets`, gitignored and
>   untracked, read by `backend/entrypoint.sh` (env var preferred, file as
>   fallback). No `*_PASSWORD_FILE` indirection, which Spring Boot would ignore.
>
> Issue **#16 (TLS termination + HSTS + docker secrets) is therefore done** and
> should be closed.
>
> ⚠️ **Correction:** branch protection was *documented* in `7c105b6` but never
> applied — `docs/branch-protection.md` described it as a manual
> "Settings → Branches" step, and the GitHub API confirmed `main` was
> unprotected. It is now actually enforced: the four CI status checks are
> required, admins included, with 1 approving review, no force pushes, no
> deletions and linear history. Consequence: **changes reach `main` via pull
> request, not direct push.**

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

| # | Issue | Area | Status |
|---|-------|------|--------|
| 9 | Prometheus metrics, structured logs, correlation IDs | devops/performance | ⬜ **Not Started — genuinely the largest remaining gap** |
| 10 | Backup/restore runbook + Redis persistence policy | docs/devops | ✅ Done (docs/operations.md) |
| 11 | OpenAPI docs (springdoc) + API versioning plan | api/docs | 🟡 **Partial** — springdoc is wired and ADMIN-gated (`/v3/api-docs`, `/swagger-ui.html`); no versioning plan |
| 12 | ~~Replace JPA-entity responses with dedicated DTOs~~ | api | ✅ Done (commit `185414f`) |
| 13 | Standardize error contract + RBAC matrix test | api/testing | ✅ Done (commit `67bd469`) |
| 14 | ~~Frontend hardening: token storage, error boundaries, states~~ | security/testing | ✅ Done (commit `e5fdc0e`) |
| 15 | CI hardening: Trivy image scan + Flyway deploy gate | devops | ✅ **Done** — see note below |
| 16 | TLS termination + HSTS + docker secrets | devops | ✅ **Done** — see Phase 1 note above |

> **Correction:** #15 and #16 were both marked "Not Started". Both are implemented
> on `main`:
>
> - **Trivy** runs as a digest-pinned container (not the action, which is broken)
>   and scans the backend, frontend and Caddy images at HIGH/CRITICAL, ratcheted
>   against `.trivyignore`. New findings fail the build; SARIF is uploaded for all
>   three.
> - The **Flyway gate** has three layers: `migrate validate` against a real
>   Postgres 16 service, `tools/verify-migrations.sh` (SHA256 manifest
>   immutability + single-copy assertion), and `tools/test-verify-migrations.sh`,
>   which self-tests the gate.
>
> ⚠️ What CI still does **not** do: application dependency scanning (npm audit is
> explicitly disabled), SAST/CodeQL (the `security-events: write` plumbing exists
> but nothing populates it), and secret scanning — the same class of problem
> ADR-0004 flags as still open.

### Testing Sub-Steps (P2)
| # | Issue | Area | Status |
|---|-------|------|--------|
| T1 | **End-to-End API Testing** — validate all REST endpoints with valid/invalid payloads, cover all 7 roles | api/testing | 🟡 In Progress (Playwright `probe:flows` + `smoke`/`full` specs exist) |
| T2 | **Negative Scenario Testing** — malformed requests, missing auth, unauthorized access, boundary values, duplicate submissions | api/testing | 🟡 In Progress |
| T3 | **Security Penetration Testing** — OWASP Top 10; OWASP ZAP or Burp Suite | security/testing | ⬜ Not Started |
| T4 | **Docker Integration Testing** — `docker compose up --build`; health checks, env propagation, network isolation | devops/testing | 🟡 Partial — CI validates the compose model and asserts invariants, but does not boot the full prod stack |
| T5 | **Environment Variable Validation** — pre-flight check that required env vars are set and non-empty | devops/testing | 🟡 Partial — fail-closed at startup for JWT_SECRET/PII_ENCRYPTION_KEY; no standalone pre-flight script |
| T6 | **PII Encryption End-to-End Test** — AES-256-GCM round trip, re-encryption on rotation, no plaintext leakage | security/testing | 🟡 Partial — key isolation, blind-index independence, versioned/legacy ciphertext and migration tests exist; no full encrypt→rotate→decrypt journey |
| T7 | **Redis Persistence & Failover Test** — RDB/AOF, restart without loss, cache invalidation on token revocation | devops/testing | ⬜ Not Started |
| T8 | **Flyway Migration Validation** — validate on fresh DB, rollforward, baseline, checksums | database/testing | ✅ Done — three-layer gate in CI |
| T9 | **Role-Based Access Control Test** — 7 roles, privilege escalation, 403 on cross-role access | api/testing | ✅ Done (`RbacMatrixTest`) |
| T10 | **Disaster Recovery Drill** — volume wipe, rebuild, restore from pg_dump, audit completeness | devops/testing | ⬜ Not Started |

---

## Key & encryption lifecycle (post-ADR-0009)

Implemented on `main`, superseding the old "the PII key cannot be rotated" position:

| Capability | Status |
|---|---|
| KEK wraps a per-tenant DEK (`tenant_keys.wrapped_dek`) | ✅ Done (#86, #112) |
| Blind-index key wrapped and stored separately from the DEK | ✅ Done (#87, #113) |
| Ciphertext versioned as `kv1.d<generation>:<base64>` | ✅ Done (#85, #111) |
| One tenant's key cannot decrypt another's records | ✅ Done (#86) + `TenantKeyIsolationTest` |
| KEK re-wrap: dry-run / apply / verify, resumable, fail-closed | ✅ Done (#88, #130) |
| Operator runbook for rotation | ✅ Done (#89, #131, `docs/operations.md` §7d) |
| **Per-tenant DEK rotation (`dek_rotation`)** | 🔴 **Not implemented** — schema reserves it, no code path calls `advanceDekGeneration` |
| **Ciphertext above generation 1** | 🔴 **Not reachable** — `TenantKeyHolder.currentGeneration()` hardcodes `1` |
| Blind-index key rotation | 🔴 Not implemented — `rotateIndependently` has no operator surface |

---

## Suggested working order

1. **Fix the two P0 blockers first** — wire `BOOTSTRAP_ADMIN_PASSWORD` into
   `docker-compose.prod.yml`, and stop `/api/v1/users/me` leaking the password hash
   (#106). Neither is large; both block a real deploy.
2. **Then #9 (observability)** — it is the single largest remaining gap and the
   only Phase 3 item genuinely at zero. Metrics, JSON logs and a correlation ID
   are what make the rest operable.
3. **Then the container hardening sweep** — resource limits, `read_only`,
   `cap_drop`, `no-new-privileges`, and dropping root from the redis/caddy/migrate
   images. Mechanical, and it removes a whole class of finding.
4. **Close the tenant-scoping gap** — `TenantStatementInspector` covers `SELECT`
   root-`FROM` only; writes depend on services remembering to set `tenantId`.
5. **Finish #11** with an API versioning plan; the springdoc part is done.
6. **P3** — ongoing; needs legal/ops input for retention & consent policies.

## Quick wins (single sitting)

- ~~#8 graceful shutdown (`server.shutdown=graceful`, 30s timeout, readiness group)~~ ✅ Done
- ~~#13 error contract + RBAC matrix test (pure tests, no schema change)~~ ✅ Done
- ~~#10 backup runbook (docs + a cron sidecar yml)~~ ✅ Done (docs/operations.md)
- ~~#15 Trivy + Flyway gate~~ ✅ Done
- ~~#16 TLS + HSTS + docker secrets~~ ✅ Done
