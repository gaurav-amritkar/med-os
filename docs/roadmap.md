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
> [`docs/feature-releases.md`](feature-releases.md) — the requested feature
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
> [`docs/requirements-traceability.md`](requirements-traceability.md). Note
> that the superseded v3.0 sources (`docs/requirements/BRD.pdf` and
> `docs/requirements/PRD.pdf`, retained for provenance) describe a
> **Node/Express/SQLite single-tenant** product; the current, code-verified
> requirements are `docs/requirements/*_v4.md`. The largest unmet v4
> requirement remains the Claude AI integration, which the code does not
> implement.

---

## Milestone: Release Readiness - Phase 1 (P0) — blocks any prod deploy 🚨

| # | Issue | Area | Status |
|---|-------|------|--------|
| 1 | ~~PII encryption at rest (patient demographics, clinical notes)~~ | security | ✅ Done (commit `7c105b6`) |
| 2 | ~~Return 401 (not 403) for unauthenticated API requests~~ | security/api | ✅ Done (commit `7c105b6`) |
| 3 | ~~Branch protection: require CI status checks on main~~ | devops | ✅ Enforced |
| — | ~~`BOOTSTRAP_ADMIN_PASSWORD` not wired into `docker-compose.prod.yml`~~ | devops | ✅ Done (#137) — secret declared and mounted, plus `tools/verify-prod-secrets.sh` in CI |
| — | ~~`/api/v1/users/me` returns the password hash (#106)~~ | security | ✅ Done (#108, commit `fd19cce`) — `CurrentUserDTO` replaced the entity; Playwright asserts no hash, persistence-only fields, and a resolved acting tenant. **Verified green 2026-10-22.** |

> **All P0 release blockers are closed as of 2026-10-22.** Phase 1 has no open
> items.

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
> required, admins included, no force pushes, no deletions and linear history.
> Consequence: **changes reach `main` via pull request, not direct push.**
>
> ⚠️ **Correction (13 Oct 2026):** this passage claimed "1 approving review".
> `gh api` reports `required_approving_review_count: **0**` — no human review is
> required. CI is the only gate on landing code on `main`. That is a coherent
> single-maintainer setup, but it is not what this document said, and anyone
> reading it would have believed a second pair of eyes was enforced.

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
| T1 | **End-to-End API Testing** — validate all REST endpoints with valid/invalid payloads, cover all 7 roles | api/testing | 🟡 In Progress — `probe:flows` verified **31/31 (2026-10-22)** against a live stack; Playwright UI suites **9/9**. Full-stack covered, but probe is role-matrix-thin: runs as admin/doctor only, not all 7 roles |
| T2 | **Negative Scenario Testing** — malformed requests, missing auth, unauthorized access, boundary values, duplicate submissions | api/testing | 🟡 In Progress — error contract + RBAC matrix tests and probe negative paths (missing idempotency key → 400) verified 2026-10-22 |
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

## Design programmes and their status (2026-10-22 audit)

All design work now lives under `docs/`. Status verified against the code,
not against the documents.

### A. MedWorks parity programme (`docs/superpowers/specs/2026-10-19-medworks-parity-design.md`, ADR-0010) — **the active programme**
27 modules, 109 new tables, 8 delivery waves. Wave 0 is a gate the parity work
cannot start without. Its five items, verified against the working tree:

| Wave-0 item | Status | Evidence |
|---|---|---|
| Consent decoupled from registration | ✅ **Closed** (2026-10-22) | `PatientService` no longer throws when `dpdpConsent` is absent; the DTO field is optional and the stored flag mirrors it (no fabricated consent event). Pinned by `PatientServiceTest.registerPatient_withoutConsent_registersAndLeavesNoConsentEvent` and `PatientRegistrationConsentOptionalTest` (400 → 201) |
| `/users/me` stops returning the password hash | ✅ **Closed** (#108, `fd19cce`) | `CurrentUserDTO`; Playwright assertion green 2026-10-22 |
| Per-tenant DEK rotation | 🔴 **Open** | `TenantKeyHolder.currentGeneration()` hardcodes `1` |
| Observability (metrics, structured logs, correlation IDs) | 🟡 **First slice landed** (2026-10-22) | Correlation ID filter (`X-Request-Id` → MDC → logback pattern `%X{requestId}`, proxy ids honored only when safe) + Prometheus scrape (`micrometer-registry-prometheus`, `/manage/` + `prometheus` exposed, ADMIN-gate relaxed for it inside the private network) + `json-logs` profile via logstash-logback-encoder. Pinned by `RequestCorrelationFilterTest` (5 specs) and `ObservabilityHttpContractTest` (3 specs). Remaining to close #9 fully: wire `json-logs` into prod containers, define the dashboard/alert thresholds |
| Shared `<DataTable>` / `<CounterRow>` / `<FilterBar>` component set | ✅ **Closed** (2026-10-22) | Built in `frontend/src/components/` with 19 tests (19 vitest specs): `<DataTable>` owns the §3.2 #6–#8 grammar (dense table, `role="alert"` failure ≠ empty state, "Showing X–Y of Z · page i of N" footer with ‹ Prev/Next ›). Adoption follows each page's restyle wave — the design-system ratchet (`src/test/design-system.test.js`) keeps the eight existing page components on their documented inline-style baseline until then |

Wave 0 is followed by Wave 1 (front desk; enquiry/appointments), then clinical
core, inpatient, diagnostics, etc. The spec names Wave 7 (double-entry
accounts) as the largest risk needing its own design pass.

### B. UI/UX redesign (`docs/superpowers/specs/2026-09-27-medos-ui-ux-redesign-design.md`) — ✅ **Foundation shipped**
Wave 0 (token layer, shell) merged in #170 (`a63f3a1`); design-token plus
component wave landed and the contrast gate runs in CI. 61 UI snapshots
captured 18 Oct 2026 (`docs/ui-snapshots/`). Page-level redesign continues
against the spec's waves as pages are touched.

### C. Key-material deepening (`docs/architecture-review/*.md`, `docs/adr/0001-tenant-encryption-seam-deepening.md`, `docs/superpowers/specs/2026-10-04-tenant-key-resolver-design.md`) — 🟡 **Proposed, not started**
`TenantKeyHolder` still a 491-line hand-rolled mix of 6 responsibilities; the
`TenantKeyResolver`/`BlindIndexKeyProvider` extraction is a health-adjacent
refactor to do before/during DEK-rotation work, since both touch the same file.

### D. In-flight, uncommitted (working tree)
Backend: `GlobalExceptionHandler` docs/tests + `PharmacyRequestParamContractTest`;
frontend: Pharmacy **"+ Add Medicine"** modal (`pharmacyApi.addMedicine`) +
`PharmacyEmptyCatalogue.test.jsx`. Both suites green on 2026-10-22 (backend 311,
frontend 62, E2E 9/9 flow-probe 31/31). Needs commit/PR to land.

---

## Suggested working order

_Updated 2026-10-22 after the P0 blockers closed. The parity programme's Wave 0
gate is now the organizing priority._

1. ~~**Commit the in-flight Pharmacy work** (§D above)~~ ✅ Done (PR #175, 2026-10-22).
2. **Wave 0 first, before any parity module.** ~~Smallest item with the largest
   compliance value: **decouple consent from registration**~~ ✅ Done (2026-10-22):
   a patient registers without a consent checkbox (DPDP §6(2)); consent stays an
   auditable event in `consents` whenever the caller sends one. Backend + tests
   only; no schema change; nothing else in Wave 0 depends on it.
3. **Shared `<DataTable>` / `<CounterRow>` / `<FilterBar>`** ✅ Done (2026-10-22) —
   built and tested; every Wave 1+ list page imports these instead of hand-rolling.
4. **Tenant write-scoping guard** (#134/G16) ✅ Done (2026-10-22) —
   `TenantWriteScopingGuardTest` guards the entity↔`TENANT_TABLES`↔listener
   alignment in both directions; the first Wave 1 entity that forgets either
   half of write-stamping fails CI. Verified mutant-killing: dropping
   `TenantOwned` from `MedicineCatalog` fails it with the named cause. When
   Wave 1 lands, extend `TenantStatementInspector` itself to writes (issue
   #134), not just the guard.
5. **Observability (#9)** ✅ First slice landed (2026-10-22, PR #179): correlation
   IDs (`X-Request-Id`, MDC-bound, echoed in every log line as `req:<id>`, proxy
   ids honored when safe), Prometheus scrape at `/manage/prometheus`
   (network-private, no PII, pinned by `ObservabilityHttpContractTest` — note
   Boot 3.4+ test autoconfigure flips metrics export off unless
   `@AutoConfigureObservability` is used), and structured-log JSON via the
   `json-logs` profile (`logback-spring.xml` + LogstashEncoder). Remaining for
   full #9 closure: wire `json-logs` into the prod containers + hand HTTP-level
   request/latency dashboards to an alerting spec.
6. **Per-tenant DEK rotation** — pair with the `TenantKeyResolver` extraction
   (§C): same file, one design pass covers both.
7. **Then Wave 1 (front desk)** under the parity spec's wave gates; **then** the
   container hardening sweep, API versioning plan (#11), and P3 (needs
   legal/ops input for retention & consent policies).

## Quick wins (single sitting)

- ~~#8 graceful shutdown (`server.shutdown=graceful`, 30s timeout, readiness group)~~ ✅ Done
- ~~#13 error contract + RBAC matrix test (pure tests, no schema change)~~ ✅ Done
- ~~#10 backup runbook (docs + a cron sidecar yml)~~ ✅ Done (docs/operations.md)
- ~~#15 Trivy + Flyway gate~~ ✅ Done
- ~~#16 TLS + HSTS + docker secrets~~ ✅ Done
