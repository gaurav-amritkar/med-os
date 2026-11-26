<!-- medos-release-audit: scope -->
## Outcome and priority

**P1 · Suggested accountable role: Product + clinical lead + backend lead**. Release tracker: #219.

The audit recommends a controlled front-desk + OPD pilot; broad hospital parity is not release-ready.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§§7,10–11**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Record the hospital, named user cohort, enabled modules/roles, patient-data boundary, pilot hours and accountable release/support owners.
2. Create an explicit included/excluded capability matrix covering pharmacy, laboratory, IPD, billing, AI, notifications and interoperability; resolve contractual requirements before deferring them.
3. Implement or verify server-side module gates and matching navigation/routes. Hiding a menu alone is insufficient; excluded operations must be denied through direct API calls.
4. Define downtime/manual recording, reconciliation after recovery, escalation and pilot stop criteria with the hospital sponsor.
5. Record agreed performance, availability, RPO/RTO and UAT criteria; use measured capacity rather than promising an unsupported launch date.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Each included workflow has a linked acceptance owner and release gate.
- [ ] Excluded UI routes and direct API calls are tested for every role.
- [ ] Clinical and product owners approve the written scope; later scope changes reopen affected gates.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
