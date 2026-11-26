<!-- medos-release-audit: debt -->
## Outcome and priority

**P1 · Suggested accountable role: Backend billing lead + cashier reviewer**. Release tracker: #219.

A charge of 100 with a 20 discount and payment of 80 can leave phantom debt because the balance uses original charges.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R08**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Write the 100/20/80 regression against BillingService and PatientBalanceService before changing arithmetic.
2. Define billed receivable, unbilled charges, patient credit and outstanding consistently; document where tax and discounts enter the calculation.
3. Use authoritative invoice/payment/adjustment records rather than subtracting payments from undiscounted charges.
4. Cover partial payments and multiple invoices without double-counting; define supported credit/refund behavior with the cashier owner.
5. Integrate corrected arithmetic with durable recalculation/reconciliation in #148 and use the same totals in API, UI and printed outputs.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] 100 gross −20 discount −80 paid yields zero billed outstanding.
- [ ] Multi-invoice, tax rounding, partial payment and supported credit/refund fixtures reconcile exactly.
- [ ] Repeated recomputation produces the same totals and tenant isolation holds.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
