<!-- medos-release-audit: cashier -->
## Outcome and priority

**P1 · Suggested accountable role: Billing lead + hospital cashier/accounting owner**. Release tracker: #219.

A complete daily-close/refund/credit process is not verified; live collection needs reproducible totals and an approved exception policy.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§7 billing; §9**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Agree supported payment methods, date/time boundaries, opening/closing process and treatment of unbilled items and credits.
2. Reconcile invoices, discounts, tax, partial payments and collections by method against authoritative records.
3. Either implement authorized auditable refund/credit handling or explicitly document and operationally support its exclusion for the pilot; never silently delete payments.
4. Validate GST/invoice/receipt treatment with the hospital accounting owner using #59 and #58 rather than assuming hard-coded rates are correct.
5. Produce a daily close/export and discrepancy resolution workflow with attributable adjustments.

## Dependencies and coordination

- #226 — Calculate discounted invoice balances from authoritative receivables
- #242 — Prove retry and crash safety for stock and financial writes
- #148
- #58
- #59

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] A seeded day including discounted/partial payments closes to exact expected totals by method.
- [ ] Duplicate/retried collection does not increase receipts or balances twice.
- [ ] Cashier signs off receipt, adjustment policy and discrepancy handling.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
