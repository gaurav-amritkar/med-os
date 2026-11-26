<!-- medos-release-audit: roles -->
## Outcome and priority

**P1 · Suggested accountable role: Backend/security lead**. Release tracker: #219.

JwtAuthenticationFilter uses token authority while checking active membership; a demoted user can retain previous privileges until expiry.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R02**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Trace role resolution in JwtAuthenticationFilter and membership updates in UserService; add an old-token demotion regression test.
2. Resolve current authorized membership on requests or implement a checked membership/token version; document cache invalidation and failure behavior.
3. Apply the same rule to role changes, membership removal, user disable and tenant disable; keep tenant switching explicitly scoped.
4. Ensure authorization-dependent idempotency replay uses current authority and does not bypass revocation.

## Dependencies and coordination

- #221 — Authorize idempotency replay and isolate cached responses

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] An old admin token is immediately denied staff-management access after demotion.
- [ ] Disabled user/membership/tenant is denied; legitimate remaining role permissions still work.
- [ ] Cross-tenant memberships cannot supply authority for the acting tenant.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
