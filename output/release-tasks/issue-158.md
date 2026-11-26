<!-- medos-release-audit: ci -->
## Outcome and priority

**P1 · Suggested accountable role: Platform + QA lead**. Release tracker: #219.

Current jobs do not prove the full production stack works through Caddy; local browser fixtures and skipped PostgreSQL tests are insufficient.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **R13; §11**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Build a reproducible isolated stack with PostgreSQL, Redis, migrate, backend, frontend and Caddy using candidate images/digests.
2. Seed synthetic two-tenant users/records and run critical real-browser workflows through the edge.
3. Run actual rendered contrast/accessibility checks alongside backend/frontend tests and all required PostgreSQL suites.
4. Fail on missing/failed migration, service health, unexplained required-test skips and workflow assertions; retain sanitized logs/screenshots/test reports.
5. Verify current branch-protection/ruleset configuration requires intended jobs and record their exact names.
6. Document local reproduction and safe cleanup so failure evidence is actionable.

## Dependencies and coordination

- #228 — Remove Caddy HTTPS self-redirect and prove edge routing
- #229 — Provide controlled managed-PostgreSQL egress for production services
- #239 — Validate Flyway and JDBC through the managed database pooler
- #235 — Resolve dependency findings and gate supported release builds

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] A deliberately broken edge route or API contract makes the workflow fail.
- [ ] Green execution proves migrations/startup and critical two-tenant journeys with real services.
- [ ] Artifacts identify the tested SHA/digests; required checks are enforced, not merely documented.

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

## Contrast check is not wired in

`npm run check:contrast` exists and covers 29 token pairs, and `design-system.test.js` is a real ratchet. **Neither runs in CI.** `ci.yml` does `npm ci` → `npm test` → `npm run lint` → `npm run build`.

So the accessibility gate is advisory. A token change that drops a pair below WCAG contrast merges green and is caught only if a developer remembers to run the command.

This matters more than usual here: the redesign UI issues propose adding new colour pairs, and Direction B explicitly requires keeping a contrast table, `prefers-contrast` and forced-colors blocks in lockstep. That is exactly the kind of change this gate exists to catch.

## No E2E in CI

`e2e/smoke.spec.js`, `e2e/full.spec.js` and the `probe:flows` script exist and run locally. Nothing runs them in CI, and they require a live stack plus `E2E_PASSWORD`.

## Fix

- Wire `check:contrast` into the frontend CI job — no Docker needed, it is a script
- E2E needs more work: provision the stack in the job, then run against it. Worth its own issue once the container job's compose bring-up is factored into something reusable.

## Note

`full.spec.js` currently contains a **deliberately failing** assertion for #106 that must not be weakened. If E2E is wired into CI, that assertion will fail the build until #106 is fixed — which is correct, but do not be surprised by it.

</details>
