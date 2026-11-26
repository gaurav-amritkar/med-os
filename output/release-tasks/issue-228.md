<!-- medos-release-audit: edge -->
## Outcome and priority

**P0 · Suggested accountable role: Release/platform engineer**. Release tracker: #219.

The reviewed HTTPS site has an unconditional redirect to the same HTTPS URL, potentially preventing application access. This was config-verified, not live-tested.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R10**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Inspect the current Caddyfile and confirm directive ordering using caddy adapt and validate.
2. Remove the self-redirect or restrict redirection to HTTP while retaining intended HTTPS/HSTS behavior.
3. Deploy the production edge topology in an isolated staging environment with an appropriate test hostname/certificate.
4. Verify root, login, static assets, API and health routing through Caddy; exercise HTTPS API POST without redirecting it back to itself.
5. Document the resulting redirect policy and add a regression smoke check to #158.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] HTTP reaches the canonical HTTPS destination in a bounded redirect chain.
- [ ] HTTPS login/API/health terminate correctly with valid certificates and no loop.
- [ ] API method/body are preserved and internal service ports are not publicly exposed.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
