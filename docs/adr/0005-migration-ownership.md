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

  **Amended 2026-10-01.** A classpath copy *was* being generated, by a
  `<resources>` entry in `backend/pom.xml` that added
  `database/migrations` as a second resource directory, packaged by the
  Dockerfile's `COPY`. It appeared only inside the built jar, at
  `BOOT-INF/classes/db/migration/`, so it satisfied the letter of this ADR while
  contradicting it: the on-disk checks could not see it, and the application —
  which had `flyway.locations: classpath:db/migration` — migrated the database
  from that copy at startup, racing the migrate container and winning. Both the
  resource entry and the application-side Flyway are now gone; the classpath copy
  no longer exists in any form.
- `caddy/migrations/` is **gone**; `caddy/` now contains only `Caddyfile` and
  `Dockerfile`.
- `database/migrations/` really is the single owner, holding
  `V1__initial_schema.sql`. On 2026-10-01 the patient name blind index was
  folded into V1 from `V2__patient_name_blind_index.sql`, while no database had
  ever applied either file; the baseline was squashed before first use rather
  than shipping a V2 that only added a column a fresh database could declare
  up front. V1 is frozen from that point on.

Enforcement is now explicit rather than incidental:

- **`tools/verify-migrations.sh`** asserts no `V*.sql` exists outside
  `database/migrations/`, so a copy cannot quietly reappear — the exact failure
  this ADR was written about. It also inspects build artifacts for migrations
  bundled inside a jar, since a packaged copy satisfies the file-level check
  while still being a second copy. That check is what was missing when the
  classpath copy above drifted.
- **`tools/test-verify-migrations.sh`** tests the gate itself, including the
  jar-bundling case. A gate that quietly stops detecting drift is worse than no
  gate, because CI stays green.
- **`database/migrations/SHA256SUMS`** records a checksum per applied migration,
  and the same script verifies it. Flyway stores the checksum of what it actually
  applied, so editing an applied migration fails `flyway validate` on the first
  run against a real database. Catching it in CI is far cheaper. Adding a
  migration is expected and must be registered with
  `tools/update-migration-manifest.sh`.

Both run in the `containers` CI job, and the second is the "deploy gate" half of
issue #15. The PostgreSQL version drift noted above is also resolved: the
migration image and the application both target PostgreSQL 16.
