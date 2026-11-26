<!-- medos-release-audit: queue -->
## Outcome and priority

**P1 · Suggested accountable role: Frontend/backend clinical lead**. Release tracker: #219.

The queue always requests page zero and derives Now serving from the visible page, failing once the active patient is outside it.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§6 queue**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Wire page/size/filter controls to server requests and reset pagination deliberately when filters change.
2. Resolve the active consultation from an authoritative endpoint/query independent of the visible page.
3. Preserve tenant/date/doctor scope consistently for counts, worklist and active token.
4. Handle concurrent status changes and stale page responses; refresh without duplicating token actions.
5. Confirm role navigation exposes queue work only to appropriate staff.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] A fixture with more than 20 tokens supports navigation to every page.
- [ ] Now serving remains correct when the active consultation is on another page.
- [ ] Filter/reset, empty queue, concurrent updates and failed fetches show accurate state.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
