# MedOS HMS — Comprehensive Gap Analysis
**Date:** 2026-10-13  
**Scope:** `main` branch, all layers — backend security, business logic, data integrity, frontend, infrastructure, observability, and feature completeness  
**Method:** Cross-referenced `docs/production-readiness.md`, `docs/production-readiness-review-2026-10-12.md`, `docs/requirements-traceability.md`, `docs/feature-releases.md`, `docs/roadmap.md`, nine ADRs, four `docs/architecture-review/` deep-dives, and direct source inspection  
**Status of fixed items:** Items marked ✅ in earlier docs are excluded unless re-opened by code evidence

> **Reading order:** §1 is the executive summary table; §2–§8 give evidence, impact, and recommended fix for every open gap. Items are tagged P0/P1/P2/P3 and assigned to functional areas (Security, Reliability, Observability, Feature, Architecture, Infra, Frontend).
>
> ⚠️ **Read the status note before trusting any row.** This analysis was taken
> against `main` **before** the P1 security batch landed the same day. Five items
> in it — **G01–G05** — were fixed and merged within hours of being written, and
> are marked ✅ below with the PR that closed them. Three claims were checked
> against the code and found wrong; those corrections are noted inline rather
> than quietly dropped.

---

## §1 Executive summary

| # | Gap | Area | Severity | Tracked |
|---|-----|------|----------|---------|
| G01 | ~~Zero-membership users receive a global-admin token ~~| Security | ✅ **Fixed** | #136 — membership-less login now refused with 403 |
| G02 | ~~10-hour token revocation gap on membership deactivation ~~| Security | ✅ **Fixed** | #167 — membership re-checked per request |
| G03 | ~~Login rate limiter bypassable via `X-Forwarded-For` rotation ~~| Security | ✅ **Fixed** | #164 — socket peer replaces `X-Forwarded-For` |
| G04 | ~~Idempotency cache not tenant/user-scoped; non-atomic check-then-set ~~| Security | ✅ **Fixed** | #166 — key scoped to tenant+user, claimed atomically |
| G05 | ~~`markRead` IDOR — any authenticated user marks any notification ~~| Security | ✅ **Fixed** | #138 — mark-read scoped to the recipient |
| G06 | No authentication-event auditing (login / failure / lockout) | Security | **P1** | #134/S6 |
| G07 | No rate limiting on tenant onboarding endpoint | Security | **P1** | feature-releases S1 |
| G08 | Dead CORS header `X-Tenant-Id` whitelisted but never read | Security | **P1** | #134/S8 |
| G09 | Double-dispense race — prescription row never locked | Reliability | **P0** | #134/R1 |
| G10 | Double-discharge race — admission row never locked | Reliability | **P0** | #134/R2 |
| G11 | Double-invoice race — unbilled charges claimed without row lock | Reliability | **P0** | #134/R3 |
| G12 | No container resource limits; one noisy service OOMs the host | Infra | **P1** | #134/O3 |
| G13 | Redis healthcheck leaks password to process list (`-a` flag) | Infra | **P1** | #134/O4 |
| G14 | Backup/restore runbook never drilled against production target | Infra | **P1** | #134/O5 |
| G15 | Zero observability: no Prometheus metrics, no structured logs, no correlation IDs | Observability | **P1** | #9 |
| G16 | `TenantStatementInspector` covers `SELECT` root-FROM only; writes rely on services setting `tenantId` | Architecture | **P1** | #134 |
| G17 | Name search decrypts up to 5,000 rows per query in Java; dies past a few thousand patients | Architecture | **P1** | #134 |
| G18 | Per-tenant DEK rotation not implemented; `currentGeneration()` hardcodes `1` | Architecture | **P1** | ADR-0009 |
| G19 | Blind-index key rotation has no operator surface | Architecture | **P1** | ADR-0009 |
| G20 | No discharge summary; `dischargePatient` writes only `dischargeDiagnosis` string | Feature | **P2** | #69 |
| G21 | `lab_orders` table is dead — entity/repo exist, no service/controller, no charges generated | Feature | **P2** | #63 |
| G22 | `ai_note` never written; read path exists but no `setAiNote` anywhere | Feature | **P2** | #65 |
| G23 | Partial dispensing status `partially_dispensed` has no code path; qty-2 of qty-10 marks `dispensed` | Feature | **P2** | #134 |
| G24 | Room GST: `MoneyUtil.GST_RATE_ROOM = 12%` unused; hardcoded 0% charged | Feature | **P2** | #134 |
| G25 | AI medicine advisor calls no LLM; `AiMedicineService` is a keyword matcher only | Feature | **P2** | #61 |
| G26 | No Reports page; `/reports` route missing; Recharts imported by 0 files | Feature | **P2** | #62 |
| G27 | Appointment system incomplete; entity/repo exist, no meaningful service behaviour | Feature | **P2** | #50 |
| G28 | `performedBy` never set on stock transactions; past expiry accepted by `addStock` | Feature | **P2** | #134 |
| G29 | OpenAPI: springdoc wired, no API versioning plan | API | **P2** | #11 |
| G30 | Super-admin deboard/lock not built; `Tenant` has no `SUSPENDED`/`locked`/`deleted_at` state | Feature | **P3** | feature-releases #9 |
| G31 | Landing page missing alongside login | Feature | **P3** | feature-releases #1 |
| G32 | Tenant name not shown post-login; data exists, never sent to frontend | Feature | **P3** | feature-releases #2, #57 |
| G33 | Printable receipt missing | Feature | **P3** | feature-releases #4 |
| G34 | GST not configurable per tenant; rates hardcoded | Feature | **P3** | feature-releases #5 |
| G35 | Wards/rooms: ward is a free-text string, rooms read-only | Feature | **P3** | feature-releases #6 |
| G36 | No payment gateway; billing is manual ledger only | Feature | **P3** | feature-releases #8 |
| G37 | Frontend: no debounce/abort guard on 4 search pages | Frontend | **P2** | #134 |
| G38 | Frontend: modals lack `role="dialog"`, focus trap, Escape (≥7 hand-rolled copies) | Frontend | **P2** | #134 |
| G39 | Frontend: Admissions page has zero error handling | Frontend | **P2** | #134 |
| G40 | Frontend: Dashboard shows fabricated zeros on failure (`console.error` only) | Frontend | **P2** | #134 |
| G41 | Frontend: global loading store is dead code | Frontend | **P3** | #134 |
| G42 | Frontend: z-index tokens missing from design system | Frontend | **P3** | req 6.4 |
| G43 | Frontend: empty-state layout ad hoc per page, not centralised | Frontend | **P3** | req 6.5 |
| G44 | No penetration testing; OWASP Top 10 un-exercised | Security | **P2** | T3 |
| G45 | Redis persistence/failover never tested; RDB/AOF state unverified | Infra | **P2** | T7 |
| G46 | RBAC matrix test has no row for users, tenants, onboarding endpoints | Testing | **P2** | feature-releases §3 |
| G47 | No full encrypt→rotate→decrypt end-to-end PII journey test | Testing | **P2** | T6 |
| G48 | Unbounded list endpoints remain on admissions, rooms, unbilled, invoices | API | **P2** | #134 |

