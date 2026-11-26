<!-- medos-release-audit: privacy -->
## Outcome and priority

**P1 · Suggested accountable role: Hospital privacy/legal owner + engineering**. Release tracker: #219.

A checkbox does not cover notices, purpose inventory, patient rights, retention, processors or breach handling; legal commencement is phased.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§9**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Inventory demographics, clinical text, results, audit copies, exports, backups and future AI/audio data with purpose, access group, processor and retention owner.
2. Have the hospital legal/compliance owner verify applicable requirements and staged commencement as of launch; correct stale blanket legal claims.
3. Agree notice languages/versions, processing grounds, children's-data handling, grievance and incident/breach escalation.
4. Link implementation to #98 consent ledger, #101 patient requests and #102 retention; verify identity, access/correction and erasure/legal-hold decisions without destructive defaults.
5. Review hosting/processor terms, staff access and approved data exports; keep raw clinical data out of issue attachments.
6. Document manual pilot processes where automation is deferred and obtain named ownership/sign-off.

## Dependencies and coordination

- #220 — Freeze pilot scope and enforce excluded modules
- #224 — Remove clinical payloads from routine audits and logs

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Each pilot data category has an approved purpose/access/retention and processor record.
- [ ] A synthetic patient request and consent change can be handled with attributable evidence.
- [ ] Notices, language requirements and incident process are approved; deferred automation has an executable manual process.

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

## Goal
Strengthen DPDP alignment before government/enterprise deployments.

## Scope
- Full consent lifecycle: record, version, revoke; enforce consent checks wherever patient data is touched.
- Add data_principal_request endpoints (export / delete) honoring patient rights; pseudonymize instead of hard-delete medical records.
- Define retention windows per entity + archival job with audit trail.
- Clinical safety: AI advisor stays decision-support-only; add visible non-prescriptive disclaimer and doctor sign-off requirement.

## Acceptance Criteria
- Consent lifecycle implemented and testable.
- Data-export/delete endpoint exists with documented behavior.
- Retention policy documented and enforced by a scheduled job.

</details>
