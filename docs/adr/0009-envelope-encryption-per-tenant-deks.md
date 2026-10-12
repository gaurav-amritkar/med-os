# ADR-0009: Envelope encryption with per-tenant data encryption keys

- **Status:** Accepted
- **Date:** 2026-10-01
- **Supersedes:** nothing. Extends ADR-0004, which decided how secrets are *stored and supplied* but not how PII keys are *structured*.
- **Related:** ADR-0002 (row-level tenancy), ADR-0004 (secret management), ADR-0008 (Supabase managed PostgreSQL), spec `docs/superpowers/specs/2026-10-01-pii-key-lifecycle-fhir-abdm-design.md`

> ## Implementation status — read this before the Context section
>
> The **Context** below describes the *pre-ADR* system. It no longer describes the
> code. As of `main`, this design is **implemented**:
>
> | Capability | State |
> |---|---|
> | KEK wraps a per-tenant DEK (`tenant_keys.wrapped_dek`) | ✅ shipped |
> | Blind-index key separately generated and wrapped (`wrapped_bi_key`) | ✅ shipped |
> | Ciphertext versioned `kv1.d<generation>:<base64>`; legacy values readable | ✅ shipped |
> | One tenant's key cannot decrypt another's rows | ✅ shipped (`TenantKeyIsolationTest`) |
> | KEK re-wrap: dry-run / apply / verify, resumable, fail-closed | ✅ shipped |
> | Rotation runbook (`docs/operations.md` §7d) | ✅ shipped |
> | **Per-tenant DEK rotation (`dek_rotation`)** | 🔴 **not implemented** |
> | **Generation > 1 in ciphertext** | 🔴 **unreachable** (`currentGeneration()` hardcodes `1`) |
> | Blind-index key rotation | 🔴 no operator surface |
>
> If you are looking for a statement that "the PII key cannot be rotated", it is
> stale — four documents carried it until 2026-10-12 and all four were wrong. The
> KEK is rotatable online with no data migration.

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
  **Implemented:** `KeyRewrapService` (dry-run `plan()`, apply `rotate()`;
  resumable via `wrapped_kek_version`; fails closed with zero writes if any
  tenant cannot be unwrapped) and `PiiKeyRotationRunner`
  (`dry-run` / `apply` / `verify`, driven by `MEDOS_KEY_ROTATION_*` env vars, not
  argv). Audited to `key_rotations`. Runbook: `docs/operations.md` §7d.
- **The blind index is separated from the PII secret** (issue #87 — **done**).
  It is a third, independently generated key in `tenant_keys.wrapped_bi_key`, not
  derived from the DEK, with its own `bi_key_generation`. *An earlier revision of
  this line said the separation was still pending and that "any rotation breaks
  patient search silently"; that is closed.*
- `EncryptionUtil` must become tenant-aware while remaining a JPA
  `AttributeConverter` for all nine columns. **Done** — it reads the acting
  tenant's DEK through `TenantKeyResolverFactory`, populated from a `ThreadLocal`
  by `JwtAuthenticationFilter`, which fails closed when no tenant is present.
- Every new encrypted column must be registered with the tenant's DEK. This is a
  real hazard: a forgotten registration would write data no rotation can re-wrap.
  ⚠️ There is currently **no regression test that enumerates the registered
  encrypted columns**, so this hazard is unenforced — a new `@Convert` column
  that resolves no DEK would fail at runtime, not at build time.
- The `kv1.d<generation>:` prefix identifies the DEK generation. ⚠️ As shipped,
  `TenantKeyHolder.currentGeneration()` returns `1` and
  `PiiCiphertextFormat.tenantResolver()` ignores its `generation` argument, so
  `UnknownGenerationException` cannot fire and **generation > 1 is not reachable**.
  The prefix is correct and necessary, but currently only generation 1 can ever
  be written. This is what blocks `dek_rotation`.
- `docs/operations.md` carries the rotation runbook, including the hard rule
  that the previous KEK version is never deleted before verification passes.
  Note the asymmetry: a **KEK** re-wrap *is* fully reversible by restoring the
  old key file, because no data was rewritten. Only `dek_rotation`, which
  rewrites ciphertext, is irreversible that way — and it is not implemented.
- Tenant offboarding destroying the DEK was proposed here but **no code path does
  it**; there is no tenant-deletion or key-destroy operation today.
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