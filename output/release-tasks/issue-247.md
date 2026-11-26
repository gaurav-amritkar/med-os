<!-- medos-release-audit: restore -->
## Outcome and priority

**P1 · Suggested accountable role: Release/database owner**. Release tracker: #219.

Restore, key recovery and rollback were not exercised; an encrypted database backup without recoverable keys is insufficient.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R12; §§9–11**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Inventory database backups, tenant wrapped-key/history records, KEK/blind-index recovery, required config and separately controlled recovery access.
2. Agree RPO/RTO and retention with the hospital; restore an isolated representative encrypted fixture from backup using the documented operator path.
3. Verify patients, encounters, discharge/addenda, lab results, stock and financial totals after restore and application restart.
4. Rehearse a failed release recovery using known image digests and a schema-compatible strategy; prefer a documented forward fix where migrations are irreversible.
5. Capture timings, failures, corrective actions and responder ownership without publishing secrets or patient data.

## Dependencies and coordination

- #225 — Include every encrypted clinical column in DEK rotation verification
- #237 — Encrypt lab result storage with a verified upgrade path
- #239 — Validate Flyway and JDBC through the managed database pooler

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Independent operator restores readable encrypted records using recoverable keys and documented access.
- [ ] Measured RPO/RTO satisfy the agreed pilot targets or block release.
- [ ] Recovery preserves tenant boundaries and reconciled stock/financial totals.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
