<!-- medos-release-audit: egress -->
## Outcome and priority

**P0 · Suggested accountable role: Release/platform engineer**. Release tracker: #219.

Backend and migrate are attached only to an internal Docker network while production uses external managed PostgreSQL.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R11**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Resolve the complete production Compose configuration and inspect network attachments for migrate, backend, Redis, frontend and Caddy.
2. Provide deliberate outbound connectivity for database clients without publishing backend/database/cache ports.
3. Verify managed-database DNS resolution, TLS certificate validation and least-privilege credentials from the actual migrate/backend containers.
4. Run migration then backend startup against a staging managed database; capture redacted evidence and failure behavior.
5. Document required firewall/egress rules and any host-specific override so deployment is reproducible.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] A clean production-topology deployment migrates and starts without an undocumented host workaround.
- [ ] Database connection uses validated TLS; unavailable DB fails clearly.
- [ ] External port scan/host bindings expose only intended edge services.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
