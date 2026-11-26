<!-- medos-release-audit: consentui -->
## Outcome and priority

**P1 · Suggested accountable role: Frontend lead**. Release tracker: #219.

Treatment consent updates traverse undefined nested state and crash the registration page.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§6 registration**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Reproduce every consent checkbox and purpose explanation with the current registration form state.
2. Replace dotted-path mutation with explicit immutable updates matching the nested consent object; keep each purpose independent.
3. Add stable field IDs, htmlFor associations and error descriptions; fix checkbox/radio sizing without changing text-input sizing.
4. Verify submitted payloads and server response/error handling through the canonical registration route.
5. Keep optional consent refusal supported and preserve entered form values after a recoverable failure.

## Dependencies and coordination

- #223 — Unify patient registration authorization and consent evidence

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Treatment, billing and research toggles/explanations update independently without a crash.
- [ ] Keyboard and label clicks operate the correct controls; required errors are announced.
- [ ] Registration succeeds for valid optional-consent refusal and records the exact selected purposes.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
