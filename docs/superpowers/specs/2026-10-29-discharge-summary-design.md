# Structured discharge summary design

Issue: [#69](https://github.com/gaurav-amritkar/med-os/issues/69)

## Outcome

Discharging an admitted patient creates and signs a structured, immutable clinical summary in the same transaction that closes the admission, frees the room, and posts room charges. The summary remains readable from the admission history and printable for the patient. Corrections are append-only addenda.

## Chosen design

Store one `discharge_summaries` row per admission and separate `discharge_summary_addenda` rows. The summary is a snapshot: later edits to encounters, prescriptions, medicines, vitals, lab results, or clinician names cannot silently rewrite a signed clinical record. Narrative fields use the same application encryption converter as encounter clinical notes.

The discharge request supplies the admission and discharge diagnoses, procedures, condition at discharge, medications at discharge, investigation notes, follow-up advice, and next review date. The service also aggregates signed and open encounters, vitals, prescriptions, and lab orders created for the patient during the admission window. Empty sections are stored and rendered as `Not recorded`; they are never ambiguous blank strings.

The summary is mandatory. A discharge request without the required discharge diagnosis, condition, or follow-up advice is rejected before any admission, room, charge, or summary state changes.

## Alternatives considered

1. Put additional discharge columns on `admissions`. This keeps the write simple but mixes administrative state with a signed clinical document, makes addenda awkward, and cannot express document identity or signing metadata cleanly.
2. Generate a summary on every read from live clinical rows. This avoids duplicated data but violates immutability because later source changes alter the document the clinician signed.
3. Use a summary snapshot plus append-only addenda. This preserves the signed record, supports corrections, and keeps the discharge transaction atomic. This is the selected approach.

## Data model

`discharge_summaries` is tenant owned and has a composite foreign key to `(tenant_id, admission_id)`. `admission_id` is unique per tenant. It records patient and clinician identifiers, all structured narrative sections, JSON snapshots for source encounters/vitals/medications/investigations, and `signed_at`. Tenant, admission, patient, and signer lookup columns are indexed where needed.

`discharge_summary_addenda` is tenant owned and has a composite foreign key to its summary. Each row records encrypted addendum text, author, and timestamp. No update or delete endpoint is exposed for either signed summaries or addenda.

## API and access

- `PUT /api/v1/admissions/{id}/discharge` accepts the expanded structured request and returns the signed summary together with the discharged admission.
- `GET /api/v1/admissions/{id}/discharge-summary` returns the signed snapshot and addenda.
- `POST /api/v1/admissions/{id}/discharge-summary/addenda` appends a correction.

Doctors and admins may discharge or append an addendum. Doctors, nurses, and admins may read summaries. Existing tenant SQL scoping and composite foreign keys prevent cross-tenant access.

## UI

The discharge modal becomes a structured form. It shows that existing clinical data will be included, requires the three safety-critical fields, and labels optional empty sections as `Not recorded`. After signing, the Admissions page offers a summary viewer with a print action. Print CSS hides application controls and formats the clinical document.

## Error handling and transaction boundaries

The service locks the admission before checking its state. Duplicate or concurrent discharge attempts find either an already discharged admission or an existing unique summary and fail without a second charge. Summary persistence occurs before the administrative state changes and all operations share one transaction, so any failure rolls the entire discharge back.

## Verification

- Service integration tests prove the snapshot aggregates admission-window encounters, prescriptions, vitals, and labs; missing data becomes `Not recorded`; summary signing and discharge are atomic; and a second discharge cannot mutate the signed record or create a second charge.
- Controller access tests prove clinical role gates and tenant scoping.
- Addendum tests prove corrections append and preserve the original summary.
- Frontend tests prove the expanded form payload, mandatory field handling, summary display, and print action.
- Migration verification, the full backend suite, frontend tests, lint, and production build must pass.
