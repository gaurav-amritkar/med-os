# Database schema management

This project uses **Flyway** as the single source of truth for PostgreSQL
schema changes. Liquibase is intentionally not installed: using two migration
tools against the same schema produces independent history tables and makes
deployment ordering and recovery ambiguous.

`database/migrations/` is the **single owner** of the schema
([ADR-0005](../docs/adr/0005-migration-ownership.md)). There is no classpath copy
in the application jar: the dedicated `migrate` container reads these files
directly, and the application only validates the schema it finds.

## Current state

- **One migration: `V1__initial_schema.sql`**, which creates all 23 tables.
  It was squashed from earlier files before it was ever applied anywhere.
- `V1` is **frozen**. Every subsequent change is a new `V2`, `V3`, …
- `SHA256SUMS` records a checksum per migration. `tools/verify-migrations.sh`
  fails the build if an applied file changes, and `tools/update-migration-manifest.sh`
  registers a new one.

## Adding a schema change

1. Add a new, immutable SQL file in `database/migrations/` named
   `V<next-number>__short_description.sql`. The next number is **2**:

   ```text
   V2__add_patient_emergency_contact.sql
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

### Key rotation is not implemented yet

`PII_ENCRYPTION_KEY` is a single secret used directly as the AES key. **Changing
it makes every encrypted value permanently unreadable**, and because the blind
index derives from the same secret, patient search breaks silently at the same
time — with no error.

The envelope-encryption design that fixes this is accepted in
[ADR-0009](../docs/adr/0009-envelope-encryption-per-tenant-deks.md) (per-tenant
DEKs wrapped by a KEK, so rotation re-wraps keys instead of re-encrypting data).
Implementation is tracked from
[#84](https://github.com/gaurav-amritkar/med-os/issues/84) onwards.

**Practical consequence today:** treat this key as permanent. Rotating it
manually requires dropping and re-entering data, so do not change it once real
patient records exist.
