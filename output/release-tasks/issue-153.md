<!-- medos-release-audit: opd -->
## Outcome and priority

**P1 · Suggested accountable role: Clinical frontend/backend lead**. Release tracker: #219.

All three tabs appear active without changing content; medicines can receive fallback dose/frequency and a fixed three-day duration without a complete review.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§6 OPD; §8 unsafe defaults**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Implement real tab state, keyboard behavior and content sections with only the selected tab active.
2. Replace DOM reads with controlled form state and explicit validation for medication, dose, frequency, duration and total quantity.
3. Remove silent clinical defaults; allow clinician editing and clearly flag missing/unknown required instructions.
4. Show patient/UHID, verified allergy/history context, findings, diagnosis and final prescription summary before signing.
5. Enforce signed-record immutability in backend and UI; show save/sign errors and prevent accidental repeated sign actions.
6. Integrate quantity contract from #160 and truthful catalogue terminology from #61.

## Dependencies and coordination

- #241 — Provide minimum longitudinal clinical history and explicit allergy status
- #222 — Enforce one patient across encounter, prescription, lab and dispense
- #160
- #61

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Each tab works and preserves intentional unsaved edits.
- [ ] Clinician reviews and confirms exact persisted instructions; no hidden three-day/fallback prescription is introduced.
- [ ] Failed save/sign is visible; signed encounter cannot be silently modified.

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

From the readiness review (#134), section 4.2. Four verified defects on one page, grouped because they share a file and a test setup.

## 1. Three tabs that render "active" and are not clickable

`Encounters.jsx:304-308` — all three tabs show as active simultaneously and none responds to a click. All content renders at once. This is fake UI: it presents a choice the user cannot make, and it looks like a bug even though it is harmless.

Either make them work or make them headings. Leaving non-functional tabs is the worst of the three.

## 2. Manual prescription form reads the DOM directly

`Encounters.jsx:372-391` uses `document.getElementById` to collect form values instead of controlled state. It breaks on re-render, cannot be validated, and bypasses every other pattern in the app.

Related: #117 is half-fixed — `Users.jsx` has the model field-error pattern (`aria-invalid`, mapped `validationErrors`) while this form has none. Fixing this properly means adopting that pattern.

## 3. Silent failures

Empty `catch {}` blocks on profile and tab loads (`Encounters.jsx:84`, `Billing.jsx:34`, `Patients.jsx:92`), and **no `.catch` at all** in `Admissions.jsx:17-25`. A failed request renders as an empty table, which reads as "no data" rather than "could not load".

The project already has the right answer — `Login.test.jsx`/`client.js` distinguish failure from empty, and `GlobalExceptionHandler` returns a structured error. The UI is discarding it.

## 4. No confirmation for irreversible actions

Encounter sign-off has no confirmation. Role change and access-end in `Users.jsx:127-150` have none either. Signing an encounter is not undoable.

## Fix

1. Make the tabs work, or render them as non-interactive headings.
2. Convert the Rx form to controlled state with the #117 field-error pattern.
3. Surface failures using the existing failure-vs-empty convention.
4. Add a confirmation step for sign-off and role change.

## Test

Playwright covers the first and fourth; the second and third are worth unit tests if the form logic is extracted.

</details>
