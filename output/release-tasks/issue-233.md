<!-- medos-release-audit: shell -->
## Outcome and priority

**P1 · Suggested accountable role: Frontend design lead**. Release tracker: #219.

Sidebar classes/state do not match stylesheet selectors; teal links on the dark frame measured approximately 2.56–2.69:1.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§6 shell/color scheme**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Align Sidebar markup with CSS selectors and use a consistent aria-current active-route contract.
2. Retain the existing teal/charcoal/white identity; use readable text/icon and focus colors on their actual backgrounds.
3. Audit role destinations, including pharmacy ledger→billing and lab→patients, so visible actions lead to permitted workflows.
4. Use neutral/information colors for ordinary bed occupancy and zero debt; reserve danger for actionable exceptions.
5. Add rendered-state contrast checks/screenshots including hover, active, disabled and focus, not just intended token pairs.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Actual normal-text combinations meet WCAG AA contrast and focus remains visible on dark rail and white header.
- [ ] Each supported role has valid active navigation without inaccessible advertised destinations.
- [ ] 390/768/1440 screenshots show correctly aligned icons, labels and selected states.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