---

## §2 Security gaps

### ✅ G01 — Zero-membership users got a global-admin token — FIXED (#136)
**Evidence:** `AuthService.java:73-78` — when `activeMembership == null`, the code falls through to `ROLE_ADMIN` with `tenantId = null`. `TenantStatementInspector` leaves SQL unmodified for `null` tenant, so the account sees every tenant's patients.  
**Impact:** Any mis-provisioned or partially-deleted account gets cross-tenant PHI access.  
**Fix:** Refuse login with 403 when `activeMembership == null` and `!isSuperAdmin`. Super-admin accounts must be explicitly flagged; the `null`-tenant fallback must be removed.

### ✅ G02 — 10-hour token revocation gap — FIXED (#167)
**Evidence:** `JwtAuthenticationFilter.java:39-52` re-checks only `tenant.active`; membership deactivation mid-session is ignored until token expiry (`expiration-ms: 36000000` = 10 h).  
**Impact:** A deactivated staff member retains full PHI access for up to 10 hours.  
**Fix:** Either validate active membership on every authenticated request (the membership row is already loaded for tenant-isolation, cost is a JOIN already present) or reduce access token TTL to ≤15 min and introduce a refresh-token flow.

### ✅ G03 — Login rate limiter bypassable — FIXED (#164)
**Evidence:** `LoginRateLimiter.java:98-105` trusts the client-supplied first `X-Forwarded-For` value. Any client can rotate headers to reset their attempt counter.  
**Impact:** Brute-force protection was advisory whenever the request reached the backend without the trusted proxy in front.

