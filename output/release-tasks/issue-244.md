<!-- medos-release-audit: a11y -->
## Outcome and priority

**P1 · Suggested accountable role: Frontend accessibility lead + QA**. Release tracker: #219.

Off-screen mobile navigation remains focusable; labels, aria-controls, room actions and layout overflow need repair.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§6 accessibility**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Make closed mobile navigation inert/hidden and implement focus entry/return and Escape behavior for drawers/dialogs.
2. Fix Header aria-controls to reference the actual navigation ID and use native named controls for room-card actions.
3. Associate all enabled forms with labels/descriptions and review validation announcements, tables, dialogs and radio/checkbox behavior.
4. Test keyboard-only and representative screen-reader journeys at 390/768/1440 widths and 200% zoom.
5. Use WCAG 2.2 AA criteria, including applicable 24px target-size rules/exceptions; prefer 44px touch targets where practical without claiming it is universally AA-required.

## Dependencies and coordination

- #233 — Repair sidebar styling, active navigation and actual color contrast
- #240 — Repair registration consent state and labeled field behavior

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] No focus lands in closed off-screen navigation; opening/closing returns focus predictably.
- [ ] Registration, prescribing, dispensing and collection can be completed by keyboard.
- [ ] Screenshots/manual findings show no blocked controls or critical overflow; automated checks are supplemented by manual evidence.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
