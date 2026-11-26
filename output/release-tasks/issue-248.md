<!-- medos-release-audit: operations -->
## Outcome and priority

**P1 · Suggested accountable role: Release operator + hospital support owner**. Release tracker: #219.

Monitoring foundations exist, but actionable alert delivery, capacity and response readiness were not established.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§§9–11**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Map health, availability, error, latency, DB/pool, queue/cache, storage and backup signals to named responders and escalation paths.
2. Trigger synthetic failure conditions in staging and confirm notifications arrive with useful sanitized context.
3. Run representative concurrent hospital workloads on target-like hardware/network against agreed latency/capacity criteria.
4. Create downtime, incident, privacy/security escalation and daily reconciliation runbooks; train the pilot operator.
5. Define monitored pilot hours, observation window, stop criteria and post-incident review responsibilities.

## Dependencies and coordination

- #220 — Freeze pilot scope and enforce excluded modules
- #224 — Remove clinical payloads from routine audits and logs
- #242 — Prove retry and crash safety for stock and financial writes

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Synthetic failures reach the designated responder and recovery clears alerts.
- [ ] Load results meet agreed targets without tenant leakage or financial/stock corruption.
- [ ] Hospital staff can execute downtime/escalation procedures and logs contain no raw clinical text.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
