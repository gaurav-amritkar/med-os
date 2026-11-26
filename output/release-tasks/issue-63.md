<!-- medos-release-audit: lab -->
## Outcome and priority

**P1 · Suggested accountable role: Lab frontend/backend lead**. Release tracker: #219.

API/UI now exist, superseding the original missing-interface claim. Reviewed failures include flat users/me role parsing, scalar resultModal.id and uppercase conversion of lowercase status enums.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§6 laboratory; R03,R06**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Revalidate implemented controller/UI before coding; use the actual flat current-user response and supported lab role.
2. Use one result-modal state shape and submit the correct order ID; handle missing/stale selection safely.
3. Normalize/validate status filters against the actual enum contract and make Clear apply next state immediately.
4. Test allowed lifecycle transitions, authorization, ordering/result timestamps, cancellation and encounter-signing rules; distinguish late result recording from mutating a signed order.
5. Integrate patient association enforcement and encrypted result/audit handling from linked tasks.
6. Verify doctor ordering, lab worklist priority, result entry and clinician viewing through real API responses; enable lab only after all gates pass.

## Dependencies and coordination

- #222 — Enforce one patient across encounter, prescription, lab and dispense
- #237 — Encrypt lab result storage with a verified upgrade path
- #245 — Complete staff provisioning and credential recovery for pilot roles

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Authorized lab actions render; unauthorized roles cannot call result mutations directly.
- [ ] The selected order alone receives the result and all status filters/clear work.
- [ ] Two-tenant full lifecycle, invalid transitions and signed-record policy pass with readable encrypted results.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.

## Historical issue context

The execution plan above supersedes conflicting historical implementation/status assumptions below. This text is retained for traceability; it is not confirmation of current behavior. In particular, do not implement old migration numbers, plaintext clinical audit logging or obsolete missing-feature claims.

<details>
<summary>Previous issue description</summary>

### Summary

BRD §5 lists "Orders" among the OPD core features, and a fully-designed `lab_orders` table and `LabOrder` entity exist — tenant-scoped, with a priority and a status enum. **No controller references `LabOrder` and there is no UI.** A doctor cannot order a lab test, and a lab cannot record a result.

### Evidence

The entity is complete and correct — this is a missing interface, not a missing design:

- `LabOrder.java` — `id`, `tenantId`, `encounterId`, `patientId`, `doctorId`, `testName`, `testCode`, `priority`, `status`, `result`, `resultAt`, `orderedAt`
- `Priority` = `normal, urgent, stat`
- `Status` = `ordered, collected, in_progress, completed, cancelled`
- `lab_orders` (`V1__initial_schema.sql:164-178`) with CHECK constraints on both enums, and it **is** in the inspector's tenant-scoped table list, so isolation is already handled

But: `grep -rn LabOrder backend/src/main/java/com/medos/controller/` → no match, and there is no `labOrderApi` in `frontend/src/api/index.js` and no lab route in `App.jsx`.

### Requirements

- `LabOrderController` with: create an order from an encounter, list orders for a patient or encounter, update status through the lifecycle, record a result, and cancel.
- **Enforce the state machine.** The enum allows any transition, so `collected -> ordered` and `completed -> collected` must be rejected explicitly, and a result must not be recordable before `collected`. An unvalidated status field on a clinical order is a data-integrity problem, not a cosmetic one.
- Record `resultAt` automatically when a result is written, and `orderedAt` on create — the entity has `@PrePersist` defaults for neither, so confirm they are populated.
- Role split: order for `DOCTOR` (and `ADMIN`), status/result update for a lab role. There is **no `lab` role** today — decide whether to add one or fold it into `NURSE`, and make the decision explicitly rather than defaulting to whoever is convenient.
- OPD UI: order a test from the encounter detail pane alongside prescriptions, and list outstanding orders for the patient.
- A lab worklist view showing `stat` and `urgent` first, with result entry.
- Enforce **PRD §1's rule that an order is not mutable after its parent encounter is signed** — same constraint prescriptions already follow.
- `result` is clinical data. Confirm it is covered by the PII-encryption policy applied to other clinical text, and that it is not returned to roles that should not see it.

### Decisions needed

- **Add a `lab` role or reuse `nurse`?** A dedicated role is more honest for a hospital and matches the BRD's role-based model, but adds a value to the role enum and its DB CHECK constraint in a new `V3__` migration. Recommend a dedicated role.
- Whether a lab result is visible to the patient in this version, or clinician-only.

### Verification

- A doctor orders a test from an encounter; it appears on the lab worklist with the right priority.
- An invalid status transition is rejected with 400, asserted per transition.
- A result cannot be recorded before collection, and `resultAt` is set automatically.
- An order on a signed encounter is refused.
- Tenant isolation: an order from tenant A is invisible to tenant B.
- A non-authorised role cannot record a result.
- The result is stored encrypted if the clinical-PII policy covers it, asserted in a test.

### Notes

- Tracked from `docs/requirements-traceability.md` §1.4. The schema work is already done, so this is API + UI.
- Interacts with billing: a lab test is a chargeable service in most hospitals, so decide here whether ordering a lab posts a charge. BRD §3 states charges are auto-posted whenever a service is created — if labs follow that rule, it must be implemented with the same idempotency discipline as pharmacy dispensing.


</details>
