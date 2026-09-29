# Requirements Traceability — BRD/PRD vs Implementation

Comparison of the v3.0 requirements documents against what is actually built.

- **Sources:** `requirements/BRD.pdf` / `requirements/MedOS_Business_Requirements_Document.html`,
  and `requirements/PRD.pdf` / `requirements/MedOS_Product_Requirements_Document.html`
  (both stamped `2026-04-18`, `STATUS: VERIFIED_DEPLOYMENT`).
- **Verified against:** `main` @ `dc8a8cc`.
- **Method:** every row was checked against the code, the schema, or the running
  app. Nothing here is inferred from a document alone.

## Read this first: the specs are stale as architecture documents

Both documents specify a **Node.js / Express / SQLite / Knex / bcrypt / Anthropic
Claude** product, deployed as a **single-tenant** instance (PRD §7, BRD §4, §10,
§14). The implementation is **Java 21 / Spring Boot 3 / PostgreSQL / AES-GCM /
multi-tenant**.

So this is a comparison at the level of *capabilities and data model*, not stack.
Three consequences:

1. The stack divergence is deliberate and recorded — ADR-0001 (single-host
   managed PostgreSQL) and ADR-0002 (row-level tenancy) both post-date the specs
   and both argue for the change on their merits.
2. Any requirement phrased in stack terms (SQLite file, Knex, `can()` middleware,
   `localStorage` token) is superseded rather than "missing".
3. The specs' own `STATUS: VERIFIED_DEPLOYMENT` and the BRD's "Single Source of
   Truth" framing no longer describe reality. **These documents should be
   rewritten or retired**, not maintained alongside the code.

---

## Verdict summary

| Area | Spec | State |
|---|---|---|
| Data model — patients, encounters, admissions, stock | PRD §1 | **Met** |
| Data model — table count | PRD §1 (19) | **Exceeded** (23) |
| Auth — bcrypt, 10h JWT, 15m/5 rate limit | PRD §2 | **Met, exact** |
| Patient ledger sync | PRD §5.1 | **Met and improved** |
| FEFO inventory | PRD §5.2 | **Met and hardened** |
| Front office — registration, DPDP, audit | BRD §5 | **Met** |
| Front office — role management | BRD §5 | **Missing** |
| OPD — notes, vitals, AI advisor, orders | BRD §5 | **Partial** (orders missing; "AI advisor" is not AI) |
| IPD — bed allocation, occupancy map, discharge | BRD §5 | **Met** |
| Pharmacy — stock in, FEFO out, ledger drilldown | BRD §5 | **Partial** |
| Financial — charges, invoices, AR ledger | BRD §5 | **Partial** (no AR report) |
| Appointments (user flow steps 1–2) | BRD §8 | **Missing** |
| **Claude AI integration** | PRD §2, BRD §2/§4/§12 | **Missing — the headline differentiator** |
| Reports / analytics page | PRD §3 | **Missing** |
| Dark-luxury glassmorphism design system | PRD §6, BRD §6 | **Replaced deliberately** |
| Monopoly: 5 users, AI add-on tier, SLA | BRD §12 | **Missing** |
| NFR — < 1.5s mount, < 200ms API | BRD §10 | **Unmeasured** |
| KPI measurement (§9) | BRD §9 | **Missing — no telemetry at all** |

---

## Full requirement traceability matrix

One row per stated requirement. `Met` means verified in code; `Diverged` means
implemented differently on purpose; `Missing` means absent; `Unmeasured` means no
evidence either way.

### PRD §1 — Database schema mappings

