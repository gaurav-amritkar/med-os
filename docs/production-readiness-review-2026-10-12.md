# MedOS Production-Readiness & UI/UX Review (2026-10-12)

> Full review of `main` @ `8854d8e`, performed by five parallel review passes
> (backend security, backend reliability, infra/CI, frontend code, UI design)
> with every top-severity claim independently re-verified in code.
>
> **Verdict:** the codebase is in far better shape than "dull and not
> production ready" suggests — but it is not production-ready, and the UI's
> problem is not bad design, it is a half-finished redesign. The critical
> holes (committed secrets, public onboarding, cosmetic tenant deactivation,
> cross-tenant user listing) were found and genuinely fixed on `main` — each
> fix was verified in code, not just in docs. What remains is a specific,
> fixable set of P1 gaps plus a first-deploy path with four concrete break
> points. The UI is a disciplined, WCAG-verified clinical design system
> (Wave 0) whose page-level redesign waves (1–4) were never executed.

---

## 1. What is genuinely good (do not re-do this)

| Area | Evidence |
|---|---|
| Secrets fail closed | JWT secret / PII key validated at startup, throw on missing (`JwtTokenProvider.java:44-62`, `EncryptionConfig.java:27-33`); no defaults in prod profile |
| Onboarding gated | `public-signup=false` removes the route from permitAll (`SecurityConfig.java:35-77`); secret constant-time compared, blank = disabled (`OnboardingController.java:105-127`); provisioning audited |
| `Tenant.active` enforced | At login *and* per-request (`AuthService.firstActiveTenantMembership`, `JwtAuthenticationFilter.isTenantActive`) — issue #53 truly fixed, fails closed on both paths |
| Per-tenant envelope encryption | DEK per tenant, wrapped KEK, generation tracking; blind index uses an independently-lifecycled per-tenant key (`TenantKeyHolder.java:213-221`, `BlindIndexUtil.java:37-77`) |
| KEK rotation runner safe by construction | Dry-run default, explicit apply/verify, refuses target ≤ in-use, refuses on any tenant that can't unwrap (`PiiKeyRotationRunner.java:58-83, 118-150, 209-215`) |
| Migration discipline | Single Flyway owner (ADR-0005), drift gate **with tests for itself** (`tools/verify-migrations.sh`), migrations excluded from the backend jar with the incident documented, CI validates prod compose invariants |
| Trivy gate (roadmap #15) | Done — digest-pinned scanner, `.trivyignore` ratchet, SARIF uploaded even on failure |
| Error contract + RBAC | `@Valid` on all 9 controllers, bean validation on DTOs, 400/409 mapping (`GlobalExceptionHandler.java:87-141`), 22-case RBAC matrix test, `TenantIsolationTest` |
| Money math | BigDecimal everywhere, centralized GST (`MoneyUtil.java:14-49`), invoice-locked payments — double-payment properly prevented (`PaymentService.java:46`, `InvoiceRepository.java:21-23`) |
| FEFO dispense safety | Batch lock + pre-validated total availability prevents partial stock deduction (`DispenseService.java:56-63`) |
| UHID / invoice / payment numbers | Sequence-backed, unique-constrained — no allocation races |
| Frontend guardrails | Contrast checker over 29 token pairs (`scripts/contrast-check.mjs`), design-system ratchet test (`src/test/design-system.test.js`), skip link, reduced-motion + forced-colors, failure-vs-empty distinction with tests, 401 login-vs-expiry disambiguation (`client.js:56-68`), idempotency module + replay probe |
| CI | Backend tests incl. migrations on real Postgres; frontend lint + tests + build; compose model validation for dev **and** prod; prod invariant assertions (only Caddy published, no in-host DB, no ignored secret keys) |
| Issue tracking honesty | #16 records verified vs unverified with unusual precision; planning docs flag their own staleness |

---

## 2. P1 — ship-blockers (fix before any deploy)

### 2.1 Security

| # | Finding | Evidence | Impact |
|---|---|---|---|
| S1 | **Zero-membership users get a global admin token** — `activeMembership == null → ROLE_ADMIN`, `tenantId = null`; the tenant inspector leaves SQL unmodified for null-tenant, so the account sees every tenant's patients. The platform super-admin flag now exists; the fallback shouldn't. | `AuthService.java:73-78` (verified) | Cross-tenant PHI read for any mis-provisioned account |
| S2 | **10-hour revocation gap** — `JwtAuthenticationFilter` re-checks only `tenant.active`; membership deactivation mid-session is ignored until token expiry (`expiration-ms: 36000000`). | `JwtAuthenticationFilter.java:39-52`, `application.yml:67` | Deactivated staff keep PHI access for up to 10 h |
| S3 | **Login rate limiter bypassable via `X-Forwarded-For`** — trusts the client-supplied first XFF value; rotating XFF resets the counter. | `LoginRateLimiter.java:98-105` | Brute-force protection is advisory unless the proxy overwrites the header |
| S4 | **Idempotency cache not tenant/user-scoped** — key = `idempotency:{endpoint}:{key}` with no tenant/user component; also non-atomic GET-then-SET, and replay always returns 200 (original was 201). | `IdempotencyService.java:36`, `IdempotencyFilter.java:73-87` | Two tenants presenting the same key can receive each other's payment/invoice response; double-execution race |
| S5 | **Notification `markRead` IDOR** — `findById(id)` with only `isAuthenticated()`; `notifications` is absent from `TENANT_TABLES` and there is no userId check on the write. | `DashboardController.java:77-85` (verified) | Any authenticated user can read-mark any notification by ID |
| S6 | **No authentication-event auditing** — logins, failures, lockouts never reach `audit_log`; `AuditLog` carries no `tenant_id`. | `AuthService.java:36-98` has no `AuditLogger` reference | For a hospital system, login/lockout is exactly what the audit trail exists for |
| S7 | **WebSocket SUBSCRIBE not tenant-checked** — CONNECT is authenticated, but no interceptor validates destinations. | `WebSocketConfig.java:56-75` [INFERENCE on actual leak: depends on topic payloads] | Potential cross-tenant message exposure |
| S8 | **Dead CORS surface** — `X-Tenant-Id` whitelisted, read by nothing in the backend. | `SecurityConfig.java:108` | Remove it; suggests a removed client-driven tenant header |

**Fixes:** S1 → refuse login (403) when no membership and not `isSuperAdmin`; S2 → per-request membership check (cheap, the load already happens) or short access TTL + refresh tokens; S3 → use the proxy-appended XFF value or socket address; S4 → tenant+user in the key, `SETNX`, preserve original status on replay; S5 → ownership check on the write; S6 → audit login/failure/lockout with tenant context.

### 2.2 Reliability (business logic races)

| # | Finding | Evidence | Impact |
|---|---|---|---|
| R1 | **Double-dispense race** — `pending` status checked on an unlocked read; batches are locked, the prescription row never is. | `DispenseService.java:40-44` | Two concurrent dispenses both deduct stock and both create pharmacy charges |
| R2 | **Double-discharge race** — status checked *before* the room lock; the admission row is never locked. No partial unique index on `admissions WHERE status='admitted'` as backstop. | `AdmissionService.java:77-88` | Duplicate room charges; both discharges "free" the room |
| R3 | **Double-invoice race** — charges read unlocked, filtered `unbilled` in Java, flipped to `billed`; `@Version` alone is not a claim step. | `BillingService.java:45-72` | Two invoices can bill the same charges |
| R4 | **Idempotency check-then-act** — see S4. | `IdempotencyFilter.java:73-78` | Same-key concurrent requests both execute |

**Fixes:** lock the state-machine row (`findByIdForUpdate` on prescription/admission) or use conditional updates (`UPDATE ... WHERE status='pending'` / `'unbilled'`, verify affected rows); add the partial unique index; make idempotency atomic.

### 2.3 Data integrity

| # | Finding | Evidence | Impact |
|---|---|---|---|
| D1 | **23 tables, 4 foreign keys** — only `tenant_users` / `tenant_configs` / `tenant_settings` have FKs. `charges.patient_id/invoice_id`, `payments.invoice_id`, `admissions.patient_id/room_id`, `medicine_batches.medicine_id`, `stock_transactions.*` are all orphan-capable. | `V1__initial_schema.sql` (`grep -c REFERENCES` = 4, verified) | The single biggest schema gap for a medical-billing system |
| D2 | **`tenant_id` nullable** on nearly every domain table while isolation is app-side — a NULL row escapes tenant filtering. | e.g. `V1:184, 240, 265, 307, 327, 364` | Silent cross-tenant escape hatch |
| D3 | **Async balance recalc is fire-and-forget** — `@Async` + `AFTER_COMMIT` + `REQUIRES_NEW`, no retry/outbox/reconciliation job. Outstanding counts only `billed|paid` charges — reception sees ₹0 for patients with unbilled dispensed drugs or room days; overpayments are hidden by `GREATEST(...,0)`. | `PatientBalanceService.java:37-63`, `PatientBalanceEventListener.java:19-24` | Silently stale `patients.outstanding`; misleading AR picture |

### 2.4 Deploy path (first things that break, in order)

Verified against `docker-compose.prod.yml`, `docs/operations.md`, and issue #16:

1. **`MEDOS_DOMAIN=hms.example.com` placeholder** → Caddy cannot obtain an ACME cert; since Caddy is the only published port, the entire system is unreachable.
2. **No image registry / CI push step** — every tag is `${MEDOS_TAG:-local}`; the host must build from source.
3. **`secrets/cors-origins.txt` is a dead second source of truth** — `CORS_ORIGINS` is composed from `${MEDOS_DOMAIN}`; the mounted secret file is never read. **`.env.prod.example` does not exist** while `docs/operations.md:216` tells the operator to copy it.
4. **The Supabase migrate wrapper has never been run end-to-end** (flagged in #16) — though the baselineVersion=0 fix was validated against the live project.

### 2.5 Operations documentation — dangerous as written

| # | Finding | Evidence | Impact |
|---|---|---|---|
| O1 | **PII rotation docs contradict each other.** §7 instructs re-encryption via raw SQL `encrypt(decrypt(...))` against plaintext columns that no longer exist (storage is AES-256-GCM ciphertext); §7d correctly describes the KEK/DEK re-wrap runner; README says rotation breaks all ciphertext. | `docs/operations.md:323-333` vs `:377-450` | An operator following §7 **destroys readable PII**. Correct before anyone rotates on a schedule |
| O2 | **No CSP anywhere** — Caddy sets HSTS/nosniff/Referrer-Policy/no-store; nginx.conf sets no headers at all; no Content-Security-Policy in the repo. | `caddy/Caddyfile:36-42`, `frontend/nginx.conf` | XSS on a patient-record app has no second line of defense |
| O3 | **No resource limits** on any service in either compose file (only JVM `-Xmx512m`). | `docker-compose.yml`, `docker-compose.prod.yml` | One noisy service OOMs the single host (ADR-0001 accepts single-host) |
| O4 | **Redis healthcheck leaks password to the process list** (`-a` flag). Use `REDISCLI_AUTH`. | `docker-compose.prod.yml:105` | Host-local secret exposure |
| O5 | Backup/restore runbook **never executed** against the production target; RPO asserted, not demonstrated. | issue #16 comment 6 | Recovery targets unverified |

### 2.6 Observability (roadmap #9 — still open, still a launch requirement)

No Prometheus metrics, no structured logs, no correlation IDs. For a hospital system this is a launch requirement, not a nice-to-have.

---

## 3. P2 — should-fix (next sprint, not launch-blocking)

- **No discharge summary** (#69): IPD records one free-text diagnosis; `AdmissionService.dischargePatient` writes only `dischargeDiagnosis`.
- **`lab_orders` dead table** (#63): entity + repository exist, no service/controller, no lab charges generated; consultation charges are never generated either.
- **`ai_note` never written** (#65): read path exists, no `setAiNote` anywhere.
- **Partial dispensing unreachable**: dispensing 2 of a qty-10 prescription marks it `dispensed`; `partially_dispensed` (V1:185) has no code path.
- **Room GST hardcoded 0%** while `MoneyUtil.GST_RATE_ROOM = 12%` sits unused.
- **Name search decrypts up to 5000 rows per query** (`PatientService.java:128-140`): blind index matches full names only; fragment search brute-forces in Java. Dies past a few thousand patients.
- **Tenant isolation is string-rewriting SQL** (`TenantStatementInspector`, hard-coded 14-table allowlist) — fragile by design, RLS consciously deferred (ADR-0002). Every new tenant-owned table must be added to `TENANT_TABLES` or it silently escapes scoping. Consider a startup assertion that every `TenantOwned` entity's table appears in the list.
- **Legacy unbounded endpoints**: `listPatients(search)` with `Integer.MAX_VALUE`, raw `List` returns on admissions/rooms/unbilled/invoices.
- **`performedBy` never set** on stock transactions; `InventoryService.addStock` accepts past expiry dates.
- **Frontend:** no debounce/abort/sequence guard on any search (4 pages fire a request per keystroke; Pharmacy refetches the whole catalog per keystroke); modals lack `role="dialog"` / focus trap / Escape (7 hand-rolled copies); Admissions page has zero error handling; Dashboard shows fabricated zeros on failure (`console.error` only); global loading store is dead code; `patientBannerStore` ships but nothing ever sets it.
- **Login page leaks ops info in production** (unconditional JSX): bootstrap password mechanism + seed-script account list. Gate behind `import.meta.env.DEV`.
- **CI gaps**: `npm run check:contrast` isn't wired into CI (the contrast gate runs only locally); no E2E in CI.
- **Dead API surface**: notifications badge/mark-read, invoice charges/payments drill-down, UHID lookup, patient history — endpoints wrapped, no UI.
- **Misc**: role stored separately from user and can drift (two case conventions between `ProtectedRoute` and `Sidebar`); toast IDs from `Date.now()`; sidebar clock renders once; Trivy baseline permanently ignores 3 backend CRITs (monthly review is the only mitigation); Caddy image runs as root while app containers are non-root.

---

## 4. UI/UX review

### 4.1 Diagnosis: not bad design — an unfinished one

Wave 0 (token layer, shell, a11y machine-guards) is disciplined clinical craft. Waves 1–4 were never executed: `design-system.test.js` still ratchets the inline-style debt of 8 pages, `index.css:1008-1023` still carries the "replaced in Waves 1-4" section, and the redesign spec exists at `docs/superpowers/specs/2026-09-27-medos-ui-ux-redesign-design.md`. Five compounding causes of "dull":

1. **Zero elevation** — `--shadow-1` is used exactly twice (toast, modal); every card/stat/panel is an identical flat white box with a 1px 3:1 border. Nothing has visual weight (`index.css:47-48, 414-418, 785, 825`).
2. **Two tiny radii** — `--r-sm: 3px`, `--r-md: 6px`; nothing rounds or softens (`index.css:50-51`).
3. **One accent hue** — `--action #0A5C6B` is the only chrome color; the four status hues live only in badges (`index.css:16-56`).
4. **One-size-small typography** — content mostly `--t-dense`/`--t-micro` (13/12px); page h1 is 1.375rem; no display scale anywhere (`index.css:66-68, 83`).
5. **The dashboard is the dullest page** — no chart, six identical stat boxes, Unicode-glyph quick actions (`◈ ◎ ▣ ⬡ ₿`, `Dashboard.jsx:68-80`) the icon set was built to replace, and **`recharts@^3.9.2` installed but imported nowhere** (`package.json:23`).

### 4.2 Fix function before beauty — verified defects

| Defect | Where | Impact |
|---|---|---|
| **Invoice modal dead end** (#115): charge checkboxes double-toggle to zero (row `onClick` + checkbox `onChange` both call `toggleCharge`, no `stopPropagation`); modal has no charge list; discount is sent but ignored by the backend total | `Billing.jsx:148-154, 210-240, 45, 227` | Billing's core flow barely works; only "Select All" enables Generate |
| **Charge selection survives patient switch** | `Billing.jsx:21-31` never clears `selectedCharges` | "Bill (n)" can invoice another patient's charges |
| **Payment modal leaks previous invoice's method/ref** | `Billing.jsx:14, 67, 192` — reset only after success; prefill spreads old state | Wrong payment method recorded |
| **Three encounter tabs all render "active", none clickable** | `Encounters.jsx:304-308` | Fake UI; all content shows at once |
| **#117 half-fixed** — `Users.jsx` has the model field-error pattern (`aria-invalid`, mapped `validationErrors`); `Patients.jsx` still collapses everything to one toast; `parseInt(age)` NaN silently | `Users.jsx:113-119, 283-321`; `Patients.jsx:55, 64` | Registration errors invisible |
| **Manual Rx form via `document.getElementById`** | `Encounters.jsx:372-391` | Anti-pattern; fragile |
| **No destructive-action confirmations** | role change / access-end `Users.jsx:127-150`; encounter sign `Encounters.jsx` | Irreversible clicks |
| **Silent failures** — empty `catch {}` on profile/tab loads; Admissions has no `.catch` at all | `Billing.jsx:34`, `Patients.jsx:92`, `Encounters.jsx:84`, `Admissions.jsx:17-25` | Pages fail as empty tables |
| **Keyboard/AT gaps** — modals keep background in tab order; table rows as click targets without roles; search inputs unlabeled; room cards are clickable `div`s | `Billing.jsx:150`, `Patients.jsx:175`, `Admissions.jsx:107-113` | Accessibility gaps despite strong token-level a11y |

### 4.3 Redesign directions

| | A — "Clinical Depth" (recommended) | B — "Soft Premium" | C — "Editorial Data-Dense" |
|---|---|---|---|
| Idea | Keep ledger discipline; add elevation + spatial hierarchy | Warm neutrals, big radii, second brand hue | Data *is* the brand: Plex Mono ledger identity |
| Signature moves | Card hover-lift + new `--shadow-card`; `--r-lg: 10px`; real display scale; recharts dashboard (area/sparkline); skeleton loaders; grouped rail with live count badges; finish Waves 1–4 | Parchment cards; tinted acuity patient banner; split-screen login/onboarding; tenant logo slot; pressed/inset button states | 2.75–3rem display numbers + mono sparklines; mono numerals app-wide; "morning briefing" dashboard; typographic empty states; wider rail |
| Files | `index.css` tokens only + `Sidebar/Header/Dashboard/Patients/Admissions` + clear the 8 ratchet entries in `design-system.test.js:51-61` | `index.css` token block + `Sidebar/Header/Login/Onboarding/PatientBanner/ToastContainer` + all pages | `index.css` + `Dashboard/Patients/Encounters/Billing/Pharmacy/Sidebar` + ratchet |
| Risk | Minimal — non-text tokens only; contrast check untouched | Real — every text-on-tint pair must join the 29-pair contrast table and the `prefers-contrast`/forced-colors blocks in lockstep | Medium-high — zebra rows need a new contrast pair; current inline-colored stat numbers violate 1.4.1 and must go |
| Effort | Days | ~1–2 weeks | ~2 weeks |

**Recommendation: A, executed as "finish Waves 1–4 first, then add depth."** B/C are identity decisions to make against a dashboard mock before touching tokens.

---

## 5. Recommended execution order

| Phase | Scope |
|---|---|
| **1 — Ship-blockers** | S1–S6, R1–R4, Billing UI defects (#115 + selection leak + payment leak), D1 FK migration + `tenant_id NOT NULL`, O1 ops-doc correction, Login info-leak gating |
| **2 — Deploy path** | Domain / registry / `.env.prod.example` / dead CORS secret, CSP (O2), resource limits (O3), observability (#9), restore drill (O5) |
| **3 — UI/UX** | Finish Waves 1–4 (clear the 8-page inline-style ratchet), shared `Modal` with focus management, shared `Field` + search debounce hook, then Direction A tokens + recharts dashboard |
| **4 — Product gaps** | #69 discharge summary, #57 tenant name in header (data already fetched), notifications wiring (endpoint exists, UI dead), configurable GST (#59), printable receipts (#58) |

Phases 1 and 3 are largely parallelizable; Phase 2 needs a real domain to test against.

---

## 6. Verification status

- Every P1 claim in this document was either read directly in source (file:line) or confirmed against a live GitHub issue; the four top-severity security findings, the three business races, the FK count, and the Billing/Encounters UI defects were independently re-verified in this session.
- Items marked [INFERENCE] are reasoned but not executed (S4 cross-tenant reachability, S7 actual leak, D3 production behavior).
- Nothing in this document was tested at runtime; no builds or tests were run for this review.