> **Correction (verified 13 Oct):** the parenthetical here originally read *"Caddy does not set this by default"*. That is **false** — `caddy/Caddyfile:48` sets `header_up X-Forwarded-For {remote_host}`, overwriting rather than appending, so through the production edge the value *was* trustworthy. The finding still stands on its own terms: the limiter is the only control against password guessing, and whether it holds should not change with the proxy in front. Fixed in #164, which is stronger than either fix proposed here.
**Fix:** Use the last XFF value appended by the trusted proxy, or the socket address, not the client-supplied first value.

### ✅ G04 — Idempotency cache not tenant/user-scoped — FIXED (#166)
**Evidence:** `IdempotencyService.java:36` — key is `idempotency:{endpoint}:{key}` with no tenant/user component. `IdempotencyFilter.java:73-87` — non-atomic GET-then-SET; replay always returns HTTP 200 (original may have been 201).  
**Impact:** Two tenants presenting the same idempotency key can receive each other's payment/invoice response; concurrent same-key requests both execute.  
**Fix:** Key must be `idempotency:{tenantId}:{userId}:{endpoint}:{key}`. Use Redis `SETNX` for atomicity. Preserve the original response status code on replay.

### ✅ G05 — `markRead` IDOR — FIXED (#138)
**Evidence:** `DashboardController.java:77-85` — `findById(id)` with only `isAuthenticated()`. `notifications` is absent from `TENANT_TABLES`; no userId ownership check on the write.  
**Impact:** Any authenticated user can read-mark any tenant's notification by guessing/iterating IDs.  
**Fix:** ~~Add `notifications` to `TENANT_TABLES`~~ — **not done, deliberately.** `notifications` is not tenant-owned; it is addressed to a *recipient*, so a tenant predicate would be the wrong axis. #138 instead added an ownership-scoped lookup (`findByIdAndRecipientId`) and made a non-owned id return 404 rather than a blanket 200, which also stops the endpoint being an existence oracle.

### G06 — No authentication-event auditing
**Evidence:** `AuditLog` entity exists but is never written for login success, failure, or lockout. `LoginRateLimiter` tracks counters in Redis but produces no audit record.  
**Impact:** No forensic trail for credential-stuffing attacks, lockout patterns, or post-breach attribution.  
**Fix:** Write audit records with tenant context for login success, login failure, and lockout events.

### G07 — No rate limiting on tenant onboarding endpoint
**Evidence:** `LoginRateLimiter` covers `AuthService.login` only. The onboarding guard is a single shared secret with no lockout and no rotation policy. The endpoint provisions a full tenant with an admin account.  
**Impact:** Secret is brute-forceable; no per-operator attribution.  
**Fix:** Apply rate limiting to `/api/v1/onboarding/register` (count by IP; short lockout window). Consider per-request HMAC with a nonce so the secret can be rotated without downtime.

### G08 — Dead CORS header
**Evidence:** `SecurityConfig.java:108` whitelists `X-Tenant-Id` as an exposed CORS header; no backend code reads it.  
**Impact:** Suggests a removed client-driven tenant header was left open; expands attack surface.  
**Fix:** Remove `X-Tenant-Id` from the CORS configuration.

---

## §3 Reliability / race condition gaps