| # | Requirement | State | Evidence / gap |
|---|---|---|---|
| 1.1 | 19 connected entities, UUID primary keys | **Exceeded** | 23 tables (`V1__initial_schema.sql`); UUID PKs throughout |
| 1.2 | `patients.uhid` unique, auto-increment via `MAX()` | **Diverged (better)** | Postgres sequence `uhid_seq` (`:21`, `getNextUhidSeq`). `MAX()` was racy — issues #19–#23 |
| 1.3 | `patients.name` NOT NULL | Met | `Patient.java` |
| 1.4 | `patients.age` validate 0–150 | Met | `@Min(0) @Max(150)` — `PatientRegistrationRequest.java:13-14` |
| 1.5 | `patients.dpdp_consent` boolean | Met | Captured at registration, shown on the patient banner |
| 1.6 | `patients.outstanding` auto-synced | Met | `PatientBalanceService`, event-driven after commit |
| 1.7 | `encounters.patient_id` / `doctor_id` FK to users | Met | Tenant-scoped |
| 1.8 | `encounters.status` ∈ open, signed, cancelled | **Met, exact** | `Encounter.java:74-76` |
| 1.9 | `encounters.vitals_json` JSON payload | Met | Stored and parsed on the OPD form |
| 1.10 | `encounters.ai_note` mutable until signed | **Missing** | Column exists, mapper reads it, **no code path writes it**, and no lock-after-sign rule exists |
| 1.11 | `admissions.status` ∈ admitted, discharged, transferred | **Met, exact** | `Admission.java:71-73` |
| 1.12 | `admissions.room_charges` = days × `rooms.daily_rate` | Met | Computed on discharge |
| 1.13 | `stock_transactions.transaction_type` ∈ in, out, adjustment, return | **Met, exact** | `StockTransaction.java:62-64` (`return_tx`; `return` is reserved) |
| 1.14 | `quantity` negative when type is `out` | Met | Set by the dispense service |
| 1.15 | `batch_no` / `expiry` as the FEFO sort key | Met | `expiry_date` indexed, ordered ASC |

### PRD §2 — API router topology

| # | Requirement | State | Evidence / gap |
|---|---|---|---|
| 2.1 | Login: bcrypt password comparison | **Met, exact** | `BCryptPasswordEncoder` — `SecurityConfig.java:14` |
| 2.2 | Login: 10h JWT | **Met, exact** | `expiration-ms: 36000000` = 10h |
| 2.3 | Login: `RateLimit(15m/5)` | **Met, exact** | `MAX_FAILED_ATTEMPTS = 5`, `LOCKOUT_WINDOW = 15 min` — `LoginRateLimiter.java:29-30` |
| 2.4 | `suggest-medicines` **triggers the Anthropic API** | **Missing** | `AiMedicineService` is a local keyword matcher; no LLM client on the classpath; configured key/model read by nothing |
| 2.5 | `suggest-medicines` matches `diseaseMedicineMap` joined to catalog | Met | `keywordMapMatching` — the one part that works |
| 2.6 | `suggest-medicines` restricted to `can('doctor','admin')` | Met | `@PreAuthorize` on the endpoint |
| 2.7 | `pharmacy/dispense`: select unexpired batches, deduct FEFO | Met | Tested; partial-dispense bug #32 fixed |
| 2.8 | `pharmacy/dispense`: write stock transaction + post charge | Met | Atomic in one transaction |
| 2.9 | `admissions/:id/discharge` computes `days × daily_rate` | Met | `AdmissionService` |

### PRD §3 — UI render tree

| # | Requirement | State | Evidence / gap |
|---|---|---|---|
| 3.1 | `/login` route, triggers auth state | Met | `App.jsx:32` |
| 3.2 | Route guard | Met | `ProtectedRoute.jsx` (spec said `Guard`) |
| 3.3 | Layout with Sidebar + Header + Outlet | Met | `Layout.jsx` |
| 3.4 | Sidebar navigation filtered by role | Met | `Sidebar.jsx` |
| 3.5 | `ClockWidget` with isolated re-render | Met | Present in `Sidebar.jsx` |
| 3.6 | Header shows contextual actions + user profile | Met | **No tenant name** — tracked as #57 |
| 3.7 | Toast notifications | **Diverged** | Zustand store + `ToastContainer`, not React Context. Equivalent |
| 3.8 | Dashboard: metrics and **KPI trends** | **Partial** | 6 live stat cards. **No trends** — no time series anywhere |
| 3.9 | OPD: encounter list + vitals + prescriptions + AI engine | Met | All four in `Encounters.jsx` |
| 3.10 | IPD: room matrix mapped to current admissions | Met | Occupancy grid, `Admissions.jsx` |
| 3.11 | Pharmacy: data grid with **expandable rows** to `transaction_history` | **Partial** | Transactions render as a flat table, not expandable per item |
| 3.12 | Billing: charges → invoices → payments | Met | Multi-ledger flow works end to end |
| 3.13 | **Reports**: read-only Recharts analytics | **Missing** | No `/reports` route; `recharts` imported by **0 files** |

