# EGM Contract Drift Remediation Plan

Date: 2026-10-30
Status: Implemented on `codex/egm-contract-drift-remediation` through 885a8c6; subsequent comment-only test documentation is verified
Input: [Drift report](../../../2026-10-30_DRIFT_REPORT.md)

## Objective

Repair the repository contracts so developers can discover every production package, identify its public boundary, and understand persistent writes, shared state, and asynchronous work. Success requires accurate individual contracts and a complete navigation path from the repository blueprint to each documented package.

The report contains 16 findings across 15 documents. They concern stale summaries, missing package navigation, incorrect side-effect descriptions, and obsolete frontend test guidance. These are documentation defects: existing source behavior is the reference for this implementation. Implement through Markdown changes, including three missing intermediate READMEs. A runtime defect discovered during inspection should be recorded separately with evidence rather than silently changing behavior in this documentation repair.

Preserve accurate operational instructions and developer notes. Correct stale claims even when they predate the bootstrap. Avoid replacing entire existing READMEs with generated templates.

## Additional evidence discovered while planning

The drift report is a starting inventory, not proof that all other statements are accurate. Source inspection confirmed related gaps:

- `database/README.md` says V1 created 25 tables. V1 defines 23; later migrations add seven. The checked-in history defines 30 application tables. This does not establish the state of a deployed database or include Flyway metadata.
- Root and database guidance say `TenantKeyHolder.currentGeneration()` always returns 1. It now calls `generationFor(requireTenant(null))`, and `rotateDek` calls `advanceDekGeneration`. Verify the complete operator workflow before describing rotation availability and limits.
- The root says tenant scoping handles SELECT only. `TenantStatementInspector.inspect` dispatches SELECT, UPDATE, DELETE, and INSERT handling. Document supported SQL shapes and limitations without promising universal isolation.
- The root says correlation IDs do not exist. `RequestCorrelationFilter` validates or generates `X-Request-Id`, binds MDC state, sets a response header, and clears MDC state in `finally`. This alone does not establish complete observability coverage.
- The application entry contract denies external effects even though `MedOsApplication.main` starts Spring. Describe startup and its delegation to configured components.
- `PatientBalanceService` has a stale class comment about MANDATORY propagation. The executable method annotation is REQUIRES_NEW. Contract repairs must follow executable configuration; track the source-comment correction separately if the implementation remains strictly Markdown-only.

## Authoring rules

1. Ground behavioral claims in a source file and method, annotation, configuration key, or SQL object. Use relative links to existing source files and name the relevant symbol in prose.
2. Describe public visibility accurately. Qualify nested types such as `Result` and `Status` by owner. Package-private helpers can support evidence without becoming public interfaces.
3. Distinguish immediate writes, event publication, and later consumer writes. Identify affected resources and transaction/execution boundaries.
4. Derive invariants from validation, constraints, guards, and transactions. Generic transaction boilerplate is not a substitute for actual rules.
5. Use “None” only after examining implementation. Writes through an injected repository remain side effects of the caller.
6. Keep parents concise and link detailed child contracts. Grouping directories describe their children without claiming to implement their behavior.
7. State working directories for verification commands consistently. Do not repeat `cd backend` when the reader is already instructed to run inside backend.

## Task 1 — Establish the evidence and coverage inventory

Start with current Git status and the existing 40-contract bootstrap set. Reconcile against the filesystem rather than assuming the checkout is unchanged. Preserve unrelated work.

Enumerate production source directories and their intermediate parents. Exclude test-only, generated, dependency, and non-code asset directories. Map every report finding to its affected contract, source evidence, planned correction, and completion check.

Read each affected package's direct production sources. Identify public types and externally relevant methods; trace calls that write state or publish events. Audit the remaining baseline contracts for the same false-negative side-effect pattern, unsupported claims, and missing child links. Limit this audit to the contract set and corresponding sources rather than redesigning unrelated project documentation.