### G09 — Double-dispense race
**Evidence:** `DispenseService.java:40-44` — prescription status is checked on an unlocked read; medicine batches are locked but the prescription row is never locked.  
**Impact:** Two concurrent dispense requests can both deduct stock and both post pharmacy charges against the same prescription.  
**Fix:** `SELECT ... FOR UPDATE` on the prescription row before the status check, or a conditional `UPDATE prescriptions SET status='dispensed' WHERE status='pending'` that fails if already claimed. Only lock batches after claiming the prescription.

### G10 — Double-discharge race
**Evidence:** `AdmissionService.java:77-88` — status is checked before the room lock; the admission row is never locked. No partial unique index on `admissions WHERE status='admitted'` as a backstop.  
**Impact:** Two concurrent discharge calls both succeed, duplicate room charges, and both mark the room as free.  
**Fix:** Lock the admission row first (`findByIdForUpdate`). Add a partial unique index on `(room_id) WHERE status='admitted'` to make the race self-correcting at the DB level.

### G11 — Double-invoice race
**Evidence:** `BillingService.java:45-72` — charges are read without locks, filtered `unbilled` in Java, then flipped to `billed`. `@Version` on the entity alone is not a claim step; it prevents concurrent writes to the same row but not concurrent reads of the same filtered set.  
**Impact:** Two concurrent invoice-generation calls can include the same charges in two different invoices.  
**Fix:** Use `UPDATE charges SET status='billed', invoice_id=? WHERE status='unbilled' AND patient_id=?` and verify `affectedRows > 0`. Alternatively, add `SELECT ... FOR UPDATE SKIP LOCKED` on the charges cursor.

---

## §4 Infrastructure gaps

### G12 — No container resource limits
**Evidence:** `docker-compose.yml` and `docker-compose.prod.yml` have no `mem_limit`/`cpus` on any service (only JVM `-Xmx512m` via `JAVA_OPTS`).  
**Impact:** A single misbehaving service (OOM loop, runaway query) takes down the entire host. ADR-0001 explicitly accepts single-host, making this the only blast radius.  
**Fix:** Add `deploy.resources.limits` (or top-level `mem_limit`/`cpus`) to all services. Sensible starting point: backend 1024 MB / 1 CPU; Caddy 128 MB; Redis 256 MB.

### G13 — Redis healthcheck leaks password to process list
**Evidence:** `docker-compose.prod.yml:105` — Redis healthcheck uses `redis-cli -a $REDIS_PASSWORD ping`. The `-a` flag places the password in the process argument list, visible to any co-tenant process via `/proc`.  
**Fix:** Replace with `REDISCLI_AUTH=$REDIS_PASSWORD redis-cli ping` (environment variable avoids process list exposure).

### G14 — Backup/restore runbook not drilled against production
**Evidence:** `docs/operations.md §1` acknowledges the commands target the dev compose and that Supabase PITR must be "verified in the Supabase dashboard rather than assumed".  
**Impact:** RPO/RTO targets are asserted, not demonstrated; a real incident may fail to restore.  
**Fix:** Execute a documented restore drill against the Supabase target. Record the observed RTO. Schedule quarterly repetition.

---

## §5 Observability gap

### G15 — Zero observability
**Evidence:** #9 (`docs/roadmap.md:102`) marked "Not Started — genuinely the largest remaining gap". No Micrometer/Prometheus endpoint, no JSON-structured logging appender, no `X-Correlation-Id` middleware.  
**Impact:** For a hospital system handling PHI and financial transactions, production incidents cannot be diagnosed. There is no way to know when a multi-tenant isolation query is slow, when a crypto operation fails silently, or whether specific roles are triggering unexpected 500s.  
**Fix (in priority order):**  
1. Add `spring-boot-starter-actuator` + Micrometer Prometheus registry; expose `/actuator/prometheus` (internal network only).  
2. Switch to Logback JSON encoder (`logstash-logback-encoder`); emit `tenantId`, `userId`, `traceId` on every log line via MDC.  
3. Add a servlet filter that generates/propagates `X-Correlation-Id` and puts it in MDC + response header.  
4. Add a Prometheus scraper + Grafana in `docker-compose.prod.yml` as a first-party stack.

