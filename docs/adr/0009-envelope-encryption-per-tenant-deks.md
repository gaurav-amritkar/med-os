# ADR-0009: Envelope encryption with per-tenant data encryption keys

- **Status:** Accepted
- **Date:** 2026-10-01
- **Supersedes:** nothing. Extends ADR-0004, which decided how secrets are *stored and supplied* but not how PII keys are *structured*.
- **Related:** ADR-0002 (row-level tenancy), ADR-0004 (secret management), ADR-0008 (Supabase managed PostgreSQL), spec `docs/superpowers/specs/2026-10-01-pii-key-lifecycle-fhir-abdm-design.md`

## Context

`PII_ENCRYPTION_KEY` is a single Base64 secret loaded into a static field at
startup and used directly as the AES-256-GCM key for all nine encrypted columns.
Rotating it makes every encrypted value in the database permanently unreadable, and
the application fails closed rather than degrading.

This is a compliance gap, not merely an inconvenience. DPDP Act 2023 §8(5)
requires a Data Fiduciary to take "reasonable security safeguards", with penalties
extending to ₹250 crore. A key that cannot be rotated is exactly the gap §8(5)
addresses: there is no answer to "the person who held the key has left", or "we
suspect the key was exposed".

There is a second failure that is harder to notice. `BlindIndexUtil` derives its
HMAC key from the **same** secret. Rotating the key therefore invalidates every
`patients.name_index`, and `PatientService` matches on equality via
`findByNameIndexOrderByCreatedAtDescIdDesc`. Patient search would return **zero
results with no error**, presenting as broken search rather than as a key problem.

Two constraints shape the answer:

- **The deployment is shared multi-tenant.** Multiple hospitals share one deployment,
  and the explicit requirement is that rotating one hospital's key must not affect
  any other.
- **There is no KMS.** The key-encryption key must live in a file under `secrets/`.

Envelope encryption is the established pattern for this problem, consistent across
Google Cloud KMS, IBM Key Protect, Azure Key Vault and Confluent CSFLE. Its
defining property is that rotating the KEK **re-wraps** the data encryption keys
instead of re-encrypting data, so patient rows are never rewritten and there is no
downtime.

## Decision

Adopt envelope encryption with **one data encryption key per tenant**.

```
  KEK  (secrets/pii-kek.txt, AES-256-GCM)
    │ wraps / unwraps
    ▼
  wrapped DEK per tenant        (table: tenant_keys)
    │ unwrap
    ▼
  DEK, in memory only
    │ encrypts / decrypts
    ▼
  nine ciphertext columns      (patients ×5, encounters ×4)

  separately, with its own lifecycle:
  wrapped blind-index key per tenant → patients.name_index HMAC
```

- **A KEK wraps per-tenant DEKs.** Only wrapped DEKs are ever stored. A plaintext
  DEK never touches the database.
- **A tenant's DEK is created on demand** and is **stable across writes**.
- **The blind index gets its own wrapped key**, with a lifecycle independent of the
  DEK. Name search can be held steady while the DEK rotates, or rotated separately
  if the index key is suspected.
- **Ciphertext carries a version prefix** identifying the DEK *generation* — not the
  KEK version — so rotation is resumable and verifiable. Values without a prefix are
  read as legacy format, which keeps migration incremental.
- **KEK versions are retained.** A retired version must still unwrap, or existing
  DEKs become unreadable.

### The two operations, which must not be conflated

| | **KEK re-wrap** | **DEK rotation** |
|---|---|---|
| Trigger | Routine hygiene, personnel change | DEK or index key suspected compromised |
| Rewrites | `tenant_keys` rows only | One tenant's ciphertext |
| Patient data touched | No | Yes |
| Online | Yes | Yes, in batches |
| Blast radius | Tenants being re-wrapped | One tenant |
| Reversible | Restore the previous KEK file | **No** — requires a prior backup |

KEK re-wrap is the routine path and the reason envelope encryption was chosen.
DEK rotation re-encrypts data, is rare, is scoped to one tenant, and is **not**
reversible by restoring a key file.

