<!-- medos-release-audit: discharge -->
## Outcome and priority

**P1 · Suggested accountable role: Backend clinical lead**. Release tracker: #219.

DischargeSummaryAssembler uses List.of with nullable frequency/duration; eager fallback assembly can fail even when an explicit summary is supplied.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R09**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Reproduce null frequency/duration and explicit-summary paths with realistic persisted prescriptions.
2. Build optional medication text with null-safe values and compute fallback content only when needed.
3. Validate truly required clinical inputs at the boundary without rejecting supported legacy records.
4. Keep summary persistence, discharge status, bed release and charges atomic and retry-safe.
5. Return an actionable validation failure where a record cannot safely discharge.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Optional/legacy prescription fields do not throw NullPointerException.
- [ ] Explicit summary does not evaluate an unnecessary unsafe fallback.
- [ ] Failure rolls back all discharge effects; successful retry releases one bed and posts charges once.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
