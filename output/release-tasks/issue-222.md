<!-- medos-release-audit: associations -->
## Outcome and priority

**P1 · Suggested accountable role: Backend clinical/pharmacy lead**. Release tracker: #219.

Independent patient IDs can associate a prescription, lab order or dispense with another patient within the same hospital.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R03**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Add same-tenant patient A/B mismatch tests in EncounterService, LabOrderService and DispenseService.
2. Derive patient identity from the authorized parent record; if the API retains patientId, reject mismatch before any side effect.
3. Validate parent records, medicines and dependent resources against the acting tenant and required workflow state.
4. Keep validation and related stock/charge/status writes transactional; return a stable validation error without clinical data leakage.
5. Update API contracts and callers so UI-selected patient identity cannot override the parent.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Encounter A/patient B is rejected for prescribing and lab orders.
- [ ] Prescription A/patient B dispense creates no stock transaction, charge, status change or success audit.
- [ ] Foreign-tenant parent/medicine and stale or cancelled parent cases fail; correct associations succeed.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
