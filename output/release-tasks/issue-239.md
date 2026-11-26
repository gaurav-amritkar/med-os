<!-- medos-release-audit: pooler -->
## Outcome and priority

**P1 · Suggested accountable role: Database/platform engineer**. Release tracker: #219.

Production pooler/session behavior and PostgreSQL-only suites were not exercised locally; H2 tests are insufficient.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R12–R13**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Identify actual direct/session/transaction-pooler connection modes and document separate runtime and migration connection choices.
2. Verify prepared-statement behavior, connection reset/tenant context, Flyway locking and transaction boundaries for the selected mode.
3. Execute all PostgreSQL migration, schema-export and key-lifecycle suites with skipped-test counts checked.
4. Upgrade a populated fixture from the actual previous release through current migrations including V16 when included; preserve checksum immutability.
5. Test concurrent migration startup, failed migration recovery and connection exhaustion; record sanitized configurations.

## Dependencies and coordination

- #229 — Provide controlled managed-PostgreSQL egress for production services
- #230 — Verify managed database Data API exposure and least privilege

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] All required PostgreSQL suites execute with no unexplained skips.
- [ ] Previous-release upgrade preserves quantities, ciphertext and tenant ownership.
- [ ] Migration serialization and runtime pooling work through the same modes used in production.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
