<!-- medos-release-audit: tracker-2026-11-08 -->
# Production release execution plan

Source: [8 November production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md). **Audit verdict: NO GO for the reviewed candidate.** This tracker organizes remediation; creating or closing tickets alone does not authorize production release.

**Scope:** 45 executable tasks: 34 new issues and 11 expanded existing issues. Existing discussions are preserved. Each task includes a problem statement, source finding, implementation steps, dependencies, acceptance checks, accountable role and closure evidence.

## Priority and release policy

- **P0:** blocks every production release (security boundary or basic availability).
- **P1:** required for an enabled affected workflow. Exclude optional modules only by a recorded product/clinical decision plus UI/API enforcement.
- **P2:** post-pilot enhancements; the three model-backed AI tasks do not block a non-AI pilot. Truthful current AI labeling does.
- Start with a scoped front-desk + OPD pilot. Included pharmacy, billing, lab and IPD must meet their own gates; do not advertise deferred capabilities as delivered.
- Assign named engineering, clinical, cashier, privacy and operations owners before execution. No unsupported calendar deadline or arbitrary assignee has been invented.

## Execution sequence

1. Freeze scope and candidate policy; start P0 fixes immediately. Security/backend, frontend and deployment work may proceed concurrently when dependencies permit.
2. Resolve data/auth/quantity/receivable invariants, then integrate dependent screens and outputs. Preserve current user changes and verify what has actually reached main.
3. Deploy the production topology; execute PostgreSQL suites, dependency gates, real browser journeys and retry/crash checks.
4. Rehearse encrypted restore, alert response, cashier close and clinical UAT on a frozen SHA/image set.
5. Make a written go/no-go decision with the hospital sponsor. Only then start the named monitored cohort; expand modules with fresh evidence.

## Scope and immediate release stops

- [ ] #220 — **P1** Freeze pilot scope and enforce excluded modules
- [ ] #221 — **P0** Authorize idempotency replay and isolate cached responses
- [ ] #228 — **P0** Remove Caddy HTTPS self-redirect and prove edge routing
- [ ] #229 — **P0** Provide controlled managed-PostgreSQL egress for production services

## Security, privacy and database integrity

- [ ] #236 — **P1** Apply role changes and account revocation to existing JWTs
- [ ] #222 — **P1** Enforce one patient across encounter, prescription, lab and dispense
- [ ] #223 — **P1** Unify patient registration authorization and consent evidence
- [ ] #224 — **P1** Remove clinical payloads from routine audits and logs
- [ ] #225 — **P1** Include every encrypted clinical column in DEK rotation verification
- [ ] #237 — **P1** Encrypt lab result storage with a verified upgrade path
- [ ] #230 — **P1** Verify managed database Data API exposure and least privilege
- [ ] #239 — **P1** Validate Flyway and JDBC through the managed database pooler
- [ ] #17 — **P1** Establish pilot privacy operations and applicable compliance evidence

## Clinical, pharmacy and financial workflows

- [ ] #160 — **P1** Complete safe partial dispensing, remaining quantities and fulfillment UI
- [ ] #226 — **P1** Calculate discounted invoice balances from authoritative receivables
- [ ] #148 — **P1** Make balance recalculation durable and reconcilable
- [ ] #227 — **P1** Make discharge assembly safe for optional prescription fields
- [ ] #238 — **P1** Allow attributable addenda after signed discharge
- [ ] #63 — **P1** Complete and validate the existing laboratory lifecycle
- [ ] #153 — **P1** Complete OPD review tabs and explicit prescription signing
- [ ] #241 — **P1** Provide minimum longitudinal clinical history and explicit allergy status
- [ ] #246 — **P1** Produce reviewed printable prescriptions from signed records
- [ ] #58 — **P1** Deliver retrievable printed receipts matching authoritative collections
- [ ] #249 — **P1** Reconcile a cashier day and define safe refunds/credits
- [ ] #56 — **P1** Finish safe hospital room setup for any enabled IPD pilot

## UI workflow, accessibility and staff readiness

