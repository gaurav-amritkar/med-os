<!-- medos-release-audit: search -->
## Outcome and priority

**P1 · Suggested accountable role: Frontend lead**. Release tracker: #219.

FilterBar supplies a string while Patients.handleSearch reads event.target.value; debounce alone will not fix the immediate callback failure.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§6 patient search**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Repair the FilterBar/Patients callback contract and add a real typing/clear integration test.
2. Inventory the other list search inputs already covered here and use one documented string-based contract.
3. Debounce appropriate remote search, cancel or disregard superseded requests and clean up on unmount.
4. Reset pagination on query/filter changes and explicitly pass cleared filter state rather than stale closure values.
5. Preserve loading/empty/error distinction and test with intentionally reordered responses.

## Dependencies and coordination

- Can start independently after current-main revalidation; integrate with the release tracker.

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Typing and clearing patient search causes no TypeError.
- [ ] A slower previous search cannot overwrite the newest query results.
- [ ] Request count is bounded by debounce behavior and all affected page filters reset predictably.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.

## Historical issue context

The execution plan above supersedes conflicting historical implementation/status assumptions below. This text is retained for traceability; it is not confirmation of current behavior. In particular, do not implement old migration numbers, plaintext clinical audit logging or obsolete missing-feature claims.

<details>
<summary>Previous issue description</summary>

From the readiness review (#134), section 3.

## The defect

Four pages send a request on every keystroke. The worst case is **Pharmacy**, which refetches the entire medicine catalogue per keystroke.

There is also no abort and no sequence guard, so results can arrive out of order: a slow response for "smi" can overwrite the results for "smith", and the list ends up showing the wrong match for what was typed.

## Why it is worse than a performance nit

- It is a self-inflicted load problem on every keystroke of the most-used interaction in a clinical app
- Out-of-order responses are a **correctness** bug — the list does not match the query
- Patient name search is the one place where the blind index does exact matches only, so fragment search decrypts up to 5000 rows per request (separate issue)

## Fix

- One `useDebouncedValue` hook plus an `AbortController` per request
- A sequence guard, or rely on abort alone if the browser cancels reliably in the target browsers
- Server-side pagination for the pharmacy catalogue

## Note

This shares a root cause with the name-search scaling issue. Both are "the search path has no shape limit". Worth considering together.

</details>
