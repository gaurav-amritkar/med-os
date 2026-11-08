# MedOS production readiness review

**Decision: NO GO for production or a pilot handling real patient data in the reviewed state.**

Prepared 8 November 2026. Technical inspection and checks performed 6–8 November 2026. Audience: product owner, engineering lead, hospital pilot sponsor and release operator.

MedOS has a useful hospital operations foundation, but release eligibility is blocked by authorization, patient association, billing, clinical workflow and deployment defects. Passing unit tests does not establish that the hospital workflows work safely together. The fastest credible release path is a tightly scoped, supervised front-desk and OPD pilot after the gates below pass. A broad hospital launch covering the entire parity roadmap would substantially increase the work and risk.

The current teal, charcoal and white visual identity is appropriate. Preserve it. Repair the broken navigation styling, patient search, consent controls, clinical review and error states before spending time on a new theme. The current medicine advisor is keyword matching, not a generative AI integration. Do not market it as clinically intelligent prescribing.

## 1 Scope and confidence

Baseline commit: `a391b0fc789e379b9244456b85848ed6f7dbd1f6`. The user explicitly included uncommitted prescription and dispensing changes in release scope. The reviewed working tree adds prescription quantity tracking and V16. On 8 November, further room-creation, Redis-entrypoint and migration-manifest edits were present. They were preserved and inspected; the full test suite was not rerun after those later changes.

Evidence classifications used below:

- **Source verified:** the concrete failure follows from the current code path. This does not claim a live exploit or a production incident.
- **Browser verified:** reproduced in the local current frontend, with synthetic data and intercepted API responses. This validates frontend behavior, not backend persistence or authentication.
- **Check verified:** observed tool or test result, with its date and scope.
- **Release evidence required:** a control or operational property cannot be approved from this checkout alone.

The review covered frontend pages and shared components, API contracts, authentication, tenant context, idempotency, clinical associations, dispensing, discharge, invoicing, encryption lifecycle, migration controls, deployment configuration, CI, UI snapshots and current external product/standards references. Independent backend, visual design and technical frontend assessments informed the findings.

No production database, live patient records or external deployment was accessed. Docker's daemon was unavailable, so a full PostgreSQL/Redis/Caddy stack, actual deployment, disaster recovery and real-stack end-to-end tests were not executed. Browser fixtures use a synthetic hospital and synthetic patient. No claim of complete penetration testing, legal certification, clinical validation or WCAG conformance is made.

All source paths below are repository-relative. Line references identify the inspected version and can move as fixes land.

## 2 Release eligibility by area

| Area | Decision | Reason |
|---|---|---|
| Authentication and authorization | Block | Idempotency can replay before authentication; old token roles survive demotion; alternate patient endpoint bypasses canonical controls |
| Clinical integrity | Block | Patient IDs can disagree across encounter, prescription, lab order and dispense; prescribing review is inadequate |
| Pharmacy | Block | New partial dispensing can exceed prescribed remainder; UI and pending-list behavior need completion |
| Billing | Block | Fully paid discounted invoice can leave phantom patient debt |
| Front desk | Block | Search callback mismatch and treatment-consent update failure |
| Laboratory | Block if enabled | Role response mismatch hides actions; result-save ID mismatch; status filter failure |
| Inpatient discharge | Block if enabled | Optional prescription fields can crash discharge; normal discharge cannot receive an addendum |
| Privacy and encryption | Block | Plaintext clinical audit copies; incomplete encryption rotation inventory |
| Deployment | Block | Caddy self-redirect and internal-only network conflict with external database access |
| UI visual foundation | Retain and repair | Palette and typography are credible; current navigation styling is broken |
| AI differentiation | Not delivered | Local keyword retrieval exists; LLM provider integration and clinical evaluation do not |
| Operations and support | Evidence required | Restore drill, alert delivery, release rollback, load and real-user acceptance unverified |

Severity convention: **P0** is an immediate release stop affecting a security boundary or basic availability. **P1** is a required repair for any release containing the affected workflow. **P2** is a material improvement that may follow a deliberately limited pilot. This is a release prioritization scheme, not a CVSS score.

## 3 Verification results

