<!-- medos-release-audit: replay -->
## Outcome and priority

**P0 · Suggested accountable role: Backend/security lead**. Release tracker: #219.

The inspected filter order reads tenant/principal before authentication and can replay a financial response from platform:anonymous; replay also changes the JSON response shape.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R01**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Reproduce through the real Spring Security chain using SecurityConfig, IdempotencyFilter and IdempotencyService; do not mock away authentication/endpoint authorization.
2. Ensure both authentication and endpoint authorization occur before lookup/replay. Fail closed when an authenticated tenant/principal is required but absent.
3. Bind stored records to tenant, user, operation and a canonical request fingerprint. Reject reuse of a key with a different payload.
4. Preserve original status, content type and response bytes; remove double serialization and avoid caching unsafe/error responses.
5. Handle concurrent requests for the same operation atomically; document in-progress and retry semantics and remove reliance on anonymous financial cache entries.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] No/expired token, other tenant, other user and demoted role cannot obtain the original response.
- [ ] An authorized identical retry returns the original response shape and exactly one financial mutation.
- [ ] Changed payload conflicts and concurrent retries do not double-charge.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.
