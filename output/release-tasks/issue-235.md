<!-- medos-release-audit: supplychain -->
## Outcome and priority

**P1 · Suggested accountable role: Platform/security lead**. Release tracker: #219.

The audit reported four high package entries and Node build-version drift; reachability must be assessed rather than treating every package alert as an exploited SPA vulnerability.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R13; §3**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Rerun dependency/container/secret/static scans against current main and record advisory IDs, affected paths and runtime reachability.
2. Upgrade compatible supported dependencies and align frontend build/runtime tooling with a supported Node release consistent with CI.
3. Add appropriate dependency, secret and SAST gates; preserve existing container scanning and migration/secrets checks.
4. Review every suppression including .trivyignore with reason, owner and expiry; do not silence unexplained critical findings.
5. Run focused regression checks then required build/tests; document genuinely non-applicable advisories with evidence.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Release scans have no unresolved applicable release-blocking finding.
- [ ] Supported toolchain is reproducible locally/in CI/container builds.
- [ ] Suppressions are bounded and attributable; scanner jobs demonstrably fail on a safe test fixture.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