Deliverable: a finding-to-edit checklist and a complete directory-to-contract map. Every retained package must have a contract or an explicit reason for exclusion.

## Task 2 — Restore navigation, bottom-up

Create these intermediate blueprints:

- `backend/src/main/java/com/medos/modules/appointment/README.md`
- `backend/src/main/java/com/medos/modules/enquiry/README.md`
- `backend/src/main/java/com/medos/modules/payment/README.md`

Give each an overview and a relative link to its existing service contract, plus Interface, Invariants, and Side Effects sections consistent with its grouping role.

Update `backend/src/main/java/com/medos/modules/README.md` to link all six modules: appointment, billing, clinical, enquiry, payment, and pharmacy.

Update `backend/src/main/java/com/medos/README.md` to link all eleven direct child packages: config, controller, dto, entity, exception, mapper, modules, repository, security, service, and util. Document `MedOsApplication.main` startup and its declared caching, async, transaction, and module configuration.

Inspect mixed directories containing both direct code and children, including billing, clinical, and pharmacy module roots. Ensure they link service/event contracts too.

Acceptance: every retained backend package is reachable from the repository blueprint through existing Markdown links. Assuming no further omissions, the baseline becomes 43 contracts.

## Task 3 — Correct foundational package contracts

Paths below are relative to `backend/src/main/java/com/medos/`.

| Contract | Source evidence | Required coverage |
|---|---|---|
| `config/README.md` | Bootstrap/backfill runners, request filters, security/cache/WebSocket configuration | Conditional startup writes, name-index backfill persistence, request/response and context mutations, configured integrations. Distinguish optional runners from unconditional startup work. |
| `security/README.md` | `TenantKeyStoreJdbc`, `JdbcDekReencryptor`, rotation services, `LoginRateLimiter`, JWT filters and key holders | Replace copied repository-interface language with actual JDBC/JPA writes, ciphertext rewrites, rotation audit rows, Redis counters/fallback state, key caches, and request security context effects. |
| `service/README.md` | Patient, encounter, tenant, user, auth, onboarding and idempotency services | Entities persisted, Redis claim/cache/delete operations, and relevant read-only responsibilities. |
| `util/README.md` | `AuditLogger.log`, `logAsync`, encryption, blind-index and serial helpers | Audit persistence and async entry point; verify actual user/request/tenant context availability rather than assuming async propagation. |

Replace copied or generic invariants with a concise set of source-backed rules. Qualify nested public types and distinguish package-private helpers from public API.

Acceptance: no corrected package retains a contradictory denial of side effects; every relevant state-changing call has a truthful package-level description with a source owner.

## Task 4 — Correct workflow writes and event timing

Paths below are relative to `backend/src/main/java/com/medos/modules/`.

| Contract | Required resource and timing coverage |
|---|---|
| `appointment/service/README.md` | Appointment creation, status/note updates and repository writes; booking and transition validation. |
| `enquiry/service/README.md` | Enquiry persistence and status-history rows; implemented numbering/retry and transition rules. |
| `billing/service/README.md` | Invoice/charge changes, sequence allocation where applicable, event publication, and balance-service native UPDATE. |
| `payment/service/README.md` | Payment persistence, invoice changes and event publication; amount/status rules. |
| `clinical/service/README.md` | Admissions, room occupancy, discharge summaries/addenda, charges and events; implemented locking and summary immutability. |
| `pharmacy/service/README.md` | Catalog, batches, stock ledger, prescriptions, charges and events; implemented FEFO, locking, stock and money constraints. |
| `billing/event/README.md` | Event payload, publisher dependencies, AFTER_COMMIT timing, async execution and delegated balance persistence. |

Document this verified execution chain:

`originating service transaction → publish PatientBalanceEvent → successful commit → async PatientBalanceEventListener.handleBalanceUpdate → PatientBalanceService.recalculateBalance → REQUIRES_NEW UPDATE patients.outstanding`.

