<!-- medos-release-audit: resilience -->
## Outcome and priority

**P1 · Suggested accountable role: Frontend lead**. Release tracker: #219.

Dashboard and other screens can turn failed requests into reassuring zero/empty states; the existing ErrorBoundary is not mounted at the entrypoint.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§6 error states**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Mount the existing boundary appropriately in main.jsx with a usable recovery path and sanitized diagnostics.
2. Audit dashboard, queue, pharmacy, OPD and enabled screens for catches that replace failures with zeros or empty success.
3. Represent loading, valid empty, unavailable and stale-but-visible data distinctly; show retry and last-updated where useful.
4. Preserve user input on recoverable write failures and prevent duplicate submission while status is uncertain.
5. Exercise real API response shapes and timeout/403/500 paths; avoid showing another tenant/patient's stale data.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] A failed metrics request displays unavailable rather than zero collections/patients.
- [ ] A component exception offers recovery and does not expose PHI in diagnostics.
- [ ] Empty, loading, timeout, permission denied and server-error states are visibly distinct.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
