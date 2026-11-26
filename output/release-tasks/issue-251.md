<!-- medos-release-audit: uat -->
## Outcome and priority

**P1 · Suggested accountable role: QA/release owner + clinical and cashier reviewers**. Release tracker: #219.

Unit tests and synthetic frontend fixtures do not establish real production workflow readiness.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§§10–11**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Freeze the candidate SHA/image digests and list every enabled module, role and resolved prerequisite in the release evidence index.
2. Run two-tenant, multi-role real-stack registration→appointment/check-in→encounter→reviewed prescription→dispense if enabled→invoice/payment journeys through Caddy.
3. Reconcile patient/encounter/order IDs, stock movements, invoice totals, payments, consent and audit evidence at each step.
4. Cover zero/populated/multi-page screens, direct links/reload, 403/500/timeouts, wrong-patient payloads and relevant concurrent/retry cases.
5. Attach current desktop/tablet/mobile screenshots and obtain independent clinical/cashier/operator sign-off; record residual risks and explicit go/no-go.
6. Start a small monitored pilot only after required gates pass; expand modules only with new evidence.

## Dependencies and coordination

- #220 — Freeze pilot scope and enforce excluded modules
- #242 — Prove retry and crash safety for stock and financial writes
- #247 — Rehearse encrypted backup restore and release recovery
- #248 — Validate alert delivery, capacity and hospital incident response
- #249 — Reconcile a cashier day and define safe refunds/credits
- #158
- #244 — Complete keyboard, responsive and screen-reader release checks

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Every in-scope P0/P1 task is closed with evidence or an approved module exclusion enforced at UI/API.
- [ ] Real workflows pass on the frozen candidate, not mixed historical commits.
- [ ] Restore, alerts and signed clinical/cashier UAT are linked; any critical failure keeps NO GO.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
