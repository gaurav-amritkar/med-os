<!-- medos-release-audit: rotation -->
## Outcome and priority

**P1 · Suggested accountable role: Backend/security lead**. Release tracker: #219.

The encryption inventory omits discharge summaries and addenda. Historical keys are retained: the confirmed defect is incomplete rotation, not demonstrated immediate data loss.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R07**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Enumerate every entity column using encryption conversion and compare with EncryptedPiiTables and JdbcDekReencryptor.
2. Add discharge summary/addendum columns and any other confirmed omissions to both rewrite and verification paths.
3. Introduce a coverage guard so a new encrypted column cannot silently be omitted from the rotation registry.
4. Seed all encrypted entity types across multiple tenants/generations, rotate, inspect raw ciphertext prefixes, restart and read back.
5. Review #206 against current key-history behavior separately; retain recoverable keys and do not reintroduce unsafe key destruction.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] All included old-generation values move to the target generation and remain readable after restart.
- [ ] A deliberately missed old-generation value prevents successful verification.
- [ ] Other tenants remain unchanged; interrupted rotation can resume safely.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