| Check | Observed result | Interpretation |
|---|---|---|
| Backend `mvn clean test`, 7 Nov | 438 reported, 0 failures, 0 errors, 21 skipped | 417 executed successfully; not 438 executed. Includes prescription changes present then |
| Frontend `npm test`, 7 Nov | 129 passed in 22 files | Does not cover the reproduced page integration failures |
| Frontend `npm run lint`, 7 Nov | Exit success, 3 hook-dependency warnings | Warnings in Patients, Appointments and LabOrders; not warning-free |
| Frontend `npm run build`, 7 Nov | Pass | Main JS 409.61 kB, gzip 121.12 kB; CSS 42.70 kB, gzip 8.23 kB |
| `npm run check:contrast`, 7 Nov | 29 token pairs passed | Does not test all actual combinations; sidebar regression demonstrates this limit |
| `verify-migrations.sh`, 7 Nov | Failed: V16 absent from SHA256SUMS | Historical failure; corrected by subsequent workspace changes |
| `verify-migrations.sh`, 8 Nov | Pass | Current manifest includes V16; do not retain the old missing-manifest blocker |
| `verify-prod-secrets.sh`, 7 Nov | Pass | Verifies mounts, not secret validity or successful production boot |
| Migration and secrets gate self-tests, 6 Nov | 5 passed each | Historical control evidence; not a fresh execution against later edits |
| `npm audit --omit=dev --json`, 7 Nov | 4 high-severity package entries; fixes available | Axios, follow-redirects, react-router, react-router-dom; applicability needs triage |
| Docker stack | Not run | Daemon socket absent |
| Current browser | Selected desktop/mobile screens inspected | Synthetic API fixtures; not a real-stack E2E pass |

Skipped backend checks included PostgreSQL migration suites, schema export and PostgreSQL key-rewrap tests. These must run against PostgreSQL for release; H2 success cannot replace them. The dependency audit's four entries are not four demonstrated exploitable vulnerabilities: several findings concern Node HTTP adapters or React Server Components, while this product is a browser SPA. Update supported dependencies and document reachability rather than either ignoring the scan or exaggerating it.

## 4 Security and data integrity blockers

### R01 P0 Idempotency replay occurs before authentication

**Source verified.** `backend/src/main/java/com/medos/config/SecurityConfig.java:94–97` places IdempotencyFilter before JwtAuthenticationFilter, despite a comment claiming the reverse. `config/IdempotencyFilter.java:78–96` reads tenant/principal before the downstream chain and can return cached results without invoking it. `service/IdempotencyService.java:128–131` maps absent identity to `platform:anonymous`.

A successful request can therefore cache a financial response in an anonymous shared scope. A subsequent caller with the same endpoint and known key can receive that response without the normal JWT and endpoint authorization checks. Random key entropy reduces guessing; it does not restore authorization. Replay also JSON-encodes the already serialized body as a string, changing its response shape.

**Required:** authenticate and authorize before cache replay; reject missing context; bind the key to tenant, user, operation and request fingerprint; preserve original response bytes/status. Moving only after JWT is insufficient if unauthenticated requests can still proceed toward a later authorization filter.

**Acceptance:** use the real security chain. Create a payment as A, then replay with no token, expired token, another tenant, another user, demoted role, changed payload and the original authorized request. Only the authorized equivalent request may replay its record.

### R02 P1 Role demotion does not invalidate existing authority

`security/JwtAuthenticationFilter.java:46–75,117–124` uses the token's role while checking current membership activity. `service/UserService.java:217` changes membership role. A demoted, still-active admin can retain ADMIN authority until the existing token expires, potentially for the configured ten hours.

**Required:** resolve current membership authority on requests or enforce a revocation/token-version mechanism. Test immediate denial of staff-management and other privileged operations using an old token after demotion. Test account disable, tenant disable and role change separately.

### R03 P1 Patient associations can disagree

`service/EncounterService.java:201–222` loads an encounter but copies request.patientId into the prescription. `modules/clinical/service/LabOrderService.java:58–83` similarly accepts independent encounter/patient IDs. `modules/pharmacy/service/DispenseService.java:50–60,96,133–135` checks the requested patient exists, then associates stock and charges with that patient rather than enforcing equality with the prescription.

