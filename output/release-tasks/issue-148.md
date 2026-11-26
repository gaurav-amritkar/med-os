<!-- medos-release-audit: balances -->
## Outcome and priority

**P1 · Suggested accountable role: Backend billing lead**. Release tracker: #219.

Existing asynchronous recalculation can lose updates; correct discount arithmetic also needs durable propagation.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R08; §11**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Trace after-commit recalculation failures and add a test where the first recalculation fails after a committed charge/payment.
2. Choose a durable outbox/job mechanism with retry, idempotent processing and bounded operational visibility.
3. Use the corrected receivable rules from the discount task; keep billed outstanding, unbilled amounts and credit semantics explicit.
4. Add scheduled/on-demand reconciliation comparing derived patient totals with authoritative records and reporting discrepancies.
5. Handle duplicate/out-of-order events and tenant-scoped repair; expose backlog/failure signals to operations.

## Dependencies and coordination

- #226 — Calculate discounted invoice balances from authoritative receivables

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] A failure after commit eventually recovers without manual database edits.
- [ ] Duplicate/reordered jobs cannot corrupt the authoritative balance.
- [ ] Reconciliation detects and repairs a seeded stale value and distinguishes credits/unbilled amounts.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.

## Historical issue context

The execution plan above supersedes conflicting historical implementation/status assumptions below. This text is retained for traceability; it is not confirmation of current behavior. In particular, do not implement old migration numbers, plaintext clinical audit logging or obsolete missing-feature claims.

<details>
<summary>Previous issue description</summary>

From the readiness review (#134), finding **D3**.

## The defect

`PatientBalanceService` recalculates `patients.outstanding` via `@Async` + `AFTER_COMMIT` + `REQUIRES_NEW`. There is no retry, no outbox, and no reconciliation job.

An `@Async` call that throws is logged and forgotten. A committed charge whose recalculation failed leaves the balance permanently wrong, with no queue to drain and no reconciliation to repair it.

## Two secondary problems in the same calculation

- **Incomplete scope.** Outstanding counts only `billed|paid` charges. A patient with dispensed-but-unbilled drugs or accrued-but-unbilled room days shows a balance of zero. Reception sees a clean account that is not clean.
- **Overpayments are hidden.** `GREATEST(..., 0)` clamps a credit balance to zero, so a patient who has overpaid looks like they owe nothing rather than showing a credit — which is exactly the state an accounts-receivable workflow needs to see.

The first compounds the DPDP/AR exposure; the second hides good debt. #64 tracks the AR ledger; this is upstream of it.

## Fix

- Move recalculation to an outbox or a durable queue with retry, and add a periodic reconciliation pass that recomputes and compares.
- Count unbilled charges in the outstanding figure, or document deliberately that outstanding means billed-only and surface both.
- Represent credit balances as credit rather than clamping, or clamp only in display with the true value retained.

</details>
