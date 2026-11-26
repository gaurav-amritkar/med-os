<!-- medos-release-audit: partial -->
## Outcome and priority

**P1 · Suggested accountable role: Pharmacy backend/frontend lead**. Release tracker: #219.

Included prescription/V16 changes do not cap fulfillment by the remaining prescribed quantity; frontend quantities and partial worklist remain incomplete.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R04; §6 pharmacy**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Revalidate the included prescription quantity changes on current main and confirm V16 upgrade coverage; its manifest omission was already corrected and is not a new blocker.
2. Validate positive prescribed quantities and define legacy null-quantity behavior explicitly with the clinical owner.
3. Under the stock/prescription lock, calculate remaining = prescribed − cumulative supplied; reject or explicitly cap over-requests according to the agreed API contract.
4. Persist and return actual supplied/remaining amounts; use actual amounts for stock, charges, status and audit rather than the requested amount.
5. Include partially fulfilled prescriptions in the remaining worklist; preserve FEFO and prohibit expired batches, with attributable performedBy.
6. Update prescribing quantity inputs and the dispense confirmation to show patient/UHID, drug, prescribed, previously supplied, requested, actual and remaining quantities.

## Dependencies and coordination

- #222 — Enforce one patient across encounter, prescription, lab and dispense
- #221 — Authorize idempotency replay and isolate cached responses

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Prescribed 10/already supplied 8/request 10 never supplies more than 2 additional units.
- [ ] Concurrent final fills, insufficient stock, cancellation, expired batches and retries reconcile stock and billing.
- [ ] A partial fill remains visible until completed; UI success states the actual quantity and correct patient.

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

From the readiness review (#134), section 3. Three pharmacy data-integrity gaps that share a service.

## 1. `partially_dispensed` has no code path

The schema declares the status (`V1:185`) and nothing produces it. Dispensing 2 of a 10-quantity prescription marks the whole prescription `dispensed`.

That is wrong in a way that matters clinically: the remaining 8 units are simply lost from the record. The pharmacy believes the prescription is fulfilled.

## 2. `InventoryService.addStock` accepts past expiry dates

Nothing rejects a batch whose expiry is already in the past. The batch enters the catalogue and becomes selectable stock. FEFO orders by expiry, so a past-dated batch is correctly *first* in line — which means the system will preferentially dispense expired stock.

A past expiry should be rejected at the point of entry, not discovered at dispense time.

## 3. `performedBy` is never set on stock transactions

The inventory ledger records movement but not who moved it. For a controlled substance, and for any audit requirement, that is the field that matters most and it is empty.

## Fix

- Implement the partial path: decrement quantity, set `partially_dispensed` while any remains, and only reach `dispensed` at zero
- Reject `expiryDate <= today` in `addStock` with a 400
- Populate `performedBy` from the authenticated principal

## Note

Issue 1 interacts with the dispense race issue — both are in `DispenseService`, and the conditional status update needed for the race is the natural place to enforce the partial transition too.

</details>
