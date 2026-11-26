<!-- medos-release-audit: aieval -->
## Outcome and priority

**P2 · Suggested accountable role: Clinical lead + AI evaluation engineer**. Release tracker: #219.

Generic chatbot benchmarks cannot establish suitability for clinical documentation.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§8**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Build a versioned synthetic or appropriately authorized evaluation set with reference source facts and expected structured outcomes.
2. Cover omissions, negation, numerical/unit mistakes, unsupported claims, mixed-language input, prompt injection and cross-tenant retrieval attempts.
3. Agree severity-weighted acceptance thresholds with clinical reviewers before choosing a winning model/prompt.
4. Compare candidate versions with a reproducible harness and independent clinical adjudication; retain provenance without exposing PHI.
5. Add regression gating for model/prompt/retrieval changes and define monitoring for correction rate, latency, cost and incidents.

## Dependencies and coordination

- #243 — Design a tenant-scoped clinician-reviewed AI documentation gateway

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Harness detects seeded unsafe omissions/inventions and unauthorized retrieval.
- [ ] Results are reproducible for a named dataset/model/prompt version.
- [ ] No model-backed feature enables until agreed safety thresholds and privacy conditions pass.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
