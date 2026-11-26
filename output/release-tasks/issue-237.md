<!-- medos-release-audit: labcrypto -->
## Outcome and priority

**P1 · Suggested accountable role: Backend/security lead**. Release tracker: #219.

LabOrder.result is plaintext in the reviewed schema; removing audit copies alone does not protect canonical results.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R06–R07**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Confirm the clinical-data encryption policy and map all result storage, projections, search/export and API consumers.
2. Implement tenant-aware encrypted result storage using the established versioned encryption design.
3. Design an explicit migration/backfill for existing plaintext values; use a new Flyway version and update SHA256SUMS, never edit an applied migration.
4. Include new encrypted fields in the rotation inventory and define rollback/restore and mixed-state handling.
5. Keep authorized result read/write behavior stable and avoid placing plaintext into logs or ordinary audit rows.

## Dependencies and coordination

- #225 — Include every encrypted clinical column in DEK rotation verification
- #224 — Remove clinical payloads from routine audits and logs

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Raw database result values are encrypted after upgrading a populated previous-release fixture.
- [ ] Authorized lab/clinician workflows read results; unauthorized/cross-tenant callers cannot.
- [ ] Rotation, restart, interrupted migration and recovery checks preserve readable results.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
