<!-- medos-release-audit: staff -->
## Outcome and priority

**P1 · Suggested accountable role: Backend/frontend lead**. Release tracker: #219.

Temporary-password indicators do not prove enforced change/recovery, lab role provisioning is missing in the reviewed UI, and production login exposes development instructions.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§6 staff/login; §7 hospital settings**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Map supported roles across backend enum, staff form, authorization and navigation; enable lab provisioning only if included.
2. Verify initial credential issuance, forced temporary-password change, admin reset/account recovery and disabled-account behavior.
3. Ensure reset/change invalidates relevant sessions and uses rate limits, expiry and non-disclosing errors appropriate to the chosen flow.
4. Remove seed/bootstrap instructions and demo-password text from production rendering; provide the agreed account-support path.
5. Document operator steps so staff setup/recovery does not require ad hoc database edits.

## Dependencies and coordination

- #236 — Apply role changes and account revocation to existing JWTs
- #220 — Freeze pilot scope and enforce excluded modules

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] An administrator provisions each enabled role and the user completes enforced initial password change.
- [ ] Expired/reused reset credentials and disabled users cannot regain access.
- [ ] Recovery works in a clean deployment; production login contains no developer setup instructions.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
