# Database-Level Defense: PostgreSQL Row Level Security for Tenant Tables

* **Author:** MedOS team
* **Date:** 2026-10-31

## Overview

Add PostgreSQL Row Level Security (RLS) policies on all tenant-scoped tables so tenant isolation is enforced at the database layer, not only in the application layer. The application sets the acting tenant via a session variable at the start of each request; PostgreSQL policies then restrict every row access to that tenant's rows. Existing application-layer tenant scoping (`TenantStatementInspector`) remains as a second line of defense.

## Analysis

**Contrarian / YAGNI** — Does this feature need to exist?
The application already scopes reads and writes by `tenant_id` through `TenantStatementInspector`. The counter-argument is that application-layer scoping is sufficient if the code is correct. However, application code has bugs, native SQL bypasses Hibernate, and anyone with direct database access can read any row. RLS is the database's own enforcement boundary. For a hospital system holding patient records, defense in depth at the storage layer is justified. The counter-argument against RLS is operational complexity: misconfigured policies can break legitimate queries or silently block access. That risk is manageable with testing.

**Cost, Quota & Token Economics** — What is the footprint?
RLS is built into PostgreSQL. No new infrastructure, no extra services, no license cost. Query planning has a small overhead for policy evaluation on each query, which is negligible at MedOS's expected QPS (dozens, not thousands). No LLM or token cost.

**UX & Developer Ergonomics** — Is it intuitive?
RLS is transparent to application code when policies match the existing `tenant_id` scoping. Developers writing native SQL must understand that rows outside their tenant are invisible, not missing. The friction point is onboarding: new engineers need to learn that the database enforces tenant boundaries, and debugging "missing rows" requires checking RLS policies as well as application code.

**Migration & Backward Compatibility** — Does it break anything?
No schema changes. Tables already have `tenant_id` columns. Policies are added via `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` and `CREATE POLICY`. Existing data must have correct `tenant_id` values (nulls would make rows invisible to all tenants). Backward compatible for the application if the app sets the tenant session variable on every connection. Rollback: `ALTER TABLE ... DISABLE ROW LEVEL SECURITY` reverts to previous behavior.

**Observability & SRE Debuggability** — Is it traceable and actionable?
PostgreSQL logs permission denied errors when RLS blocks a query. These should be captured in structured logs and correlated with `X-Request-Id`. No dedicated metrics are needed; RLS either allows or denies, which is visible in query logs. Oncall should know that a sudden spike in "permission denied" for tenant queries likely means a policy or session-variable problem.

**Long-Term Tech Debt & Maintenance** — Will it hurt in two years?
RLS policies are schema objects. Every new tenant-scoped table needs a policy, and every table change may require policy review. Forgotten policies create silent access gaps. Maintenance burden is low but real: policies must be reviewed alongside schema migrations.

## Goals vs. Non-Goals

**In-Scope Goals**
- Enable RLS on all 18 tenant-scoped tables listed in `TenantStatementInspector.TENANT_TABLES`.
- Create a policy on each table that allows SELECT, INSERT, UPDATE, DELETE only where `tenant_id = current_setting('medos.tenant_id', true)`.
- Set `medos.tenant_id` session variable from the backend at connection checkout and clear it on return.
- Ensure `tenant_id` is NOT NULL on tenant tables where data already exists; backfill or reject nulls.
- Keep application-layer tenant scoping as defense in depth; do not remove it.

**Out-of-Scope Non-Goals**
- Row-level encryption (different from row-level security).
- Replacing application-layer tenant scoping with RLS alone.
- Per-row permissions within a tenant (doctor vs nurse visibility).
- Changes to the public API or authentication model.

## Alternatives Weighed

**Option A: PostgreSQL RLS with session variable (chosen)**
Reason: Native database enforcement, no extra infrastructure, transparent to application queries that already set `tenant_id`, consistent with industry practice for multi-tenant SaaS on Postgres.

**Option B: Application-layer scoping only (status quo)**
Reason rejected: No defense in depth. A bug in `TenantStatementInspector`, a native query, or direct database access bypasses tenant boundaries entirely.

**Option C: Database views per tenant**
Reason rejected: One view per tenant per table does not scale; schema changes must be replicated across hundreds of views; operational burden is high.

## Key Decisions & Staging for Next Steps

1. Use a custom PostgreSQL session variable (`medos.tenant_id`) rather than `app.current_user_id` or `current_user`, because the backend manages tenant context independently of database roles.
2. Backend sets the variable via `SET LOCAL` inside a transaction, or via `SET` on connection checkout from the pool, and clears it on release.
3. Policies use `USING (tenant_id = current_setting('medos.tenant_id', true)::uuid)` with a `WITH CHECK` clause for writes.
4. `tenant_id` nullable columns must be audited and backfilled before enabling RLS, or rows with null tenant_id become invisible to everyone.
5. Next step: implement backend session-variable setting, then enable RLS table by table in a staging environment before production.