### PRD §4–§7 — State, logic, design system, deployment

| # | Requirement | State | Evidence / gap |
|---|---|---|---|
| 4.1 | Zustand auth store holding token + user | Met | `authStore.js` |
| 4.2 | Token persisted to `localStorage` | **Diverged (better)** | `sessionStorage` — narrows the XSS exfiltration window |
| 5.1 | `outstanding = billed − paid` | **Met, improved** | Implemented from **charges**, not invoices, and floored with `MAX(0, …)` |
| 5.2 | FEFO cascading subtraction | Met | Tested, incl. insufficient-stock spanning batches |
| 6.1 | All styling from root CSS variables | Met | Token layer, no-hardcoded-hex rule |
| 6.2 | Dark base `#06060b`, glassmorphism `backdrop-filter` | **Replaced** | Light clinical theme. BRD §13 names dark-mode visibility as an invalidation trigger — the refactor was the point |
| 6.3 | Hover `translateY(-1px)` + `brightness(1.1)` | **Replaced** | Interaction constants dropped in the rebuild |
| 6.4 | Z-index hierarchy (`--z-sidebar` … `--z-toast`) | **Missing** | No z-index tokens in `index.css` |
| 6.5 | Centralised empty-state layout | **Partial** | Empty states exist in 10+ places, but ad hoc per page, not centralised |
| 7.1 | Parameterised SQL (anti-injection) | **Exceeded** | JPA parameter binding throughout |
| 7.2 | Input validation before persistence | Met | `@Valid` on all 8 controllers |
| 7.3 | Rate limiting on identity endpoints | **Exceeded** | Redis-backed, survives restart, unlike in-process limiting |
| 7.4 | SQLite single-file deployment | **Diverged** | PostgreSQL — BRD §11 named SQLite locking as a risk |

### BRD §5 — Functional requirements by module

| # | Requirement | State | Evidence / gap |
|---|---|---|---|
| 5.1 | Patient registration | Met | Form-driven, journey-tested |
| 5.2 | DPDP consent capture | Met | Checkbox + consent purpose at registration |
| 5.3 | Audit logging | Met | `AuditLogger` on financial and identity events |
| 5.4 | **Role management** | **Missing** | No user management at all — tracked as #55 |
| 5.5 | Encounter notes + vitals capture | Met | |
| 5.6 | AI medicine advisor | **Missing (as AI)** | Keyword matcher only — see §1.1 of the findings |
| 5.7 | **Orders** (lab) | **Missing** | `LabOrder` entity + tenant-scoped table, **no controller, no UI** |
| 5.8 | Bed allocation + visual occupancy map | Met | |
| 5.9 | Discharge summary | **Partial** | `discharge_diagnosis` column + textarea; a diagnosis field, not a summary document |
| 5.10 | Stock in (purchase) | Met | |
| 5.11 | Stock out, FEFO, auto-charge | Met | |
| 5.12 | Pharmacy ledger drilldown | **Partial** | Flat transaction table |
| 5.13 | Charge generation + invoice mapping | Met | |
| 5.14 | Unified patient ledger / **AR ledgers** | **Partial** | Per-patient outstanding shown; **no cross-patient receivables report**, no ageing |
| 5.15 | GST-compliant invoices | **Partial** | Rates hardcoded, no GSTIN/HSN, no per-line tax — tracked as #59 |

