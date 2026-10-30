# Pharmacy partial dispensing design

Issue: [#160](https://github.com/gaurav-amritkar/med-os/issues/160)

## Outcome

Track the prescribed quantity and cumulative dispensed quantity so a pharmacy
can dispense a prescription in multiple transactions without marking it
fulfilled early. Each dispense decrements stock and bills only the quantity
handed out. A prescription stays in the pharmacy queue while any prescribed
quantity remains.

The expired-stock validation and transaction actor attribution portions of
issue #160 were already implemented in PR #211. This design covers the
remaining partial-dispensing behavior.

## Current behavior and constraint

`Prescription` stores dosage, frequency, duration, and instructions, but no
total quantity. `DispenseRequest.quantity` is only the amount being dispensed
in the current transaction. The database declares `partially_dispensed`, but
the dispensing service only accepts `pending` prescriptions and marks the
whole prescription `dispensed` after any successful dispense.

`DispenseService` already locks a prescription row with
`findByIdForUpdate` before checking its status. The partial transition and
remaining-quantity check will use this lock and remain in the same transaction
as stock deductions, stock transactions, prescription updates, billing charges,
and the balance event.

## Data model and migration

Add two integer columns to `prescriptions`:

- `prescribed_quantity`: positive quantity ordered by the prescriber.
- `dispensed_quantity`: cumulative quantity fulfilled so far.

For newly created prescriptions, the API requires `prescribedQuantity > 0` and
the service initializes `dispensedQuantity` to zero. The database enforces
positive prescribed quantities, nonnegative dispensed quantities, and
`dispensed_quantity <= prescribed_quantity` whenever a prescribed quantity is
present.

Both fields remain nullable for legacy records. Existing quantities cannot be
reconstructed reliably from dosage, frequency, or duration text, so the
migration does not invent values. A legacy prescription with unknown quantity
keeps its current one-shot behavior: the pharmacist supplies the dispense
quantity, stock and billing use that amount, and a successful dispense marks
the prescription `dispensed`. Its `dispensed_quantity` can record the actual
one-shot amount, while `prescribed_quantity` remains unknown. Existing
completed rows retain unknown quantities.

The migration is additive and does not rewrite prescription status or existing
dispense history. It adds the new nullable columns and constraints that permit
legacy nulls while enforcing the quantity relationship for new prescriptions.

## Prescription creation

`PrescriptionRequest` gains a required positive `prescribedQuantity`, and
`PrescriptionDTO` exposes both prescribed and dispensed quantities. The
encounter prescribing flow adds an explicit total-quantity input. AI medicine
suggestions may prefill the medicine and dosage fields but cannot create a
prescription until the prescriber supplies its total quantity; no implicit
quantity is selected.

The required request field is a coordinated API change: the frontend and
backend ship together, and clients creating new prescriptions must send the
total quantity. Existing stored prescriptions continue through the legacy
one-shot path described above.

## Dispensing behavior

For quantity-aware prescriptions, the locked service computes
`remaining = prescribedQuantity - dispensedQuantity` and accepts only requests
from one unit through `remaining`. Requests above the remainder fail with a
clear 400 response before any batch is mutated. Existing FEFO availability
validation also completes before stock mutation.

After a successful dispense, the service adds the request quantity to
`dispensedQuantity`:

- If the new cumulative amount is less than `prescribedQuantity`, status becomes
  `partially_dispensed` and the prescription remains available to the pharmacy.
- If the new cumulative amount equals `prescribedQuantity`, status becomes
  `dispensed` and `dispensedAt` is set.

Each successful partial dispense creates stock-out transactions and a pharmacy
charge for only that dispense quantity, then publishes the existing patient
balance event. The audit entry records the amount and resulting progress.

The pharmacy pending list includes both `pending` and
`partially_dispensed` prescriptions. It displays prescribed, dispensed, and
remaining quantities for new prescriptions. Legacy rows display quantity as
unknown. The dispense dialog defaults to the remaining quantity for a
quantity-aware prescription, allows the pharmacist to choose a smaller
positive amount, and sets the maximum to the remaining quantity. Legacy rows
retain the existing quantity entry behavior.

The `PrescriptionService.processPrescription` status-only method has no callers.
Remove it so it cannot mark a prescription dispensed without corresponding
stock and billing records.

## Concurrency and transaction safety

The existing pessimistic prescription row lock serializes attempts against the
same prescription. The service reads the latest dispensed quantity under that
lock, rejects a request that exceeds the current remainder, and commits the
quantity/status update with the stock, billing, event, and audit effects. Two
simultaneous requests therefore cannot each consume the same remaining
quantity. Batch row locks and the existing all-or-nothing stock availability
check remain in place.

## Verification

- Migration tests validate the constraints and prove existing rows with null
  quantities remain readable and dispensable through the one-shot path.
- Service tests cover a partial dispense, a dispense that completes the
  remainder, over-dispense rejection before stock mutation, insufficient stock,
  and the legacy one-shot path.
- A concurrency test proves simultaneous requests cannot exceed the prescribed
  total.
- Controller tests verify request validation and the 400 response for an
  over-dispense.
- Frontend tests cover required quantity on new prescriptions, pharmacy
  remaining-quantity display, partial completion remaining queued, and final
  completion leaving the queue.
- Full backend tests, frontend tests, lint, build, and migration validation
  pass.
