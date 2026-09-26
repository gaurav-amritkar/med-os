# ADR-0005: Single owner for Flyway migrations

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

Migrations exist in three places. `database/migrations/` holds `V1__init.sql` and `V2__initial_schema.sql`. `backend/src/main/resources/db/migration/` holds byte-identical copies of both. `caddy/migrations/` holds `V1` through `V8`, a superseded lineage whose `V1__initial_schema.sql` is 335 lines against the current 396-line consolidated `V2`, and which still contains demo seed data, PII-encryption DDL, and index work that no longer exists in the baseline. `README.md` names `database/migrations/` as the schema owner; the backend comment claims the classpath copy exists so app and migrate container share one file.

## Decision

`database/migrations/` is the single owner. The classpath copy is generated at build time rather than maintained by hand. `caddy/migrations/` is deleted.

## Consequences

- Divergence between two hand-maintained copies becomes impossible.
- The `caddy` directory keeps only its Caddyfile, TLS role, and entrypoint.
- A developer editing a migration must edit it in `database/migrations/` and rebuild; the build must fail if the generated copy is stale.
- PostgreSQL version drift (15 in compose, 16.9 in `database/Dockerfile`) is resolved in the same change, since migration behaviour can differ across major versions.