### BRD §8 — End-to-end user flow

| # | Step | State | Evidence / gap |
|---|---|---|---|
| 8.1 | Receptionist registers patient + DPDP consent | Met | |
| 8.2 | Receptionist **books an appointment** | **Missing** | No `AppointmentController`, no UI. Dashboard still counts the appointments table, so the KPI is real but the rows are uncreatable through the app — tracked as #50 |
| 8.3 | Nurse views incoming appointments, marks **checked-in** | **Missing** | No appointment or check-in concept exists |
| 8.4 | Nurse captures vitals on a new encounter | Met | |
| 8.5 | Doctor documents complaint, generates prescription, signs | Met | Journey-tested |
| 8.6 | Pharmacist dispenses, system picks closest expiry | Met | FEFO |
| 8.7 | Billing aggregates unbilled, invoices, takes payment | Met | Journey-tested |

**The flow cannot complete: it breaks at step 2.**

### BRD §9–§14 — KPIs, NFRs, economics, assumptions

| # | Requirement | State | Evidence / gap |
|---|---|---|---|
| 9.1 | Reduction in unbilled revenue | **Unmeasured** | No analytics, no baseline, no tracking |
| 9.2 | Reduction in expired write-offs | **Unmeasured** | FEFO reduces it by design; nothing measures it |
| 9.3 | Reduction in average patient wait time | **Unmeasured** | No timestamps for it |
| 9.4 | DAU/MAU across roles | **Unmeasured** | No usage telemetry |
| 9.5 | AI suggestion acceptance rate | **Unmeasured** | No suggestion event is recorded |
| 9.6 | Task completion times | **Unmeasured** | No interaction timing |
| 10.1 | Initial mount < 1.5s | **Unmeasured** | No perf test, bundle budget or web-vitals |
| 10.2 | API response < 200ms | **Unmeasured** | Flow probe reports timings but asserts no budget |
| 10.3 | JWT 10h, bcrypt, audit trail | **Met** | See 2.1–2.3, 5.3 |
| 10.4 | Single-tenant, migrate to PG for multi-tenancy | **Exceeded** | Multi-tenant already shipped — ADR-0002 |
| 12.1 | Base tier includes **5 users** | **Missing** | No seat limit enforced anywhere |
| 12.2 | **AI add-on tier**, per-query metering | **Missing** | No tier concept; nothing to meter (no LLM calls) |
| 12.3 | Maintenance SLA for regulatory updates | **Missing** | No versioning of regulatory configuration |
| 13.1 | Pivot trigger: dark mode hurts visibility in bright clinics | **Actioned** | Light clinical theme shipped |
| 14.1 | Localhost/LAN single-server deployment | **Diverged** | Single-host managed PostgreSQL, multi-tenant — ADR-0001 |
| 14.3 | Pharmacy purely digital, no IoT/barcode in v3.0 | **Met (consistent)** | No barcode/IoT code exists |
| 14.4 | Patient identifiers sanitized before external LLM calls | **Moot** | No LLM call exists, so nothing leaves the instance. Becomes a live requirement the moment §2.4 is built |

### Not requested by either document, but delivered

| Capability | Note |
|---|---|
| PII encryption at rest (AES-256-GCM) | Spec asked only for bcrypt on passwords |
| Keyed blind-index search over encrypted names | No notion of this problem in the spec |
| Multi-tenant row-level isolation, 14 tables | Spec assumed single-tenant |
| Idempotency keys on all financial POSTs | Not in spec |
| Optimistic + pessimistic locking | Spec assumed a synchronous single node |
| WCAG 2.2 AA contrast enforcement | **Spec contains no accessibility requirement at all** |
| Skip link, focus-visible, live regions, keyboard tests | Same |
| Real-time notifications (WebSocket/STOMP) | Absent from both documents |
| Self-serve tenant onboarding | Spec assumed one pre-provisioned instance |
| 93 backend / 35 frontend tests, flow matrix, journey, 3-job CI | Not specified |
| Six ADRs recording decisions | Not specified |

