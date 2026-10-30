# Repository Tools

## Overview

This directory contains operational and CI helper scripts for migration integrity,
production secret wiring, local demo seeding, image scan baselines, and migration
manifest maintenance.

## Interface

- `verify-migrations.sh` checks versioned migration files against `SHA256SUMS` and
  repository ownership rules.
- `update-migration-manifest.sh` recalculates and writes the migration checksum
  manifest.
- `verify-prod-secrets.sh` checks required production secret mounts and wiring.
- `seed-dev.sh` with `seed-dev.sql` seeds development accounts and demo records;
  it supports Docker Compose and a `--local` mode.
- `update-trivy-baseline.sh` updates the repository's Trivy ignore baseline.
- [`observability/prometheus-rules-medos.yml`](./observability/prometheus-rules-medos.yml)
  contains Prometheus rule configuration.
- `test-verify-migrations.sh` and `test-verify-prod-secrets.sh` exercise the
  corresponding verification scripts.

## Invariants

- `seed-dev.sh` is for local development only and must not target a production
  database.
- Migration checksums are maintained through the manifest update script; migration
  edits are not concealed by rewriting history after release.
- Production secret checks must reflect the secret files mounted by the production
  Compose configuration.

## Side Effects

- Migration and secret verification scripts read repository configuration and return
  a nonzero status on detected violations.
- `update-migration-manifest.sh` and `update-trivy-baseline.sh` write repository
  files.
- `seed-dev.sh` writes demo data to PostgreSQL and creates demo patients through the
  application API when available.

## Verification

- Run: `./tools/test-verify-migrations.sh`
- Run: `./tools/test-verify-prod-secrets.sh`