Do not describe balance recalculation as atomic with the originating write. Inspect error handling and retry configuration before claiming delivery, recovery, or consistency guarantees. An annotation establishes configured behavior; documentation should not claim a live execution was observed.

Acceptance: all seven contracts identify persistent resources, publishers and consumers consistently. Transaction timing agrees across the billing service, event listener and originating workflow contracts.

## Task 5 — Reconcile repository, database and frontend guidance

Edit root `README.md`, `database/README.md`, and `frontend/README.md`; inspect the migration contract for consistency.

1. Correct the root schema heading, architecture table-count comment and table inventory. Cross-check migration CREATE/DROP/RENAME statements before deriving the final schema count. Distinguish application tables, Flyway metadata and deployed state.
2. Correct database guidance to 23 initial tables and seven later additions in the current source history. Describe the latest checked-in version without claiming a live environment has applied it.
3. Add appointment and enquiry to the root architecture module summary.
4. Reconcile generation/rotation claims against `TenantKeyHolder`, `DekRotationService`, `PiiKeyRotationRunner`, feature flags and the existing operations runbook. Distinguish implemented code, enabled operator surface and remaining retirement limitations. Do not invent production commands from one method alone.
5. Correct SELECT-only scoping and absent-correlation-ID claims while retaining evidenced limitations.
6. Replace the frontend expected-failure note with the current password-exclusion contract. Cite `DashboardController.getCurrentUser` and `CurrentUserDTO`, which own `/api/v1/users/me`; it is not implemented by `UserController`.
7. Describe the E2E assertion's purpose without claiming the suite passes unless it was actually executed successfully.

Acceptance: shared facts agree across the edited documents; historical defects are labeled historical; checked-in source behavior is not presented as verified production state.

## Task 6 — Verify and close findings

After integrating Tasks 2–5:

1. Read every changed/new contract in full, including intermediate blueprints.
2. Check required sections, headings, table syntax, balanced fences, and locally configured Markdown formatting if available.
3. Resolve all relative file links on disk and verify local heading anchors separately. Check missing child coverage as well as broken existing links.
4. Recheck symbols, routes, SQL objects, invariants and side effects against exact owners. Review nested visibility and delegated writes manually; a textual symbol match is insufficient evidence.
5. Recount migration objects. Check documented verification commands against manifests/scripts and stated working directories. Do not execute data-writing operator commands to validate prose.
6. Run `git diff --check` and inspect new Markdown files separately because that command does not cover untracked files.
7. Review the final diff for intended documentation scope and preservation of unrelated work.
8. Write a follow-up drift review mapping each original finding to resolved/unresolved status and evidence. Preserve the original report as a baseline. Record additional findings and their disposition explicitly.

Application unit/integration/E2E tests are unnecessary for Markdown-only repairs. No new test framework or CI gate is needed. A later runtime change requires its own scope, review and appropriate tests.

## Dependencies and review checkpoints

Execute Task 1 first. Task 2 establishes navigation. Tasks 3 and 4 establish the behavior summaries used by Task 5. Task 6 follows all integrated edits. Keep edits to a shared contract sequential and avoid regenerating whole trees over existing notes.

Review in four logical groups: navigation, foundational contracts, workflow/event contracts, and repository/database/frontend reconciliation. These groups do not authorize automatic staging or commits.

## Definition of done

- Every original finding has a documented resolution and supporting evidence.
- All retained packages are reachable through existing contract links, including the three new intermediate READMEs.
- No reviewed contract denies database, Redis, audit or async effects performed by its source.
- The balance-event chain accurately describes after-commit async work and separate REQUIRES_NEW persistence.
- Schema counts, generation handling, tenant SQL coverage and correlation-ID descriptions agree with current source.
- Frontend guidance preserves the password-exclusion assertion without claiming an unexecuted test result.
- Full contract read-through, structural, link, coverage and whitespace checks pass.
- The handoff lists changed documents, checks performed, unresolved limitations and the follow-up drift-review result.
