<!-- medos-release-audit: onboarding -->
## Outcome and priority

**P1 · Suggested accountable role: Backend/security lead**. Release tracker: #219.

The alternate PatientOnboarding path bypasses canonical role checks and consent/registration audit events.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R05**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Inventory all registration routes and compare PatientOnboardingController/Service with PatientController/Service.
2. Remove the duplicate write path or delegate through the same guarded canonical service; keep backward compatibility explicit.
3. Record equivalent actor, purpose, notice version and timestamp evidence through the canonical consent mechanism.
4. Preserve registration when optional consent is declined; do not make treatment conditional on unrelated research/billing permissions.
5. Update clients and endpoint documentation and test both routes through the real security chain.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Nurse/pharmacist and unauthenticated callers cannot bypass canonical registration permissions.
- [ ] Authorized registration on each retained route produces equivalent patient, consent and audit records.
- [ ] Optional-consent refusal still permits the supported registration workflow.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
