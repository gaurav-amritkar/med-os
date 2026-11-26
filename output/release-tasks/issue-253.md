<!-- medos-release-audit: docs -->
## Outcome and priority

**P1 · Suggested accountable role: Engineering/release owner**. Release tracker: #219.

Roadmap and project snapshot contain superseded blocker/status claims; screenshots predate current behavior.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§12**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Review PRODUCT, README, roadmap, requirements traceability, project snapshot, operations and UI snapshot index against the frozen candidate.
2. Correct obsolete claims about password-hash exposure, missing observability, SELECT-only scoping, mandatory consent and missing Add Medicine.
3. Replace blanket 'all blockers closed' statements with linked current tracker/evidence and separate implemented, verified and deferred capabilities.
4. Regenerate representative synthetic screenshots after workflow fixes and record capture date/SHA; no patient data.
5. Document enabled pilot scope, setup/support, recovery, known limitations and truthful AI capability.

## Dependencies and coordination

- #251 — Qualify a frozen release with two-tenant hospital UAT

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Documents agree on candidate SHA, scope and unresolved gates.
- [ ] Screenshots reflect current working routes and target widths.
- [ ] No implementation claim depends only on an old roadmap entry.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
