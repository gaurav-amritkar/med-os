# Database Migrations

## Overview

This directory is the Flyway-owned PostgreSQL schema history. Versioned SQL files are
applied by the dedicated migration container, and `SHA256SUMS` records the expected
content checksums.

## Interface

- `V1__initial_schema.sql` creates the initial hospital, tenant, user, patient,
  appointment, encounter, pharmacy, billing, consent, notification, and audit tables.
- `V2__pii_key_lifecycle.sql` creates `tenant_keys` and `key_rotations`.
- `V6__tenant_dek_history.sql` creates `tenant_dek_history`.
- `V7__enquiry_desk.sql` creates `enquiries` and `enquiry_status_history`.
- `V14__discharge_summaries.sql` creates `discharge_summaries` and
  `discharge_summary_addenda`.
- Other versioned files add columns, constraints, indexes, or tenant-scoped
  relationships to this schema.
- `SHA256SUMS` is the migration checksum manifest consumed by
  `tools/verify-migrations.sh`.

## Invariants

- Version numbers form the ordered Flyway migration history. Released migrations
  are not renamed, edited, or deleted.
- Every new migration must be registered in `database/migrations/SHA256SUMS`
  using `tools/update-migration-manifest.sh`.
- Schema changes are forward-only; correct a released schema through a later
  migration.

## Side Effects

- Flyway execution applies DDL and data changes to the configured PostgreSQL
  database and updates `flyway_schema_history`.
- Updating the manifest changes the local `SHA256SUMS` file.

## Verification

- Run: `docker compose run --rm migrate validate`
- Run: `./tools/verify-migrations.sh`