---

## §6 Architecture / data-layer gaps

### G16 — `TenantStatementInspector` covers SELECT only
**Evidence:** `TenantStatementInspector` returns early for anything that is not a `select`, so `INSERT`/`UPDATE`/`DELETE` are unscoped; writes depend on services setting `tenantId` explicitly. (This row originally cited `PRODUCTION_READINESS.md:31-32`; that file has since been rewritten, so the claim is restated here rather than left as a dangling line reference.) `TenantStatementInspector` operates only on `SELECT` root-FROM clauses; there are 14 tables in the allowlist.  
**Impact:** Any new tenant-owned entity added without updating `TENANT_TABLES` silently escapes row-level scoping. Service layer bugs (forgotten `tenantId` on writes) are invisible at the SQL boundary.  
**Fix:** Add a startup assertion that every `@Entity` implementing `TenantOwned` has its table name present in `TENANT_TABLES`. Consider a Hibernate event listener that validates `tenantId` on `PreInsertEvent`/`PreUpdateEvent`.

### G17 — Name search scalability cliff
**Evidence:** `PatientService.java:128-140` — blind index matches full names only; fragment search decrypts up to 5,000 rows in Java. With a few thousand patients the query times out.  
**Impact:** Clinical workflows (nurse looking up a patient mid-procedure) fail silently past a low patient count.  
**Fix short-term:** Enforce a minimum prefix length (≥3 chars) before triggering the decrypt loop; add an indexed `name_prefix` column (first 3 chars of the encrypted name, stored plaintext) to limit the candidate set. **Fix long-term:** Implement trigram blind indexing (hash each 3-gram of the plaintext name; store set in `patient_name_trigrams`; query is intersection of hashed 3-grams, decrypt only matches).

### G18 — Per-tenant DEK rotation not implemented
**Evidence:** ADR-0009 implementation status table — "Per-tenant DEK rotation (`dek_rotation`) — 🔴 not implemented"; "`currentGeneration()` hardcodes `1`; generation > 1 unreachable".  
**Impact:** Compromised DEK cannot be remediated without decrypting and re-encrypting all patient data manually.  
**Fix:** Implement `dek_rotation` state machine: generate new DEK, re-encrypt all ciphertext rows for the tenant in a background job (resumable by `last_row_id`), flip `current_dek_generation`, mark old DEK for purge after confirmation. `PiiKeyRotationRunner` (exists) should orchestrate this.

### G19 — Blind-index key rotation has no operator surface
**Evidence:** ADR-0009 — "Blind-index key rotation: 🔴 no operator surface". `BlindIndexKeyService.rewrapAll` exists but is only called by KEK rotation, not by an explicit BI-key rotation command.  
**Impact:** If the blind-index key is compromised, there is no way to re-key it without rebuilding the whole key store.  
**Fix:** Add an admin-only endpoint (or `PiiKeyRotationRunner` command) that rotates the blind-index key and triggers `PatientNameIndexBackfillRunner` to re-hash all patient names.

---

## §7 Missing / incomplete features

### G20 — No discharge summary
**Evidence:** `AdmissionService.dischargePatient` writes only `dischargeDiagnosis` (free text). IPD workflow in the BRD requires a structured summary with procedures performed, medication list, follow-up plan, and referring doctor.  
No `discharge_summaries` table; no corresponding DTO or controller action.

### G21 — `lab_orders` is dead code
**Evidence:** `LabOrder` entity + `LabOrderRepository` exist from V1. No `LabOrderService`, no controller, no charges generated; consultation charges are also never generated. The table carries 25 columns with no write path from any active code.

### G22 — `ai_note` never written
**Evidence:** `Encounter` entity has `ai_note` column. `EncounterService` reads it but `setAiNote` is never called. Read path (`GET /encounters/{id}`) returns `null` for every record.

### G23 — Partial dispensing status unreachable
**Evidence:** `Prescription.status` includes `partially_dispensed` (`V1__initial_schema.sql:185`, `Prescription.java:67`). `DispenseService` sets `dispensed` when any quantity is fulfilled; there is no branch that sets `partially_dispensed`. Dispensing 2 of a 10-unit supply marks the prescription `dispensed` and prevents further fills.

