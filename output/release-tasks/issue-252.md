<!-- medos-release-audit: aidrafts -->
## Outcome and priority

**P2 · Suggested accountable role: AI/frontend/backend lead**. Release tracker: #219.

Draft creation, provenance and human review need an end-to-end implementation before any AI feature is advertised.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§8**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Implement the approved gateway and structured draft schema behind a default-off feature flag; keep provider secrets server-side.
2. Show source-linked editable draft and differences with explicit accept/reject actions; never auto-sign or create orders.
3. Persist attributable draft/approval metadata and enforce signed-note immutability; coordinate existing ai_note scope in #65.
4. Implement timeout/cancel/manual fallback and cost/rate limits; test the kill switch during an in-flight request.
5. Run the approved evaluations and a small supervised clinician trial before enabling for an agreed tenant cohort.

## Dependencies and coordination

- #243 — Design a tenant-scoped clinician-reviewed AI documentation gateway
- #250 — Build clinical AI safety evaluations and an enablement gate
- #65

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Unsupported/invalid model responses fail safely and manual documentation still works.
- [ ] Cross-tenant access and prompt injection fixtures fail closed.
- [ ] Signed notes remain immutable; model/prompt/source provenance and clinician approval are attributable.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
