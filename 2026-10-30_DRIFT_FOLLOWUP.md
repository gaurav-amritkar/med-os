# EGM Drift Follow-up — 2026-10-30

Baseline: [2026-10-30 drift report](./2026-10-30_DRIFT_REPORT.md)
Implementation plan: [contract drift remediation](./docs/superpowers/plans/2026-10-30-egm-contract-drift-remediation.md)
Code baseline: `main` at `83c84dd`

## Original findings

| Finding | Status | Evidence / resolution |
|---|---|---|
| Root database table total and inventory stale | Resolved | `README.md` now lists the 30 application tables defined by current migration history, including seven later additions. |
| Root module summary omitted appointment and enquiry | Resolved | Root architecture summary lists both modules. |
| `com/medos` contract omitted child package links | Resolved | Its Interface section links all eleven direct package contracts. |
| Module index omitted appointment, enquiry, and payment | Resolved | The module blueprint links all six module branches. |
| Intermediate appointment, enquiry, payment contracts absent | Resolved | Added one grouping README for each, linked to its service contract. |
| Config side effects understated | Resolved | Bootstrap persistence, name-index backfill, request/response effects are described. |
| Security side effects mischaracterized | Resolved | Contract now names PostgreSQL key/ciphertext writes, rotation audit state, and Redis/process-local rate-limit state. |
| General service side effects omitted | Resolved | Contract describes repository persistence and Redis idempotency operations. |
| Utility audit persistence omitted | Resolved | Contract describes `AuditLogger` persistence and async entry point. |
| Appointment service writes omitted | Resolved | Contract describes appointment repository writes. |
| Billing service writes/events omitted | Resolved | Contract describes invoice/charge changes and balance-event publication, with a link to the event contract. |
| Balance listener timing and write omitted | Resolved | Contract states AFTER_COMMIT, asynchronous handling, and the REQUIRES_NEW balance update. |
| Clinical service writes omitted | Resolved | Contract lists admission, room, summary, addendum, charge, and event effects. |
| Enquiry persistence/history omitted | Resolved | Contract describes enquiry, append-only history, audit, and numbering retry effects. |
| Payment writes/events omitted | Resolved | Contract describes payment/invoice writes and balance-event publication. |
| Pharmacy writes/events omitted | Resolved | Contract lists stock/catalog/prescription/charge writes, FEFO, and event publication. |
| Frontend E2E note described fixed password leak as current | Resolved | Contract now identifies the DTO response and retains the assertion without claiming tests were run. |

## Additional related claims corrected

- `database/README.md` now distinguishes 23 tables in V1 from seven later table additions and the current checked-in total of 30.
- Root and database guides now describe implemented per-tenant DEK rotation and the still-missing safe retirement of archived generations. The operations guide remains the runbook source.
- Root security guidance now describes the implemented statement-shape handling for SELECT, UPDATE, DELETE, and guarded INSERT, with its limitations.
- Root observability guidance records the `X-Request-Id` filter while retaining the gaps in metrics and structured logging.
- `com/medos/README.md` now describes application startup rather than claiming no side effect.

## Additional source comment correction

The stale class-level comment in `PatientBalanceService` was corrected to match the method's `REQUIRES_NEW` transaction and the asynchronous after-commit caller. This updates documentation in source comments only; it does not change runtime behavior.

## Verification

Markdown content, headings, code fences, relative links, package navigation, and whitespace were reviewed after the edits. The Java change only corrects a comment; project tests were not run because executable behavior did not change.
