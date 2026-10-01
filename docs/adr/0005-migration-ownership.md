# ADR-0005: Single owner for Flyway migrations

- **Status:** Accepted
- **Date:** 2026-09-26
- **Amended:** 30 Sep 2026 — the copies this ADR removed no longer exist, so the
  drift rule it described is enforced differently. See the amendment below.

## Context

Migrations exist in three places. `database/migrations/` holds `V1__init.sql` and `V2__initial_schema.sql`. `backend/src/main/resources/db/migration/` holds byte-identical copies of both. `caddy/migrations/` holds `V1` through `V8`, a superseded lineage whose `V1__initial_schema.sql` is 335 lines against the current 396-line consolidated `V2`, and which still contains demo seed data, PII-encryption DDL, and index work that no longer exists in the baseline. `README.md` names `database/migrations/` as the schema owner; the backend comment claims the classpath copy exists so app and migrate container share one file.

## Decision

`database/migrations/` is the single owner. The classpath copy is generated at build time rather than maintained by hand. `caddy/migrations/` is deleted.

## Consequences

- Divergence between two hand-maintained copies becomes impossible.
- The `caddy` directory keeps only its Caddyfile, TLS role, and entrypoint.
- A developer editing a migration must edit it in `database/migrations/` and rebuild; the build must fail if the generated copy is stale.
- PostgreSQL version drift (15 in compose, 16.9 in `database/Dockerfile`) is resolved in the same change, since migration behaviour can differ across major versions.

## Amendment (30 Sep 2026)

The situation this ADR described has since been resolved, which changes *how* the
single-owner rule is enforced rather than the rule itself.

Verified on this branch:

- `backend/src/main/resources/db/migration/` **does not exist**. There is no
  classpath copy, and no build step generates one. The original "fail if the
  generated copy is stale" rule therefore has nothing to compare.
- `caddy/migrations/` is **gone**; `caddy/` now contains only `Caddyfile` and
  `Dockerfile`.
- `database/migrations/` really is the single owner, holding `V1__initial_schema.sql`
  and `V2__patient_name_blind_index.sql`.

Enforcement is now explicit rather than incidental:

- **`tools/verify-migrations.sh`** asserts no `V*.sql` exists outside
  `database/migrations/`, so a copy cannot quietly reappear — the exact failure
  this ADR was written about.
- **`database/migrations/SHA256SUMS`** records a checksum per applied migration,
  and the same script verifies it. Flyway stores the checksum of what it actually
  applied, so editing an applied migration fails `flyway validate` on the first
  run against a real database. Catching it in CI is far cheaper. Adding a
  migration is expected and must be registered with
  `tools/update-migration-manifest.sh`.

Both run in the `containers` CI job, and the second is the "deploy gate" half of
issue #15. The PostgreSQL version drift noted above is also resolved: the
migration image and the application both target PostgreSQL 16.