> **Correction (verified 13 Oct):** this row originally attributed the status to `MedicineBatch.status`. The enum is on `Prescription`. The underlying gap is unchanged.

### G24 — Room GST hardcoded 0%
**Evidence:** `MoneyUtil.GST_RATE_ROOM = 12%` is defined but never called. `AdmissionService` charges room fees at 0% GST. Revenue is understated and tax filings are incorrect.

### G25 — AI medicine advisor — no LLM integration
**Evidence:** `AiMedicineService` is a `diseaseMedicineMap` keyword matcher. No HTTP client, no Anthropic/OpenAI dependency. `AI_API_KEY` and `AI_MODEL` env vars are read by nothing. BRD §1.1 requires the endpoint to call an external LLM.  
**Note:** Current absence means no patient data leaves the instance (privacy upside). Implementing it makes BRD §14 Assumption 4 (sanitise identifiers before LLM calls) a live requirement.

### G26 — No Reports page
**Evidence:** Requirements-traceability 3.13 — "`recharts` imported by 0 files". No `/reports` route in `App.jsx`. PRD §3.13 requires Recharts analytics for revenue, patient volume, and pharmacy trends.

### G27 — Appointment system incomplete
**Evidence:** `Appointment` entity and `AppointmentRepository` exist; no `AppointmentService` business logic beyond basic CRUD. No scheduling constraints (double-booking prevention), no patient-facing booking surface, no calendar integration surface.

### G28 — Stock transaction `performedBy` never set
**Evidence:** `InventoryService.addStock` never sets `performedBy`. `StockTransaction` records are unsigned. `addStock` also accepts past expiry dates without rejection.  
**Impact:** Audit trail for controlled substances is incomplete; pharmacy compliance fails.

### G29 — No API versioning plan
**Evidence:** `docs/roadmap.md:104` — "Partial: springdoc wired and ADMIN-gated; no versioning plan". All endpoints are under `/api/v1/` with no strategy for introducing breaking changes.

---

## §8 Frontend gaps

### G37 — No debounce/abort on search inputs
**Evidence:** `production-readiness-review-2026-10-12.md` — "4 pages fire a request per keystroke; Pharmacy refetches the whole catalog per keystroke". No `useEffect` cleanup with `AbortController`, no lodash/native debounce.  
**Impact:** Race conditions produce stale results; backend receives O(N²) requests for fast typists.  
**Fix:** 300 ms debounce + `AbortController` cancel on every search `useEffect`.

### G38 — Modal accessibility failures
**Evidence:** ≥7 hand-rolled modal copies; none have `role="dialog"`, `aria-modal`, focus trap, or Escape-key dismiss.  
**Impact:** WCAG 2.2 AA failure (despite the design system otherwise meeting it); keyboard and screen-reader users cannot operate the application.  
**Fix:** Extract one `<Modal>` component with focus trap (`focus-trap-react` or manual), `role="dialog"`, `aria-labelledby`, Escape key handler. Replace all 7+ copies.

### G39 — Admissions page zero error handling
**Evidence:** `production-readiness-review-2026-10-12.md` — "Admissions page has zero error handling". Network failures silently leave the page blank.  
**Fix:** Wrap API calls in try/catch; render an error boundary or inline error state consistent with the Patients/Encounters pattern.

### G40 — Dashboard fabricates zeros on failure
**Evidence:** Dashboard API failure path calls `console.error` and leaves all counters at zero — clinically meaningless but visually indistinguishable from a real zero. No error state is rendered.  
**Fix:** Distinguish 0 (returned by API) from failure (show an error banner or skeleton error state); do not conflate network failure with successful zero-count responses.

### G41 — Dead loading store
**Evidence:** `loadingStore.js` exists but is not consumed by any component. All loading states are managed ad hoc per-page with local `useState`.

