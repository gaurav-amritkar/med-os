<!-- medos-release-audit: addenda -->
## Outcome and priority

**P1 · Suggested accountable role: Backend clinical lead**. Release tracker: #219.

The normal discharge sets status discharged, but addendum creation requires admitted, making correction of a signed discharge unavailable.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R09**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Define allowed addendum behavior for a persisted signed/discharged summary with clinical owner review.
2. Adjust service guards to permit authorized corrections after discharge while keeping the original signed content immutable.
3. Store each addendum append-only with tenant, original-summary reference, author and timestamp; enforce role/patient association.
4. Display original and subsequent amendments chronologically and label amended print/export versions.
5. Reject attempts to edit/delete original signed text or another tenant's summary.

## Dependencies and coordination

- #227 — Make discharge assembly safe for optional prescription fields

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Two consecutive addenda after normal discharge succeed for the authorized clinician.
- [ ] Original signed content remains byte-for-byte unchanged and every amendment is attributable.
- [ ] Unauthorized, cross-tenant and missing-summary requests fail without writes.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