---

## Findings in detail

### 1. Missing

### 1.1 The Claude AI integration is not implemented — the stated differentiator

The single largest gap. BRD §2 names "integrated LLM-based clinical decision
support (Claude AI)" as one of three differentiators, §4 places an "Intelligence
Layer" in the architecture, §12 sells it as the paid add-on tier, and PRD §2
specifies the endpoint behaviour as "Trigger Anthropic API".

**What is actually there** (`AiMedicineService.java`, 100 lines):

```java
Set<MedicineSuggestion> suggestions = new LinkedHashSet<>();
suggestions.addAll(keywordMapMatching(text));   // substring match on disease_medicine_map
suggestions.addAll(catalogSearchMatching(text)); // token match on catalog name/generic
```

That is a local keyword matcher. There is:
- **no LLM client on the classpath** — no Anthropic, Claude, OpenAI or
  LangChain dependency in `backend/pom.xml`
- no HTTP client, no prompt construction, and no outbound call in the service
- a **dead configuration block** — `application.yml:60-61` sets
  `anthropic-api-key` and `model: claude-3-5-sonnet-20241022`, and nothing reads them
- a **dead feature flag** — `AiMedicineService.java:26-27` declares
  `@Value("${medos.ai.enabled:false}") private boolean aiEnabled` and the field is
  never read, so the flag cannot disable anything

**One requirement *is* met:** BRD §11 requires suggestions be "hard-bounded to
ONLY return medicines existing in local `medicine_catalog`", and the
implementation only ever returns catalog rows. The safety property the BRD cared
about holds — for the wrong reason.

**Knock-on effects:** BRD §12's "per-query cost pass-through" monetisation has
nothing to meter. BRD §9's "AI Medicine Suggestion acceptance rate" KPI has no
event to record. `encounters.ai_note` is never written by any code path — the
column exists and the mapper reads it, but nothing sets it, so PRD §1's
`MUTABLE_UNTIL_SIGNED` rule is also unimplemented.

### 1.2 No Reports or analytics page

PRD §3 specifies `<Reports>` — "Read-only analytic charts using Recharts mapped
array" — in the render tree. There is **no `/reports` route** (`App.jsx:32-51`),
no Reports page, and **`recharts` is a dependency imported by zero files**. It is
a dead dependency alongside a missing feature.

This matters beyond the missing page: BRD §9 defines the product's success
metrics (reduction in unbilled revenue, expired write-offs, patient wait time,
DAU/MAU, task completion time), and the Reports page was the only place any of it
could be observed. **As built, the product cannot measure its own stated KPIs**,
and there is no analytics or telemetry layer to do it from.

### 1.3 Appointments — the documented flow cannot be completed

BRD §8 step 1 is "Registers Patient (acquires DPDP consent) ➔ **Books
Appointment**" and step 2 is "Nurse ... **Views incoming Appointment** ➔ Modifies
status to 'Checked-in'".

The `Appointment` entity and `appointments` table exist and are tenant-scoped,
but there is **no `AppointmentController` and no frontend route or API client**
for them. The only frontend reference to the word is a string in `Dashboard.jsx`.
There is no appointment or "checked-in" status anywhere.

So the BRD's five-step scenario breaks at step 1: a receptionist can register a
patient but cannot book them in, and a nurse has no worklist to work from.
Tracked as #50.

### 1.4 Lab orders — entity with no interface

BRD §5 lists "Orders" among OPD core features. `LabOrder.java` and the
`lab_orders` table exist, and `lab_orders` is correctly in the inspector's
tenant-scoped table list — but **no controller references `LabOrder`** and there
is no frontend API or UI. An entity, a table and a tenant predicate with nothing
using them.

### 1.5 Accounts-receivable ledger

BRD §5 Financial Billing expects OUT: "GST-compliant Invoices, **Accounts
Receivable Ledgers**". Invoices are generated and per-patient outstanding is
shown in `Billing.jsx` and `Patients.jsx`, but there is **no receivables
reporting** — no cross-patient view of who owes what, no ageing, no collection
follow-up. Outstanding is displayed per patient, which is a different thing.

