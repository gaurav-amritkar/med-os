# Database schema management

This project uses **Flyway** as the single source of truth for PostgreSQL
schema changes. Liquibase is intentionally not installed: using two migration
tools against the same schema produces independent history tables and makes
deployment ordering and recovery ambiguous.

## Adding a schema change

1. Add a new, immutable SQL file in `database/migrations/` named
   `V<next-number>__short_description.sql`. For example:

   ```text
   V5__add_patient_emergency_contact.sql
   ```

2. Make the SQL safe for the current production data. Use nullable columns or
   defaults for a first deployment; backfill data; then add `NOT NULL` or other
   strict constraints in a later migration.
3. Update the corresponding JPA entity and add or update backend tests.
4. Validate the migration against a disposable local database:

   ```bash
   docker compose up -d db
   docker compose run --rm migrate migrate
   docker compose run --rm migrate validate
   ```

5. Run the full stack with `docker compose up --build` and confirm the backend
   health check succeeds.

## Rules

- Never rename, edit, or delete a migration that has reached any shared
  environment. Flyway records its checksum in `flyway_schema_history`.
- Migrations are forward-only. Correct a released migration with a new one;
  do not use `repair` to conceal a checksum mismatch.
- For destructive or large-table work, use the expand/backfill/contract pattern
  across separate releases. Create indexes concurrently outside transactional
  Flyway migrations when the PostgreSQL operation requires it.
- The application runs Hibernate with `ddl-auto: validate` in production. It
  validates the schema but never changes it.

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

Set the encryption key via environment variable (required in production):

```bash
# Generate key: openssl rand -base64 32
export PII_ENCRYPTION_KEY="<base64-encoded-32-byte-key>"
```

In `application.yml`:
```yaml
medos:
  security:
    pii-encryption-key: ${PII_ENCRYPTION_KEY}
```

### Implementation

- `EncryptionUtil` (JPA `AttributeConverter`) handles transparent encryption/decryption
- Each field encrypted with unique random IV (12 bytes) + AES-GCM
- Encrypted format: Base64(IV || ciphertext || authTag)

### Migration Notes

- `V5__encrypt_pii_fields.sql` adds column comments indicating encryption
- Existing plaintext data requires one-time encryption script or re-entry
- For zero-downtime: add encrypted columns, backfill, switch, drop old
