<!-- medos-release-audit: reports -->
## Outcome and priority

**P1 · Suggested accountable role: Backend/frontend + hospital operator**. Release tracker: #219.

A broad analytics roadmap can wait, but pilot staff need daily activity/collections and attributable audit retrieval.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§7 reporting**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Agree a small pilot report set: registrations/appointments/encounters, collections by method, outstanding/unbilled totals and included pharmacy/lab activity.
2. Use authoritative domain records and consistent tenant/timezone/date-boundary rules; avoid duplicating inaccurate balance calculations.
3. Expose role-appropriate report/export and audit lookup with actor/time/record/action filters.
4. Separate unavailable report data from valid zeros; bound pagination/exports and avoid clinical payloads in general operational exports.
5. Compare report totals with a seeded cashier/clinical day and document report definitions.

## Dependencies and coordination

- #226 — Calculate discounted invoice balances from authoritative receivables
- #224 — Remove clinical payloads from routine audits and logs
- #148

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Daily totals reconcile with authoritative workflow fixtures and cash close.
- [ ] Roles and tenants cannot retrieve another organization's report/audit data.
- [ ] Large result sets paginate/export safely and unavailable inputs do not display reassuring zeros.

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

### Summary

PRD §3 specifies a `<Reports>` page — "Read-only analytic charts using Recharts mapped array". It does not exist, and `recharts` is installed but **imported by zero files**. This is the only gap that also blocks the product measuring its own success criteria.

### Evidence

- No `/reports` route (`App.jsx:32-51`).
- `grep -rl recharts frontend/src` → **0 files**. The dependency is dead weight.
- BRD §9 defines six success metrics: reduction in unbilled revenue, reduction in expired write-offs, reduction in average patient wait time, DAU/MAU, AI suggestion acceptance rate, end-to-end task completion times. **None has any measurement behind it** — no analytics, no usage telemetry, no event capture, no baseline.
- BRD §10 sets a performance NFR of API responses < 200ms. The flow probe *reports* per-call timings (`probe-flows.mjs:55`) but **asserts no budget**, so a regression would pass silently.

### The data already exists

Every input is in the schema; this is a read-and-visualise task, not a data-capture one.

| Wanted | Available |
|---|---|
| Revenue over time | `charges.created_at`, `charges.status`, `charges.charge_type` |
| Unbilled exposure | `invoices.status` (issued/partially_paid/paid), `charges.status` (unbilled/billed/paid) |
| Billed vs collected | `invoices.total_amount`, `payments.amount`, `payments.status`, `payments.received_at` |
| Expiry exposure | `medicine_batches.expiry_date`, `remaining_qty` — `expiringStockCount` already computed in `DashboardService` |
| Occupancy | `admissions.status`, `rooms.occupied` |
| Volume | `patients`, `encounters`, `prescriptions` with `created_at` |

### Requirements

- A `ReportsController` and a `/reports` route, plus a sidebar entry. Tenant-scoped like every other read.
- **Decide the audience and gate by role.** Billing clerks and clinicians should not see revenue. Restrict to `ADMIN` initially, and say so.
- Charts, at minimum: revenue collected over time, billed vs collected, unbilled exposure by charge type, stock expiring within 30/60/90 days, and admissions/occupancy.
- A receivables summary that aggregates **per patient across invoices** — see the AR issue; these share a data source and should be built together or at least consistently.
- Reuse the existing token layer. **Do not reintroduce hardcoded hex** — PRD §6's token constraint still holds even though its visual spec was replaced.
- **Assert the BRD §10 budget in the flow probe** rather than only printing timings, so an API regression fails CI.
- Set a bundle-size and load budget for the < 1.5s mount NFR, and measure it.
- Ship at least one baseline snapshot of each metric, so later change is measurable at all.

### Explicitly out of scope

DAU/MAU, wait-time and task-completion metrics need usage telemetry that does not exist. Tracking those is separate work and should be its own decision — do not imply they are covered by this page.

### Verification

- The page renders only the caller's tenant's data, and a non-admin role is refused.
- Revenue totals on the page reconcile exactly against a direct sum of the same period from the database.
- Expiring-stock counts match `expiringStockCount` in the dashboard for the same window.
- The flow probe fails when an endpoint exceeds the 200ms budget (tested by temporarily breaking the budget).
- A load-time budget check runs in CI and is reported.
- Contrast check passes, per the Wave 0 guard.

### Notes

- Tracked from `docs/requirements-traceability.md` §1.2 and §1.7.
- `recharts` is already installed, so the chart work itself is low cost; the value is the missing measurement layer.
- Pairs with the accounts-receivable issue; share a read model.


</details>