Tenant isolation does not prevent mixing two patients within the same hospital. This is both a clinical record and financial integrity defect.

**Required:** derive patient identity from the authorized parent record or reject mismatches before writes. Test encounter A/patient B at prescribing, lab ordering and dispensing; assert no stock, charge, status or success-audit side effects. Also test foreign-tenant parents and medicines.

### R04 P1 Partial dispensing is incomplete and can oversupply

The included working changes in `DispenseService.java:60,77,115–124` cap by request and available stock, but not by prescribed quantity minus cumulative dispensed quantity. Prescribed 10, already dispensed 8, requested 10 can deduct and bill 10 more, recording 18. The new quantity in `dto/PrescriptionRequest.java:27–29` has no positive-value constraint. Audit text uses requested quantity even when actual stock-limited fulfillment is lower.

The frontend still submits no prescribed total from its prescribing flow and reports generic dispensing success. The pending prescription query and UI need to include remaining partial fills; do not treat the presence of a new enum transition as an end-to-end feature.

**Required:** enforce remaining quantity under the existing lock; validate positive totals; return actual supplied and remaining quantities; show the patient/drug and actual fulfillment in confirmation; define legacy null-quantity behavior. Test repeated and concurrent partial fills, over-request, insufficient stock, retry, cancellation and charge reconciliation.

### R05 P1 Alternate registration bypasses canonical controls

`controller/PatientOnboardingController.java:20–35` lacks the role guard used by `PatientController.java:24–27`. `service/PatientOnboardingService.java:34–46` saves a consent boolean without the canonical consent event and registration audit produced by `PatientService.java:100–132`.

**Required:** remove the duplicate path or delegate through the same authorization and service contract. Verify nurse/pharmacist denial on both paths, and equivalent actor, notice, purpose and timestamp evidence for authorized registration. Registration without optional consent must remain possible; do not reintroduce the previously corrected consent-as-a-care-precondition behavior.

### R06 P1 Clinical text is duplicated into plaintext audit storage

`modules/clinical/service/AdmissionService.java:152` passes clinicalCourse to AuditLogger; `LabOrderService.java:171–172` passes full result text. `util/AuditLogger.java:40–50` stores values verbatim, and `entity/AuditLog.java:34–38` has no encryption conversion. This duplicates a discharge field whose canonical storage is encrypted. LabOrder.result itself is also plaintext (`entity/LabOrder.java:49–50`).

**Required:** audit identity, operation and record references without clinical payloads; apply an explicit encryption/retention policy to lab data; assess historical audit copies. Sentinel tests must prove clinical text does not appear in audit rows or logs while the canonical clinical record remains readable by authorized users.

### R07 P1 Encryption rotation omits discharge records

`security/EncryptedPiiTables.java:55` inventories patients and encounters, while DischargeSummary and DischargeSummaryAddendum contain encrypted fields. JdbcDekReencryptor uses that registry for rewriting and verification. Rotation can report completion while discharge content remains under a superseded, potentially compromised key.

**Important qualification:** current DekRotationService retains historical keys. Immediate loss of readability is not established. The confirmed problem is incomplete compromise remediation and overstated verification.

**Required:** discover and cover all encrypted entity columns. Seed every encrypted record type, rotate, inspect raw generation prefixes, restart and read back. A deliberately omitted old-generation value must fail verification.

### R08 P1 Discounted invoices leave phantom debt

`modules/billing/service/BillingService.java:74–118` discounts invoices while preserving original charge totals. `PatientBalanceService.java:48–63` sums those charge totals minus payments. A no-tax charge of 100, discount of 20 and full payment of 80 can leave patient outstanding of 20 although the invoice is settled.

**Required:** choose one authoritative receivable calculation and reconcile invoice discounts, GST adjustments, charges and allocations. Cover discounted full payment, partial payment, multiple invoices and supported cancellation/refund behavior. A fully settled account must display zero consistently across billing and patient screens.

### R09 P1 Discharge can fail and corrections are unreachable

`modules/clinical/service/DischargeSummaryAssembler.java:90–91` constructs List.of with nullable frequency/duration before filtering nulls. Valid optional fields can therefore cause a discharge-time NPE. Explicit medication text does not avoid the eagerly evaluated fallback.