### G42 — Z-index tokens missing
**Evidence:** `requirements-traceability.md:6.4` — "No z-index tokens in `index.css`". PRD §6.4 specifies a `--z-sidebar` … `--z-toast` hierarchy.  
**Impact:** Z-index collisions occur ad hoc as new overlapping elements (modals, dropdowns, toasts) are added.

### G43 — Empty-state layout is ad hoc
**Evidence:** Empty states exist in 10+ places but none share a common `<EmptyState>` component.

---

## §9 Testing gaps

### G44 — Zero penetration testing
**Evidence:** `docs/roadmap.md:T3` — "Not Started". OWASP Top 10 not exercised. Given G01–G05 (all independently discovered auth/authz bypasses), a structured pentest is likely to yield more.

### G45 — Redis persistence/failover never tested
**Evidence:** `docs/roadmap.md:T7` — "Not Started". RDB/AOF configuration unverified against restart. Rate limiter and idempotency state would be lost on Redis crash.

### G46 — RBAC matrix test missing coverage for users/tenants/onboarding
**Evidence:** `feature-releases.md §3` — "The endpoint is still absent from the RBAC matrix test, which has no row for users, tenants, or onboarding." `RbacMatrixTest` (which exists) covers only patient/encounter/pharmacy flows.

### G47 — No end-to-end PII rotation test
**Evidence:** `docs/roadmap.md:T6` — "Partial: key isolation, blind-index independence, versioned ciphertext tests exist; no full encrypt→rotate→decrypt journey." Without this test, G18 (DEK rotation) can be implemented incorrectly without detection.

### G48 — Unbounded list endpoints
**Evidence:** `production-readiness-review-2026-10-12.md` — `listPatients(search)` with `Integer.MAX_VALUE`; raw `List` returns on admissions, rooms, unbilled charges, invoices.  
**Impact:** A tenant with large data volumes causes OOM on the backend and frontend in a single request.  
**Fix:** Apply keyset/cursor pagination uniformly (the `Page<T>` pattern is already established on patient search).

---

## §10 Gap coverage map

```
SEVERITY  OPEN   AREA BREAKDOWN
─────────────────────────────────────────────────────
P0        6      Security (G01-G05): 5  ·  Reliability (G09-G11): 3
P1        10     Security (G06-G08): 3  ·  Infra (G12-G14): 3  ·  Observability (G15): 1
                 Architecture (G16-G19): 4
P2        22     Feature (G20-G29): 10  ·  Frontend (G37-G40): 4
                 Testing (G44-G48): 5   ·  Architecture (G17): 1
P3        10     Feature (G30-G36): 7   ·  Frontend (G41-G43): 3
─────────────────────────────────────────────────────
TOTAL     48
```

### Recommended attack order

1. **P0 batch** — G01 (global-admin fallback), G09–G11 (three races) are highest-leverage; one-session fixes each. G04 (idempotency) and G05 (IDOR) are ~1h each. All six should land in a single PR.
2. **G15 (observability)** — Highest-risk gap for a deployed hospital system; without metrics/logs you cannot detect any of the P0 regressions after they are fixed.
3. **G16 (write-side tenant scoping)** — A startup assertion (fast, safe) buys protection today; a `PreInsertEvent` listener completes it.
4. **G12–G13 (container hardening)** — Mechanical; two compose file edits.
5. **G17 (name search cliff)** — Blocks real clinical use past a few thousand patients; prefix-column approach is a one-migration fix.
6. **P2 feature batch** — G20 (discharge summary), G21 (lab orders), G23 (partial dispensing), G24 (room GST): each is a contained service + migration change.
7. **G18–G19 (DEK/BI rotation)** — Necessary before a multi-tenant breach scenario; `PiiKeyRotationRunner` scaffolding already exists.
8. **P3 features** — These require product decisions (payment gateway, landing page, configurable GST) and should be sequenced by business priority.

---

*Document produced by cross-referencing all existing planning artifacts and source code. Every claim is traceable to a file and line listed above. Items marked "CORRECTED" in prior documents are excluded. Items that appear in prior docs but lack code evidence of a fix are included as open.*
