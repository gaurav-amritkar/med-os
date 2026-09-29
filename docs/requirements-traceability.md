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

## 1. Missing

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