Separately, `AdmissionService.java:150,157–162` creates a summary and changes the admission to discharged; `:186–194` requires admitted status before adding an addendum. The normal completed discharge cannot satisfy that condition.

**Required:** null-safe summary construction and an addendum state rule consistent with signed discharge. Test discharge with optional regimen fields absent, and then append two attributed addenda without changing the signed original. Verify one charge, released bed and consistent admission state.

## 5 Deployment and release engineering

### R10 P0 Caddy configuration redirects HTTPS back to itself

`caddy/Caddyfile` places unconditional `redir https://{host}{uri} permanent` inside the hostname site block. Caddy serves that block over HTTPS; the redirect is not limited to HTTP and precedes normal proxy handling. Under standard Caddy behavior it returns the same HTTPS URL, creating a redirect loop. This is configuration/source verification, not an observed production outage.

Remove the unconditional self-redirect and use automatic HTTPS or an explicitly HTTP-only redirect. Validate adapted configuration and run HTTP → HTTPS → successful page/API/health checks with a bounded redirect count. [Caddy redirect behavior](https://caddyserver.com/docs/caddyfile/directives/redir) and [automatic HTTPS](https://caddyserver.com/docs/automatic-https) support this assessment.

### R11 P0 Production database access conflicts with network isolation

`docker-compose.prod.yml:88,153,195–196` attaches migration and backend containers only to `medos-private`, marked internal. They must connect to an external managed PostgreSQL endpoint. On a standard Docker bridge, the internal network does not provide normal external egress. Caddy's attachment to both networks does not make it a database router.

Provide deliberate outbound connectivity for backend and migration jobs while keeping ports unpublished and Redis private. Prove DNS, TLS, database authentication, migration and startup from the actual deployed containers. [Docker Compose network documentation](https://docs.docker.com/reference/compose-file/networks/) defines the isolation setting. Host-specific routing could alter the outcome; no such override was verified.

### R12 P1 Database security and recovery need deployment evidence

The Supabase-compatible deployment uses JDBC and in-house auth. Do not assume application SQL interception protects Supabase Data API access. No evidence was obtained of actual exposed schemas, grants, RLS or disabled Data API. Prove application tables cannot be read or mutated using anonymous or unrelated Data API credentials. Use a least-privileged runtime database role and a separate migration identity where practical. Do not infer that all tables are publicly exposed merely because they are in public: actual grants and platform settings determine reachability. [Supabase API security](https://supabase.com/docs/guides/api/securing-your-api).

The descriptor recommends transaction pooler port 6543. Validate JDBC prepared statements, migration locking and connection semantics against the chosen mode; do not assume a session-based application and Flyway can share any pooler configuration. [Connection guidance](https://supabase.com/docs/guides/database/connecting-to-postgres).

Before real data: restore a representative encrypted backup to an isolated environment using the documented keys, verify clinical/audit records, and measure recovery time and data loss. A backup runbook or provider subscription is not restore evidence. Include KEK, historical DEKs, configuration and key access in the drill.

### R13 P1 Release controls do not exercise the production journey

CI builds/tests, validates Compose and migrations, and scans images. It does not boot the complete production topology and run the hospital journey through Caddy. Consequently configuration and integration failures can coexist with green checks.

Add an ephemeral staging gate using the release image digests, PostgreSQL migrations and Redis, then a real-browser journey. Add dependency and secret scanning and an appropriate source-security scanner. Review `.trivyignore` findings with owners and expiry; an accepted baseline is not a clean vulnerability scan. Verify current branch protection externally rather than copying old roadmap claims.

`frontend/Dockerfile` still builds with Node 20 while CI uses Node 22. Node 20 is now end-of-life; standardize a supported runtime and lockfile-based build. This is a build-chain issue, not a claim Node is the production web server. [Node release status](https://nodejs.org/en/about/previous-releases).

### Later workspace changes inspected on 8 November

V16 now appears in SHA256SUMS and the migration gate passes. Newly added ADMIN room creation sets acting tenant and writes an audit event. It still accepts a persistence Room object directly; review allowed fields, ID handling, rate/number/ward validation and duplicate handling before calling this a finished configuration workflow. No frontend room-management journey was verified. The Redis entrypoint now accepts an environment password fallback; run shell and container checks with file-only, environment-only and missing-secret cases. These later edits are not covered by the 7 November suite result.

## 6 Current UI and accessibility review

### Observed screens and evidence

Current local screenshots are in `output/playwright/release-audit/`: login, patient list, profile opened by click, patient direct URL, registration, laboratory, OPD and mobile patients. They contain synthetic data. Committed `docs/ui-snapshots/` captures were also visually reviewed for dashboard, pharmacy, inpatient and billing composition; their October baseline predates current changes and is not release approval.

| Screen | Finding | Required action |
|---|---|---|
| Shell/navigation | Sidebar emits `rail__link`/`active`; CSS styles `.rail-item`/`aria-current`. Current screenshot shows stacked icons and teal underlined links | Align markup and styles; supply active state and role-consistent links |
| Patient search | FilterBar sends a string; Patients.handleSearch expects event.target.value | Fix callback contract; test real typing, clear and stale-response ordering |
| Registration | Treatment consent update traverses undefined nested state; browser produced TypeError | Use immutable nested updates; test every purpose checkbox and explanation |
| Patient profile | Click can work through row bubbling, but direct URL/refresh does not restore selection | Load patient from route ID; handle loading, failure and not-found |
| Laboratory | Flat `/users/me` response is read as `data.user.role`, hiding authorized actions | Use real response shape and role matrix fixtures |
| Laboratory result | Stores a scalar resultModal but saves resultModal.id | Maintain one state shape; verify correct order ID in request |
| Laboratory filters | Backend uppercases lowercase enum; Clear uses stale state | Normalize enum input; pass next filter explicitly; test all statuses |
| OPD | Three tabs all active without switching behavior; little clinical summary before sign | Implement real review sections and final clinician confirmation |
| Pharmacy | Final dispense dialog lacks patient/drug identification; quantity success is generic | Show patient, UHID, medicine, prescribed/actual/remaining quantity |
| Dashboard | Failed requests become zero statistics and empty notifications | Show unavailable/stale state with retry and last-updated time |
| Queue | Always loads page zero, no page callback; current patient derived from page | Test over 20 tokens and active consultation beyond page one |
| Admissions | Room cards use click-only divs; ordinary occupancy uses danger color | Native named action; separate occupancy from acuity |
| Billing | Zero debt can still appear red; identity/amount review needs hierarchy | Neutral zero, clear patient/invoice/selected amount and payment state |
| Staff | Temporary-password indicator exists; complete recovery/change enforcement unverified; lab role missing from UI role choices | Deliver tested credential lifecycle and supported role provisioning |
| Login | Production JSX contains bootstrap/seed instructions and demo password text | Remove development help in production; provide appropriate account support |

**Browser reproduction:** selecting Treatment raised `Cannot set properties of undefined (setting 'treatment')` in Patients. The browser test timed out after the component error; this is not a backend rejection. The actual entrypoint `frontend/src/main.jsx` does not wrap App with the existing ErrorBoundary component, so merely having that component does not ensure recovery.

**Patient context:** PatientBanner is mounted, but production pages do not set its store. Connect it deliberately, clear it when leaving context/logout, and test switching patients. Display unknown allergy status honestly; absence of an allergy record must not read as a negative allergy finding.

### Color scheme decision

Retain canvas `#F6F8F8`, white surfaces, charcoal frame `#101F26`, ink `#17242C` and teal action `#0B6678`, with IBM Plex Sans and Mono. These are appropriate for dense, prolonged operational work. No evidence supports a costly palette replacement as the release priority.

The existing contrast test passes its selected pairs, but broken navigation applies teal action text to the dark frame. Computed contrast at the gradient endpoints is approximately 2.56–2.69:1, below the normal-text 4.5:1 requirement. Test actual selector combinations, not only intended token pairs. On the white topbar, the frame-colored focus outline also warrants correction/verification. Preserve red for danger and actionable exceptions; use neutral or information tones for normal occupancy and zero balance. Replace hard-coded lavender selections and the Bitcoin invoice glyph with the established system.

### Accessibility and responsive gaps

Registration inputs have visible labels without programmatic association. Add matching IDs/htmlFor and field-level error descriptions. Mobile navigation is moved off-screen but remains focusable; implement inert/hidden behavior, focus entry/return and Escape handling. Correct Header's aria-controls target. Global input dimensions distort the patient radio controls; give radio/checkbox inputs explicit sizing. Restore readable patient identifiers and clinical metadata, preferably 14–16px where space permits.

Use WCAG 2.2 AA as the acceptance target. Its minimum target-size rule is 24 CSS pixels with exceptions; 44px is a useful touch recommendation and enhanced criterion, not a universal AA requirement. Test keyboard-only navigation, screen-reader names, focus visibility, 200% zoom, 390/768/1440 layouts and actual table/modal overflow. [W3C WCAG 2.2](https://www.w3.org/TR/WCAG22/).

Indicative expert technical assessment: accessibility 1/4, performance 2/4, theming 2/4, responsive behavior 2/4, implementation integrity 1/4: **8/20**. This is an inspection score, not a benchmark or certification. Performance remains unmeasured on hospital hardware/network. The source detector returned six stylistic warnings; semantic error/acuity borders were not counted as failures merely because the detector flagged them.

## 7 Market fit and product gaps

Current vendor pages show demand for complete workflow support and connected records, not just dashboard appearance. Bahmni describes clinical records, configurable patient dashboards, prescribing, diagnostics, inpatient care, stock, billing and reporting. Practo Ray emphasizes appointments, records, billing and follow-up. Eka's current ecosystem advertises ambient scribing, pharmacy, labs and messaging integrated into patient workflows. These are vendor capability statements, not independent quality or adoption measurements. [Bahmni features](https://www.bahmni.org/feature-list), [clinical services](https://www.bahmni.org/clinical-services/), [Practo Ray](https://www.practo.com/providers/clinics/ray), [Eka add-ons](https://addons.eka.care/).

**Assessment:** MedOS has an appropriate visual direction but is behind the expected functional completeness of a hospital system. A polished landing page or dark mode would not close that gap. Workflow completion, patient continuity, reliable records, printed outputs and support matter first.

| Capability | Current assessment | Release treatment |
|---|---|---|
| Registration, search, appointments, enquiries | Implemented foundations with integration defects | Repair for front-desk pilot |
| Patient timeline and longitudinal EMR | Partial history/profile; no complete consolidated clinical review demonstrated | Minimum readable history and current medicines before clinical pilot |
| Allergy/contraindication documentation | No complete operational workflow verified | Explicit allergy status and clinician verification before prescribing use |
| Prescription review and output | Direct defaults; no verified complete editable/printable prescription journey | Add dose/frequency/duration/quantity review and approved output |
| Pharmacy partial fill, returns, expiry, reconciliation | FEFO and ledger foundations; partial-fill defects | Complete if pharmacy in pilot; defer broader POS/returns only by scope agreement |
| Billing receipts, reconciliation, refunds | Ledger exists; discounted balance defect; complete cashier close/refund journey unverified | Reconciliation and usable receipt required for live collection |
| Lab service | Implemented but broken actions/filter/result path | Repair fully or disable UI and API for excluded pilot scope |
| Inpatient/nursing/MAR | Admission/discharge foundation; broader nursing workflow not established | Exclude IPD from first pilot unless charting and discharge gates pass |
| Reports and audit UI | Infrastructure/audit records exist; operational reporting incomplete | Minimum daily activity, collections and audit retrieval for pilot |
| Hospital settings and staff lifecycle | Some APIs; new room creation; incomplete admin journeys | Operational setup and recovery must work without ad hoc database edits |
| Patient communications | No complete delivery/consent/reminder workflow verified | Prioritize after stable appointments; avoid unreliable notification promises |
| ABDM/ABHA and FHIR | Not implemented as an end-to-end integration | Separate funded integration; required sooner if pilot contract mandates it |
| Insurance/TPA, OT, blood bank, payroll, procurement/accounts | Broad parity programme, not completed capabilities | Defer; do not market as delivered |
| Multi-language and patient-facing notices | English-only working UI | Validate pilot staff and patient notice language requirements |
| Commercial subscriptions and support | Entitlement/billing/support lifecycle not established | Document manual pilot arrangements; build automation before general sale |

ABDM interoperability requires more than storing an ABHA field: identities, consent exchange, resource mapping, sandbox certification and operational handling need a dedicated scope. Use the applicable published [NRCeS FHIR implementation guide](https://nrces.in/ndhm/fhir/r4/2.0/) as a starting point and confirm the required current version with the integration programme before implementation.

## 8 AI assessment and recommended integration

`backend/src/main/java/com/medos/service/AiMedicineService.java` performs substring matching against disease mappings and token-based catalogue searches. Scores are fixed heuristics such as 50 and 100 minus priority; they are not calibrated clinical confidence. The `aiEnabled` field is injected but not used to gate this matching. No model-backed reasoning, patient-aware contraindication evaluation or validated dose engine was established.

`frontend/src/pages/Encounters.jsx` presents AI suggestions and can prescribe with fallback dosage/frequency and a fixed three-day duration. That workflow needs clinician editing and review regardless of whether an LLM is later added. Rename current functionality to catalogue/keyword suggestions, show its matching reason and avoid implying confidence in treatment suitability.

**First AI release recommendation:** clinician-reviewed documentation drafts, after core record integrity is repaired. Draft structured notes or discharge text from authorized encounter data; preserve source provenance and require a clinician to approve before signing. Ambient audio introduces recording, consent, retention, language and transcription risks; add it as a separate evaluated feature, not a shortcut to launch.

Required design before enabling a model:

1. Tenant-scoped server gateway; provider keys stay server-side; authorization precedes all retrieval.
2. Explicit allowed purposes, minimum patient context and approved provider processing/retention terms.
3. Structured draft schema with source links; no invented facts, orders or automatic prescribing.
4. Human review with editable diff, accepted/rejected state, author, timestamp and model/prompt version.
5. Input/output limits, timeout, rate and cost budgets, cancellation, fallback to manual work and a kill switch.
6. Evaluation set covering clinical omissions, negation, numerical errors, mixed-language input, unsupported claims, prompt injection and cross-tenant access attempts.
7. Audits that record provenance and decisions without copying raw PHI into ordinary logs.
8. Monitoring for failure, clinician correction rate, latency, cost and safety incidents, with a rollback owner.

Use care-specific evaluation criteria approved by the clinical lead; generic chatbot accuracy does not establish clinical suitability. NHS guidance is a useful operational reference for clinician review and information governance, not Indian law or certification. [NHS ambient scribing guidance](https://www.england.nhs.uk/long-read/guidance-on-the-use-of-ai-enabled-ambient-scribing-products-in-health-and-care-settings/).

Do not block a useful non-AI pilot on adding an LLM. Do block misleading AI claims and unsafe prescription defaults.

## 9 Privacy and operational obligations

Create a data inventory covering demographics, clinical text, prescriptions, laboratory results, audit copies, exports, backups and future AI/audio data. Identify each purpose, access group, retention period, deletion/legal-hold rule and processor. Establish a patient request process for access/correction/grievance and applicable erasure handling, with identity verification and audit evidence. A checkbox alone does not implement these workflows.

Verify applicable Indian requirements with the hospital's legal/compliance owner, including notice language, processing grounds, children's data, breach response, record retention and vendor contracts. Avoid copying incorrect legal citations from stale project documents. DPDP implementation is staged: the official commencement notification specifies immediate, one-year and eighteen-month groups. Do not describe every obligation as already fully effective on this review date. Security and trustworthy clinical records remain product release requirements regardless of phased statutory dates. [Official commencement notification](https://www.meity.gov.in/static/uploads/2025/11/c56ceae6c383460ca69577428d36828b.pdf).

GST, invoice numbering, tax treatment, receipt content and credit/refund handling require validation against the hospital's actual services and accounting process. Hard-coded rates and labels are not proof of statutory correctness.

## 10 Fastest credible release plan

Freeze feature expansion and designate a single release candidate. The stages below are gates, not promised calendar dates; team capacity, deployment access and clinical acceptance are not established enough for an honest fixed launch estimate.

| Stage | Work | Accountable role | Exit evidence |
|---|---|---|---|
| A Scope and safety | Define pilot modules, hospital, users, data boundaries and downtime procedure; remove misleading AI claims | Product + clinical lead | Written pilot scope; excluded modules unavailable at UI and API |
| B Security and integrity | R01–R08; prescribing review; current quantity behavior | Backend lead + reviewer | Targeted security chain, mismatch, concurrency and reconciliation tests |
| C User workflow repair | Search, consent, route restoration, lab if included, queue, identity, error states, sidebar | Frontend lead + QA | Real browser journeys and current screenshots at target widths |
| D Production staging | Caddy, network egress, database security, migrations, supported builds, secrets and alert wiring | Release operator | Clean deploy through actual edge; PostgreSQL tests; vulnerability disposition |
| E Operational rehearsal | Backup/restore, key recovery, rollback, support and role training | Operator + hospital sponsor | Timed drill and signed UAT, reconciled synthetic day of work |
| F Controlled pilot | Small named cohort, monitored hours, daily reconciliation and incident escalation | Product + hospital | Agreed observation window without unresolved critical defects |
| G General release | Expand only proven modules, support/SLA and commercial lifecycle | Release owner | Written go/no-go against all required gates |

Parallelize security/backend repair, frontend workflows and deployment work if staffing permits. Keep one integrated acceptance environment so repairs are tested together. Defer broad parity modules, dark mode, cosmetic redesign, autonomous AI and marketing polish until the core journey is dependable.

## 11 Required acceptance evidence

- Real filter-chain authentication/authorization tests for cache replay, revoked/demoted users and tenant boundaries.
- Two-tenant, multi-role journeys covering registration → appointment/check-in → encounter → reviewed prescription → dispense → invoice/payment, with IDs and amounts reconciled at each step.
- Wrong-patient and foreign-tenant payloads rejected without partial writes.
- Concurrent stock/bed/financial operations, retry after timeout and Redis restart/crash-window behavior. Redis-only idempotency must not silently permit duplicate financial effects after loss of its cache.
- Every enabled screen tested with real API shapes, zero data, populated data, >one page, timeout, 403 and 500; failures never appear as reassuring zeros.
- Browser reload/back/new-tab patient continuity and keyboard-only use; current desktop/tablet/mobile screenshots and field names checked.
- Complete PostgreSQL migration and key-lifecycle suites, including new encrypted discharge records and V16 upgrade from the actual previous release.
- Production URL resolves, certificates validate, redirects terminate, API works through Caddy and no internal services are publicly published.
- Data API exposure and runtime database privileges checked on the actual managed project.
- Restore and rollback rehearsed; encrypted backups readable using separately recoverable keys; RPO/RTO measured and accepted.
- Alerts delivered to a named responder; synthetic failures verified; no raw patient text in routine logs.
- Clinical and cashier UAT sign-off on identity, prescriptions, outputs, consent, balances and downtime procedures.

## 12 Existing strengths and documentation corrections

Preserve per-tenant envelope encryption, versioned ciphertext, key-history retention, fail-closed secret validation, tenant scoping guards, active-tenant checks, DTO-based current-user responses, transaction/locking foundations, migration immutability controls, non-root containers, TLS architecture, structured logs/metrics and shared UI components. These are real investments even though integration defects remain.

Do not repeat obsolete claims that observability is absent, tenant scoping is SELECT-only, `/users/me` exposes the password hash, Add Medicine is missing, or registration always requires consent. Current code supersedes those historical statements. Conversely, do not accept the roadmap statement that all production blockers are closed as evidence of release eligibility.

Update PRODUCT, project snapshot, requirements traceability and UI snapshot index against one release SHA. Regenerate screenshots only after workflow fixes, using representative synthetic datasets and clear capture dates. Keep a single release checklist whose entries link to executable checks or actual deployment evidence.

## 13 Final release recommendation

Do not deploy the reviewed candidate for real patient operations. Repair the security boundary, patient association, financial and critical UI defects first, then validate a narrowly defined pilot through the production topology. Keep the visual identity, improve clinical review and truthful system state, and defer broad feature expansion. Reassess readiness on a frozen candidate with the evidence listed above; a unit-test pass and a polished screen are insufficient release approval.

Questions skipped: the requested deliverable is an actionable audit. Product choices that remain open are recorded as release decisions rather than delaying the report.