- [ ] #154 — **P1** Repair patient search and guard debounced list requests
- [ ] #240 — **P1** Repair registration consent state and labeled field behavior
- [ ] #231 — **P1** Restore patient profiles from routes and maintain patient context
- [ ] #232 — **P1** Paginate the queue and resolve active consultation independently
- [ ] #233 — **P1** Repair sidebar styling, active navigation and actual color contrast
- [ ] #234 — **P1** Show honest loading/failure states and mount application recovery boundary
- [ ] #244 — **P1** Complete keyboard, responsive and screen-reader release checks
- [ ] #245 — **P1** Complete staff provisioning and credential recovery for pilot roles

## Release engineering and operational acceptance

- [ ] #242 — **P1** Prove retry and crash safety for stock and financial writes
- [ ] #235 — **P1** Resolve dependency findings and gate supported release builds
- [ ] #158 — **P1** Run production-topology and browser release gates in CI
- [ ] #247 — **P1** Rehearse encrypted backup restore and release recovery
- [ ] #248 — **P1** Validate alert delivery, capacity and hospital incident response
- [ ] #62 — **P1** Provide minimum daily operational reports and authorized audit retrieval
- [ ] #251 — **P1** Qualify a frozen release with two-tenant hospital UAT
- [ ] #253 — **P1** Refresh release documentation and screenshots against one candidate

## Truthful current AI and separately gated future AI

- [ ] #61 — **P1** Describe current medicine suggestions honestly and gate future AI
- [ ] #243 — **P2** Design a tenant-scoped clinician-reviewed AI documentation gateway
- [ ] #250 — **P2** Build clinical AI safety evaluations and an enablement gate
- [ ] #252 — **P2** Implement editable AI note drafts with explicit clinician approval

## Existing work to revalidate or retain separately

These are existing backlog items, not newly confirmed defects. Triage them against the selected release SHA and attach evidence before claiming closure:

- Security/data: #142 WebSocket subscription isolation, #147 tenant nullability, #156 CSP/resource limits, #206 rotation/in-flight-write history, #90 legacy ciphertext. In particular, current historical-key retention means #206's old immediate-destruction claim must be revalidated.
- Financial/product: #59 configurable GST and #64 receivables; tax/receipt semantics require the hospital accounting owner's decision. #66 pharmacy ledger drilldown and #161 bounded/search performance may become release requirements based on workload evidence.
- Privacy implementation: #98 consent ledger, #101 rights requests, #102 retention, #103 rotation evidence. Applicable legal timing and manual pilot procedures belong in #17.
- Post-pilot design/analytics: #149/#150 retain and refine the current teal/charcoal identity, #151/#67 dashboard refinement. A palette replacement, dark mode and cosmetic redesign are not the critical path.
- AI note lifecycle: #65 coordinates future drafts. Interoperability #92–#100 is separately scoped; do not build only an ABHA field and claim complete ABDM integration. Recheck contractual requirements before deferral.
- Commercial/market expansion: #60 landing page, #68 entitlements and #49 gateway. Patient communications, multilingual expansion and broader OT/insurance/blood-bank/payroll/procurement parity require separate validated scope before implementation. Pilot notice languages and manual support arrangements are still required by the scope/privacy tasks.

## Final go/no-go evidence

- [ ] Included modules/roles and excluded UI/API behavior approved.
- [ ] All applicable P0/P1 tasks and revalidated existing security findings resolved with linked evidence.
- [ ] Real security-chain and two-tenant patient/clinical/financial journeys pass.
- [ ] Stock, beds, payments and balances reconcile under concurrency/retry and cache/crash failures.
- [ ] Production Caddy/TLS, managed DB access, migrations and actual required CI checks verified.
- [ ] Encrypted restore/key recovery and release recovery rehearsed against agreed RPO/RTO.
- [ ] Alerts reach named responders; performance meets agreed targets; support/downtime procedures rehearsed.
- [ ] Clinical and cashier reviewers approve identity, prescriptions, outputs, consent, collections and adjustments.
- [ ] Candidate SHA/image digests, current screenshots, residual risks and signed go/no-go recorded.

No release date is promised until these gates have evidence. Keep secrets and real patient data out of issues and attachments.
