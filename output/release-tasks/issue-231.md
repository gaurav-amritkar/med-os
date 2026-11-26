<!-- medos-release-audit: profile -->
## Outcome and priority

**P1 · Suggested accountable role: Frontend clinical lead**. Release tracker: #219.

Ordinary row clicks may open a profile, but direct URL/reload/new-tab does not restore it; PatientBanner is not populated by production pages.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§6 profile and patient context**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Load the selected patient from the route ID instead of relying on row-bubbling/local selection state.
2. Implement loading, not-found, forbidden, server-error and retry states without briefly displaying a previous patient's details.
3. Connect PatientBanner to explicitly loaded context; display name/UHID and clinically relevant verified context.
4. Clear state on patient/tenant switch, leaving patient context and logout; cancel or disregard stale asynchronous responses.
5. Distinguish unknown allergy status from a documented negative allergy history; link longitudinal context work.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Direct URL, refresh, back/forward and new tab show the correct patient.
- [ ] Rapid A→B navigation never renders A's returned data in B's context.
- [ ] 403/404/500 and logout clear sensitive/stale context without an application crash.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
