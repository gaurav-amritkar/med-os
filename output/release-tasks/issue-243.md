<!-- medos-release-audit: aidesign -->
## Outcome and priority

**P2 · Suggested accountable role: AI/backend lead + clinical/privacy owners**. Release tracker: #219.

Model-backed functionality is not delivered. First proposed AI scope is reviewed documentation drafts after core integrity fixes, not autonomous prescribing.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§8**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Agree one initial use case and authorized source fields; exclude ambient audio and autonomous orders from this task.
2. Choose provider/model only after capability, cost, processing/retention and applicable privacy review; do not inherit an obsolete hard-coded model from historical requirements.
3. Design a server-side tenant-authorized gateway, minimum-context retrieval and structured output schema with source provenance and explicit unknowns.
4. Specify timeouts, cancellation, input/output limits, rate/cost budgets, manual fallback and a tenant/global kill switch.
5. Define audit metadata for model/prompt version, sources and acceptance/rejection without copying raw PHI into ordinary logs.
6. Write a reviewed design and threat model covering prompt injection, tenant leakage, unsupported claims and failure modes.

## Dependencies and coordination

- #222 — Enforce one patient across encounter, prescription, lab and dispense
- #224 — Remove clinical payloads from routine audits and logs
- #61

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Clinical/privacy owners approve allowed input/output and provider processing terms.
- [ ] Design contains no automatic sign/order/prescription path.
- [ ] Threat model and evaluation contract are sufficient for the independent evaluation task.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
