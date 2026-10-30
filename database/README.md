# Database schema management

This project uses **Flyway** as the single source of truth for PostgreSQL
schema changes. Liquibase is intentionally not installed: using two migration
tools against the same schema produces independent history tables and makes
deployment ordering and recovery ambiguous.

`database/migrations/` is the **single owner** of the schema
([ADR-0005](../docs/adr/0005-migration-ownership.md)). There is no classpath copy
in the application jar: the dedicated `migrate` container reads these files
directly, and the application only validates the schema it finds.

## Interface

- `database/migrations/` is the Flyway SQL input copied by
  [`migrations.Dockerfile`](./migrations.Dockerfile); `V1__initial_schema.sql`
  defines the initial tables and later versioned files extend them.
- `database/init.sql` defines the `uhid_seq`, `invoice_number_seq`, and
  `payment_number_seq` sequences when explicitly used by an operator.
- [`migrations/README.md`](./migrations/README.md) documents the migration
  package contract.

## Current state

- The schema is at **V14**. `V1__initial_schema.sql` defines 23 initial application
  tables; seven later CREATE TABLE statements add key lifecycle,
  tenant DEK history, enquiry tracking, and discharge summaries. The checked-in
  history defines 30 application tables through V14; this is not a claim about a
  deployed database.
- Applied migrations are immutable. Every subsequent schema change is a new
  versioned file.
- `SHA256SUMS` records a checksum per migration. `tools/verify-migrations.sh`
  checks the manifest, and `tools/update-migration-manifest.sh` registers new
  migrations.

## Adding a schema change

1. Add a new, immutable SQL file in `database/migrations/` named
   `V<next-number>__short_description.sql`. The next number is **15**:

   ```text
   V15__add_patient_emergency_contact.sql
   ```

2. Register it, or CI will fail on an unrecorded migration:

   ```bash
   tools/update-migration-manifest.sh
   ```

3. Make the SQL safe for the current production data. Use nullable columns or
   defaults for a first deployment; backfill data; then add `NOT NULL` or other
   strict constraints in a later migration.
4. Update the corresponding JPA entity and add or update backend tests.
5. Validate against a disposable local database:

   ```bash
   docker compose down -v
   docker compose up -d
   docker compose logs migrate    # must log "Successfully applied ... migration"
   tools/verify-migrations.sh
   ```

6. Run the full stack and confirm the backend health check succeeds:
   `curl http://localhost:8080/manage/health`.

## Why `-baselineOnMigrate=true -baselineVersion=0`

Supabase installs its own objects into the `public` schema, so Flyway refuses to
migrate a non-empty schema and asks for a baseline. The production descriptor
therefore passes **both** flags, and the version matters:

- `-baselineVersion=1` marks V1 as already applied and then **skips it**. The
  deploy reports `Schema "public" is up to date` having created **zero tables**,
  and Flyway never retries. This happened against the real project.
- `-baselineVersion=0` records a marker *below* every real migration, so V1
  applies normally and its **checksum is stored** — which is what lets a later
  `flyway validate` detect drift.

Do not raise that value. To adopt a genuinely pre-existing database, baseline
deliberately and by hand at the correct version.

## Rules

- Never rename, edit, or delete a migration that has reached any shared
  environment. Flyway records its checksum in `flyway_schema_history`.
- Migrations are forward-only. Correct a released migration with a new one;
  do not use `repair` to conceal a checksum mismatch.
- For destructive or large-table work, use the expand/backfill/contract pattern
  across separate releases. Create indexes concurrently outside transactional
  Flyway migrations when the PostgreSQL operation requires it.
- The application runs Hibernate with `ddl-auto: validate` in production. It
  validates the schema but never changes it — and application-side Flyway is
  **disabled**, so the migrate container is the only thing that can migrate.
- Keep length-typed columns `VARCHAR`, never `CHAR(n)`. PostgreSQL maps `CHAR(n)`
  to `bpchar`, which Hibernate's `ddl-auto=validate` rejects for a
  length-declared `String` (found bpchar, expecting varchar).

## Useful commands

