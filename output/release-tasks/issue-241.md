<!-- medos-release-audit: history -->
## Outcome and priority

**P1 · Suggested accountable role: Clinical product owner + frontend/backend lead**. Release tracker: #219.

A complete prior-history/current-medication/allergy review is not demonstrated; safe OPD use requires explicit patient continuity.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§7 market gaps**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Inventory existing patient history APIs and screens; define the minimum read-only timeline needed for the scoped OPD pilot.
2. Show dated encounters, signed notes, current/recent prescriptions and available lab results with author/source links.
3. Provide explicit allergy states: unknown/not reviewed, documented no known allergy, and positive findings with details and clinician verification.
4. Keep timeline access tenant/role scoped and make missing/unavailable history visible rather than implying none.
5. Integrate this review into prescribing/signing and validate terminology with the pilot clinician.

## Dependencies and coordination

- #231 — Restore patient profiles from routes and maintain patient context
- #222 — Enforce one patient across encounter, prescription, lab and dispense

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Two visits for one patient produce an accurate ordered history without another patient's records.
- [ ] Unknown allergy state cannot be mistaken for a negative finding.
- [ ] Clinical reviewer can confirm identity, relevant history and medicines before signing.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
