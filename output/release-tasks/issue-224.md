<!-- medos-release-audit: auditphi -->
## Outcome and priority

**P1 · Suggested accountable role: Backend/security + privacy owner**. Release tracker: #219.

AdmissionService and LabOrderService send clinicalCourse/result text to AuditLogger, creating plaintext copies.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R06**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Replace full clinical payloads with record references, operation, actor, timestamp and necessary non-clinical outcome metadata.
2. Inspect success, failure, exception and request logging paths for the same leakage; avoid logging decrypted request/response bodies.
3. Use synthetic sentinel text to inspect persisted audit rows and captured application logs.
4. Inventory historical audit/export/log copies and agree access restrictions, retention and remediation with the privacy owner; preserve required evidence and do not bulk-delete blindly.
5. Document the permitted audit schema and add a regression check for the affected services.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Sentinel clinical text is absent from routine audit rows and logs on success and failure.
- [ ] Authorized clinicians can still read the canonical clinical record.
- [ ] Audit events retain attributable actor, tenant, record and operation evidence.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