### 1.6 Monopoly and packaging

BRD §12 is a business model with three sellable tiers: base instance with **5
users**, an **AI add-on**, and a maintenance **SLA**. None is implemented:
no seat limit is enforced anywhere, the AI feature flag is dead (§1.1), and there
is no tier or entitlement concept to attach a paid add-on to. Whatever the
commercial intent, the product cannot currently express it.

### 1.7 Non-functional requirements are unverified

BRD §10 sets: initial app mount < 1.5s, API responses < 200ms excluding LLM
calls. **There is no performance measurement anywhere** — no perf test, no
bundle budget, no web-vitals, no load-time assertion in CI or Playwright. The
requirement is neither met nor violated as far as anyone can tell; it is simply
unmeasured. Note this is now *more* exposed than when written, since the
spec excluded LLM latency and the LLM does not exist.

### 1.8 Smaller gaps

- **Role management** (BRD §5 Front Office): no user management at all. Tracked
  as #55.
- **Pharmacy ledger drilldown** (PRD §3: "data grid with expandable rows to
  `transaction_history`"): transactions render as a flat table
  (`Pharmacy.jsx:242-250`), not expandable per item.
- **Discharge summary** (BRD §5 IPD): `discharge_diagnosis` exists as a column
  and a textarea (`Admissions.jsx:208`), which is a diagnosis field rather than
  a discharge summary document. Partial.

---

## 2. Diverged from the spec — and deliberately

| Spec | Implementation | Why the code is right |
|---|---|---|
| SQLite single file, ~5GB ceiling (PRD §7, BRD §7) | PostgreSQL | BRD §11 itself lists "SQLite Data Locking under hyper-concurrency" as a technical risk, mitigated by "migrate strictly to PG on scale". Done preemptively. ADR-0001. |
| Single-tenant, "architecture allows migration to multi-tenant" (BRD §10) | Multi-tenant with row-level isolation | Migration already executed. ADR-0002. |
| `localStorage` token (PRD §4) | `sessionStorage` | Reduces token exfiltration window from XSS. |
| Anonymous signup implied | `permitAll` onboarding endpoint | **This one is a defect, not a feature** — tracked as #51. |
| Dark luxury glassmorphism, `#06060b`, backdrop-filter, hover-lift (PRD §6, BRD §6) | Light clinical theme, dark navigation frame | **BRD §13 names this exact risk as an invalidation trigger**: "User feedback consistently reports the 'Dark Mode' severely limits visibility in bright clinic environments (forcing a light-mode refactor)." The refactor happened early, deliberately. |
| `Context`-based state, `can()` middleware | Zustand, `@PreAuthorize` | Equivalent, with a 22-case RBAC matrix test. |
| 19 tables (PRD §1) | 23 tables | Tenant tables, `tenant_configs`, blind index, idempotency and audit structures. |

The glassmorphism removal is the one divergence a stakeholder is most likely to
notice, and it is the one with the clearest documentary justification.

---

## 3. Where the implementation is better than the spec

The specs contain three latent defects that the implementation fixed. Worth
recording, because a future reader comparing against the PRD might otherwise
"restore" them.

**3.1 UHID generation.** PRD §1 specifies `VARCHAR UNIQUE, AUTO-INCREMENT VIA
MAX()`. `SELECT MAX()` is a race: two concurrent registrations read the same
maximum and one insert fails. Issues #19/#20/#22/#23 were filed and fixed for
exactly this. The implementation uses a Postgres sequence instead
(`uhid_seq`, `V1__initial_schema.sql:21`, consumed via `getNextUhidSeq`).

**3.2 Balance formula.** PRD §5.1 defines
`outstanding = SUM(invoices.total_amount) - SUM(payments WHERE status='success')`.
`PatientBalanceService` uses
`MAX(0, SUM(charges WHERE status IN billed|paid) - SUM(payments WHERE status=success))`
— measured from charges rather than invoices, and floored at zero. Both changes
are improvements: a charge is the right basis for unbilled liability, and the
floor prevents a negative balance arising from a data correction.

**3.3 Concurrency safety.** The spec assumes synchronous single-node execution
(BRD §7). The implementation adds optimistic locking (`@Version` on `Invoice`),
pessimistic row locks for stock and payments, and idempotency keys on every
financial POST. This is what caught the partial-dispense bug (#32) and the
double-spend race (#43) that the spec's model would not have caught at all.

---

## 4. Achieved beyond the specification

None of the following is requested anywhere in the BRD or PRD. All of it exists.

**Security and compliance**
- PII encryption at rest (AES-256-GCM) for patient demographics and clinical
  notes. The spec only asked for bcrypt on passwords.
- **Keyed blind-index search**, so encrypted names remain searchable without
  decrypting or weakening them. Spec had no notion of this problem.
- Per-tenant isolation enforced by a SQL-rewriting statement inspector covering
  all 14 tenant-owned domain tables, with `TenantIsolationTest`.
- Idempotency keys on invoice, payment and dispense POSTs.
- Redis-backed login rate limiting (the spec assumed in-process limiting).
- WebSocket token enforcement with origin restriction.
- Security headers, CORS restriction, standardized error contract.
- Audit logging on financial and identity events.
- Six ADRs recording decisions the spec never made explicit.

**Product surface**
- **Self-serve tenant onboarding** — the spec assumed a single pre-provisioned
  instance.
- Real-time notifications (entity, entity + WebSocket/STOMP), absent from both
  documents.
- Keyed pagination, search debounce correctness, and a doctor-name/medicine-name
  display layer that removes raw UUIDs from clinical screens.
- Modular service structure (`modules/billing`, `modules/pharmacy`,
  `modules/clinical`).

**Quality**
- **WCAG 2.2 AA** colour contrast, enforced by a token system with documented
  ratios and a `check:contrast` script. **The BRD and PRD contain no
  accessibility requirement at all** — this was added, and it is the single
  clearest case of the product exceeding its own specification.
- Skip link, focus-visible states, live regions for async errors, and keyboard
  focus tests in the Playwright suite.
- 93 backend tests, 35 frontend tests, an API flow matrix (31 checks), a
  browser journey test, and a 3-job CI pipeline with branch protection.
- A design-token layer with a no-hardcoded-hex rule, replacing ad-hoc inline
  styles.

**Privacy advantage:** because no LLM is called, **no patient data leaves the
instance**. BRD §14 Assumption 4 requires "patient identifiers are sanitized
locally before being pushed to external LLMs" — the risk is currently moot
because nothing is pushed. This should not be read as credit for §1.1; it is a
side effect of the feature being absent, and implementing the LLM will make
sanitisation a live requirement.

---

## 5. Recommended sequence

1. **Decide the specs' fate.** Rewrite or retire them. They currently assert a
   stack and a deployment model the project abandoned, and their
   `VERIFIED_DEPLOYMENT` status is misleading. Every future reader is misled by
   them, including this analysis.
2. **Resolve the AI question.** Either build the Claude integration properly —
   which means adding the client, using the dead flag, implementing
   `ai_note` with its `MUTABLE_UNTIL_SIGNED` rule, metering usage for §12, and
   implementing PII sanitisation for §14 — or delete the dead config and stop
   advertising a differentiator that does not exist. The current state, where
   marketing promises it and the code does keyword matching, is the worst of the
   three options.
3. **Build Reports.** It closes PRD §3, and it is the only mechanism for the
   BRD §9 KPIs. `recharts` is already installed and unused.
4. **Decide appointments** (#50) — either schedule it, because BRD §8 cannot
   complete without it, or close it and correct the BRD's user flow.
5. **Add performance measurement** for BRD §10, so the NFR becomes checkable in
   CI.
6. AR ledger, lab orders, and the packaging tiers are lower priority but are all
   explicitly promised in the BRD.