## Why per-tenant DEKs

Because the deployment is shared multi-tenant, per-tenant DEKs are what make the
isolation requirement true rather than aspirational:

- Rotating one hospital's key re-wraps that hospital's keyring rows. Every other
  tenant's rows stay byte-identical.
- Offboarding a tenant destroys its DEK, making its PII unreadable without
  affecting anyone else's data.
- The blast radius of a compromised KEK is bounded to the tenants being re-wrapped
  at that moment, not the whole database.

## Deviation: the KEK is a file, not an HSM

The vendors assume a KMS whose KEK lives in a hardware enclave and never leaves.
We have no KMS, so the KEK is a file under `secrets/`, gitignored, mode 600. This is
weaker than an HSM and is recorded as a weakness rather than presented as
equivalent.

Mitigations:

- Per-tenant DEKs bound the blast radius described above.
- Only wrapped DEKs are stored, so a database-only breach yields no key material.
- `PiiKeyringService` is the single component that touches the KEK, so the
  interface for moving to AWS KMS or GCP KMS is narrow and local to it.

Moving to a managed KMS is future hardening, not a prerequisite. It should not
block the key lifecycle work.

## Rejected: a new DEK on every write

Google Cloud KMS advises "generate a new DEK every time you write the data", and
the intent is sound: it minimises how much data any single key protects.

Not followed here. With per-tenant DEKs, a per-write rule would mean every row has
its own key. Patient search and any join across encrypted columns would each require
a separate unwrap, and the blind index could not be computed consistently across
rows. For a hospital EMR that destroys the record-linking the application exists to
perform.

One stable DEK per tenant is also what IBM Key Protect and Azure describe for this
case.

## Rejected: a single global DEK

Simpler, and sufficient if there were one hospital. It would make the isolation
requirement unachievable: every re-wrap touches every tenant, and there is no way to
destroy one hospital's key material alone. Retrofitting per-tenant DEKs after real
patient data exists would mean re-encrypting the whole database, which is the
outage this ADR exists to prevent.

## Rejected: re-encrypting all data on rotation

This is the status quo, and the naive fix: decrypt everything with the old key,
re-encrypt with the new one. It is reversible, requires a maintenance window
proportional to row count, and creates concurrent-write hazards for rows being
modified during the run. Envelope encryption avoids all three.

## Consequences

- Rotation becomes a routine operation touching keyring rows, not a data migration.
- **The blind index must be separated from the PII secret** (issue #87). Until it
  is, any rotation breaks patient search silently.
- `EncryptionUtil` must become tenant-aware while remaining a JPA
  `AttributeConverter` for all nine columns.
- Every new encrypted column must be registered with the tenant's DEK. This is a
  real hazard: a forgotten registration would write data no rotation can re-wrap.
  The regression tests in #86 cover this.
- `docs/operations.md` must carry the rotation runbook, including the hard rule
  that the previous KEK version is never deleted before verification passes, and
  that DEK rotation is **not** reversible by restoring a key file.
- The keyring table is a high-value target. It contains wrapped key material, so
  read access to it should be restricted to the application's database role.
- DPDP §8(5) evidence becomes demonstrable: rotation history with initiator and
  timestamps (issue #103).

## Alternatives considered

| Option | Why not |
|---|---|
| Single global DEK, re-encrypt on rotation | Unachievable isolation; whole-database rewrite on every rotation |
| Per-write DEK | Breaks search and joins across encrypted columns |
| pgsodium / Supabase Vault | Tempting, since `pgcrypto` and `supabase_vault` are already present on the project. Rejected because it would put key operations in the database, where a SQL injection or a leaked connection string could reach unwrapped key material. Keeping unwrap in the application means a database compromise alone yields nothing usable. |
| HSM-backed KMS immediately | No KMS is available, and this must not block the key lifecycle work. The interface is designed so it can be adopted later. |
| Hash-based pseudonymisation instead of encryption | Would satisfy key rotation trivially, but is irreversible: clinical records must be readable. Not a substitute. |