```bash
# Displays applied and pending versions
docker compose run --rm migrate info

# Validates migration names and checksums without changing data
docker compose run --rm migrate validate
```

## PII Encryption at Rest

Sensitive patient data and clinical notes are encrypted using AES-GCM (256-bit key) before storage.

### Encrypted Fields

| Table | Fields |
|-------|--------|
| `patients` | name, phone, email, address, blood_group |
| `encounters` | chief_complaint, diagnosis, clinical_notes, ai_note |

### Configuration

The key is required; the application **fails closed** rather than falling back to
a default:

```bash
# Must decode to exactly 32 bytes. No trailing newline.
PII_ENCRYPTION_KEY="$(openssl rand -base64 32 | tr -d '\n')"
```

```yaml
medos:
  security:
    pii-encryption-key: ${PII_ENCRYPTION_KEY}
```

Verify a key before using it:

```bash
printf '%s' "$PII_ENCRYPTION_KEY" | base64 -d | wc -c   # must print 32
```

### Implementation

- `EncryptionUtil` (JPA `AttributeConverter`) handles transparent encryption/decryption
- Each value uses a unique random IV (12 bytes) + AES-GCM
- Format: `Base64(IV || ciphertext || authTag)`

### Searching an encrypted name

`patients.name` is ciphertext, and AES-GCM is randomised per value, so
`WHERE name LIKE '%anita%'` can never match — search would return nothing at all.

`patients.name_index` holds a keyed blind index: a deterministic HMAC-SHA256 of
the normalised name, matched by equality. `uhid` stays unencrypted and supports
partial matching directly.

The blind index is as sensitive as the plaintext it protects: a holder of the
database but not the key cannot read names, but **can confirm a guessed name** by
comparing digests.

### Key rotation: both KEK re-wrap and per-tenant DEK rotation are implemented

`PII_ENCRYPTION_KEY` is **not** the key that encrypts patient data. It is the
**key-encryption key (KEK)**: it wraps a separate **per-tenant DEK** stored in
`tenant_keys.wrapped_dek`, and that DEK is what encrypts the nine ciphertext
columns. The blind index has a **third**, independently generated key in
`tenant_keys.wrapped_bi_key`, so it is not derived from either of the others.

Consequences:

- **Rotating the KEK rewrites key rows only — no patient row is touched.** The
  `KeyRewrapService` unwraps each tenant DEK with the old KEK and re-wraps it
  with the new one; ciphertext is unchanged and stays readable.
- **The blind index survives rotation**, because it is not derived from the PII
  secret. This was the silent-search-breakage failure mode the split was designed
  to remove.
- **Per-tenant DEK rotation is implemented** by `DekRotationService` and
  `TenantKeyHolder.rotateDek`. It archives the prior wrapped key, advances the
  generation, re-encrypts and verifies tenant ciphertext, and optionally rebuilds
  the blind index. The operator procedure and backup gate are in
  [`docs/operations.md` §7e](../docs/operations.md).
- **Safe automatic retirement of archived DEKs is not implemented.** The runbook
  requires retaining old generations for late commits; never delete them manually.

Design: [ADR-0009](../docs/adr/0009-envelope-encryption-per-tenant-deks.md).
Operator runbook: [`docs/operations.md` §7d](../docs/operations.md).

**Practical consequence today:** KEK re-wrap is available through §7d, and
per-tenant DEK rotation is available through §7e with a verified backup and
re-encryption. Archived generations remain required for late commits until safe
retirement is implemented.

## Invariants

- `database/migrations/` is the sole schema owner; the application validates
  the schema but does not apply Flyway migrations.
- Released migration files and their `SHA256SUMS` entries are immutable.
- PostgreSQL string columns declared with lengths use `VARCHAR`, because
  Hibernate schema validation rejects `CHAR(n)` for length-declared strings.

## Side Effects

- Applying Flyway files changes the PostgreSQL schema and migration history.
- `database/init.sql` creates sequences in the connected database when run.

## Verification

- Run: `docker compose run --rm migrate validate`
- Run: `./tools/verify-migrations.sh`

## Subdirectories & Boundaries

- [migrations](./migrations/README.md) — ordered Flyway schema changes and
  their checksum manifest.
