<!-- medos-release-audit: printRx -->
## Outcome and priority

**P1 · Suggested accountable role: Clinical owner + frontend/backend lead**. Release tracker: #219.

The clinical pilot needs a usable prescription output matching the reviewed persisted prescription, not fallback UI defaults.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§7 prescription output**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Agree required prescription fields with the pilot clinician: facility, patient/UHID, prescriber, encounter/date, medicine, dose, route if supported, frequency, duration and quantity/instructions.
2. Generate output from the authorized persisted record and clearly distinguish draft from signed/final output.
3. Implement print/download and later retrieval with patient/tenant authorization; avoid new clinical decisions during rendering.
4. Add print styling for agreed paper sizes and multi-page long prescriptions; omit application chrome.
5. Make amendments/reprints identifiable and verify content against the signing review.

## Dependencies and coordination

- #241 — Provide minimum longitudinal clinical history and explicit allergy status
- #153

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Printed output matches persisted signed medicine instructions and quantities exactly.
- [ ] Long names/multiple medicines remain legible without clipping or split critical instructions.
- [ ] Wrong-tenant/unauthorized retrieval fails; draft output cannot be confused with final signed output.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
