<!-- medos-release-audit: reliability -->
## Outcome and priority

**P1 · Suggested accountable role: Backend lead + QA**. Release tracker: #219.

Redis-only replay protection may lose deduplication after cache loss; crash windows and concurrent effects need real-stack evidence.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§11 concurrency/retry evidence**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Map transaction and idempotency boundaries for dispense, payment, invoice creation and discharge/bed allocation.
2. Add fault-injection tests for timeout after commit, crash between mutation and cache write, Redis restart/loss and concurrent duplicate submissions.
3. Use durable operation identity/uniqueness or another documented database-backed invariant where cache loss otherwise permits duplicate effects.
4. Verify stock/bed locks, retry response semantics and repair/reconciliation behavior for partially observed operations.
5. Document which outcomes are safe to retry and how operators resolve an uncertain payment without entering it twice.

## Dependencies and coordination

- #221 — Authorize idempotency replay and isolate cached responses
- #222 — Enforce one patient across encounter, prescription, lab and dispense
- #226 — Calculate discounted invoice balances from authoritative receivables
- #160

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Each logical payment/dispense posts exactly once across tested crash/restart windows.
- [ ] Concurrent last-stock and last-bed operations cannot oversell or allocate twice.
- [ ] All quantities, charges and balances reconcile after retries; recovery evidence uses PostgreSQL/Redis.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
