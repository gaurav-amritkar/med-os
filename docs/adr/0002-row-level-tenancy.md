# ADR-0002: Row-level multi-tenancy retained

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

Hospitals are the tenants. Two isolation strategies were available: row-level `tenant_id` on shared tables, or one database or schema per tenant. Row-level tenancy is already implemented through `TenantStatementInspector`, `TenantEntityListener`, and `TenantContext`, and is verified by `TenantIsolationTest` and `RbacMatrixTest`. PostgreSQL row-level security was considered as an additional enforcement layer.

## Decision

Keep row-level tenancy. Do not adopt schema-per-tenant or database-per-tenant. Defer PostgreSQL RLS.

## Consequences

- A query that bypasses the statement inspector can read across tenants. The inspector is the single enforcement point, so its test coverage is a security control and not merely a quality metric.
- ⚠️ **The inspector scopes `SELECT` only.** `TenantStatementInspector` returns early for anything that is not a `select`, so `INSERT`, `UPDATE` and `DELETE` are **not** scoped — a write that omits `tenantId` reaches another tenant's row. The predicate is also injected into the root `FROM` only, so joins, subqueries and the non-tenant tables (`users`, `tenant_users`, `tenants`) are deliberately outside its reach. Read isolation is therefore stronger than write isolation, and "queries are scoped per tenant" should not be read as covering both.
- Per-tenant data isolation, export, and deletion are application concerns; a hospital's data-deletion request requires a `tenant_id`-scoped delete across all domain tables.
- One noisy hospital cannot be isolated to its own database or resource pool without a migration.
- RLS remains available as defense-in-depth. It requires per-connection tenant binding that conflicts with Hibernate connection pooling and complicates migrations and incident response, so it is deferred rather than rejected. Revisit if app-layer enforcement is ever found insufficient.
