---
inclusion: fileMatch
fileMatchPattern: "database/**,backend/src/main/java/com/medos/entity/**,backend/src/main/java/com/medos/repository/**"
---

# MedOS Database — Steering Rules

Applies whenever touching migrations, entities, or repositories.

## Migration rules (read before adding any SQL)

- File naming: `database/migrations/V<n>__description.sql` where `<n>` is the next integer.
- Current highest: **V17** (`V17__lab_result_encrypted.sql`). Next new migration = `V18__...sql`.
- **Never edit an applied migration.** Flyway checksums every file; an edit breaks startup with `Validate failed`.
- Never create `backend/src/main/resources/db/migration/` — the `migrate` container is the sole Flyway runner.
- After adding a migration, run `./tools/verify-migrations.sh` to catch drift before CI does.
- Seed data (`INSERT`) belongs in `tools/seed-dev.sh`, not in migrations. Migrations are schema only.
- Zero-downtime changes: add a column nullable first, backfill, then add NOT NULL constraint in a later migration.

## Table inventory (31 tables as of V17)

Core tenancy: `tenants`, `tenant_users`, `tenant_configs`, `tenant_settings`, `tenant_keys`, `key_rotations`, `tenant_dek_history`

Identity: `users`, `feature_flags`

Clinical: `patients`, `appointments`, `encounters`, `prescriptions`, `admissions`, `rooms`, `discharge_summaries`, `discharge_summary_addenda`, `lab_orders`, `consents`

Enquiry: `enquiries`, `enquiry_status_history`

Pharmacy: `medicine_catalog`, `medicine_batches`, `stock_transactions`, `disease_medicine_map`

Billing: `charges`, `invoices`, `payments`

Operations: `audit_log`, `notifications`, `opd_queue`

## Tenant-scoped tables (must carry tenant_id)

All clinical, pharmacy, billing, and operations tables are tenant-scoped. When adding a new table in this domain:
1. Add `tenant_id BIGINT NOT NULL REFERENCES tenants(id)`.
2. Add the table name to `EncryptedPiiTables.TENANT_TABLES` in the backend.
3. `TenantStatementInspector` will then enforce SELECT/UPDATE/DELETE scoping automatically.

## Entity rules

- Entities live in `backend/src/main/java/com/medos/entity/` and map 1:1 to Flyway tables.
- PII columns (patient demographics, encounter notes) use the `@Convert` AES-256-GCM converter.
- Never add a `@GeneratedValue` strategy other than `IDENTITY` — the schema uses `BIGSERIAL`/`SERIAL`.
- Do not use `cascade = CascadeType.ALL` on tenant-scoped relationships without reviewing blast radius.

## Repository rules

- All repositories extend `JpaRepository<Entity, Long>`.
- Never write a `@Query` on a tenant-scoped table without a `AND e.tenantId = :tenantId` predicate.
- For locking (fixing races G09/G10/G11): use `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the finder method.
