# PII Key Lifecycle, FHIR R4 Interoperability, and ABDM/ABHA Compliance

**Date:** 2026-10-01
**Status:** Draft for review
**Supersedes:** nothing
**Related ADRs:** ADR-0002 (row-level tenancy), ADR-0004 (secret management), ADR-0005 (migration ownership), ADR-0008 (Supabase managed Postgres, auth in-house)

---

## 1. Problem

`PII_ENCRYPTION_KEY` cannot be changed without destroying data. It is a single
Base64 secret loaded into a static field at startup and used directly as the AES
key. Every encrypted value in the database is bound to it. Rotating it means
every row becomes permanently unreadable, and the application fails closed with
`Failed to decrypt PII field (wrong PII_ENCRYPTION_KEY or corrupted data)`.

This is a compliance gap, not just an inconvenience. DPDP Act 2023 §8(5) obliges
a Data Fiduciary to take "reasonable security safeguards", and the DPDP Rules
2025 (notified 14 Nov 2025) set an 18-month phased compliance timeline. Failure
to maintain reasonable safeguards carries a penalty of up to ₹250 crore. Being
unable to rotate a key after a personnel change or a suspected compromise is
exactly the gap §8(5) addresses.

There is a second, quieter failure. `BlindIndexUtil` derives its HMAC key from
the *same* PII secret, so rotating the key also silently invalidates every
`patients.name_index` value. Patient search matches by equality against that
digest (`findByNameIndexOrderByCreatedAtDescIdDesc`), so after a rotation search
returns zero results with no error at all. Search would appear to be broken
rather than the key.

Current blast radius: **9 encrypted columns** — `patients` (name, phone, email,
address, blood_group) and `encounters` (chief_complaint, diagnosis,
clinical_notes, ai_note).

## 2. Goals and non-goals

### Goals

1. Rotate the PII key with **no data loss and no downtime**.
2. Isolate tenants so rotating one hospital's key **cannot** affect another.
3. Keep patient name search correct across rotation, and make blind-index key
   rotation an explicit, separate decision.
4. Produce auditable proof of what was re-encrypted, when, and under which key.
5. Establish a standards-based export path (FHIR R4) that also satisfies ABDM,
   so the same compliance posture serves both DPDP and interoperability.

### Non-goals

- HSM-backed or KMS-hosted key custody. We have no KMS; the KEK is a file under
  `secrets/`. This is acknowledged as a weakness and mitigated by per-tenant DEKs
  (see §4.3), not pretended away.
- Per-write DEK rotation (see §4.3 for why that is wrong here).
- Replacing the in-house auth with an external identity provider.

## 3. Regulatory and standards findings

Researched 2026-10-01.

### 3.1 FHIR imposes no crypto requirement

HL7 FHIR R4's own security page states: *"there is nothing in FHIR that requires
or relies on any security being in place, or any particular security
implementation."* FHIR is a content and exchange model; security comes from
OAuth2/SMART on FHIR, TLS, and the storage layer. **Envelope encryption is
therefore fully compatible and needs no special-casing for FHIR.**

Two things FHIR *does* surface:

- **`AuditEvent`** is the FHIR audit resource. We already have `audit_log` with
  `action` / `resource` / `actor`, written by 8 services via `AuditLogger`. This
  is a foundation to grow into `AuditEvent`, not rebuild.
- **Provenance** — who asserted a value and when — maps onto existing
  `created_by` / `created_at` columns.

### 3.2 Envelope encryption is the established pattern

Consistent across Google Cloud KMS, IBM Key Protect, Azure, and Confluent CSFLE.
Rotating a KEK **re-wraps DEKs rather than re-encrypting data**, so patient rows
are never rewritten and there is no downtime. IBM: rotating a root key
"minimizes the amount of keys that you might need to manage... instead of rotating
and re-encrypting all of your DEKs."

Two vendor rules worth adopting verbatim:

- Generate a DEK locally; never store a plaintext DEK. Store the *wrapped* DEK
  beside the data it protects.
- A retired key version must still be able to **unwrap**, or old data becomes
  unreadable.

### 3.3 ABDM/ABHA

- The authoritative FHIR Implementation Guide for India is the **ABDM FHIR IG**,
  maintained by NRCES, currently **v6.5.0**, based on **FHIR R4**.
- ABDM requires health records to be exchanged as FHIR R4 in ABDM profiles.
  Profile `Composition`-based artefacts map closely onto entities we already have:
  `OPConsultRecord`, `PrescriptionRecord`, `DischargeSummaryRecord`,
  `DiagnosticReportRecord`, `WellnessRecord`, `InvoiceRecord`.
- **ABHA** is a 14-digit health identifier. An **ABHA Address**
  (`username@consent-manager`) is the handle used for consent-based sharing.
- MedOS is a **HIP** (Health Information Provider). It links records to ABHA and
  may share them onward as an **HIU** via the HIE-CM gateway.
- Sharing requires **explicit, purpose-bound, time-bound consent**. ABDM's Health
  Data Management Policy is explicitly consent-based.
- Terminology: SNOMED CT, ICD-10, LOINC.

### 3.4 DPDP

§8(5) reasonable security safeguards (≤ ₹250 crore penalty). §11–12 data-principal
rights to access, correction, erasure. §10 extra obligations for Significant Data
Fiduciaries. §8(7) storage limitation. DPDP Rules 2025 require a 90-day response
window for data-principal requests and a DPO contact point.

**Implication for key lifecycle:** the ability to rotate, and to *prove* the
rotation, is directly load-bearing evidence of §8(5) compliance. A re-wrap job
that logs what it changed is as much a compliance artefact as a security control.

## 4. Design

### 4.1 Overview

```
  KEK (file: secrets/pii-kek.txt, AES-256-GCM)
    │  wraps / unwraps
    ▼
  Wrapped DEK per tenant  (table: tenant_keys)
    │  unwrap
    ▼
  DEK (held in memory only)
    │  encrypts / decrypts
    ▼
  9 ciphertext columns (patients, encounters)

  Separately:
  Wrapped BI key per tenant → name_index HMAC   (independent lifecycle)
```

### 4.2 Components

**`PiiKeyringService`** — wraps and unwraps DEKs with the current KEK. Holds
multiple KEK versions so older wrapped DEKs stay readable. This is the only
component that touches the KEK.

**`TenantKeyService`** — resolves the DEK for a tenant, creating one on demand.
Caches unwrapped DEKs in memory for a bounded TTL. A tenant's DEK is stable
across writes (see §4.3).

**`BlindIndexKeyService`** — resolves the tenant's blind-index key, a *separate*
wrapped key. Its lifecycle is deliberately independent of the DEK's, so name
search can be held steady while the DEK rotates, or rotated separately if a
name-index key is suspected.

**`KeyRewrapService`** — the rotation job. Re-wraps every tenant's DEK under a
new KEK version. Rewrites **only the keyring rows**, never patient data. Online,
no downtime, resumable, idempotent.

**`PiiKeyRotationRunner`** — the operator-facing command. Dry-run by default;
`--apply` to commit; emits an audit record.

### 4.3 Two deliberate deviations from cloud-vendor practice

**Deviation 1 — one DEK per tenant, held stable across writes.**

Google's guidance is "generate a new DEK every time you write the data." That is
wrong for a hospital EMR: per-row DEKs destroy the ability to search or join, and
would make every row's key a separate unwrap on every read. We use **one DEK per
tenant**, stable across writes, which is what IBM and Azure describe.

The deployment is shared multi-tenant (confirmed: multiple hospitals per
deployment). Per-tenant DEKs are therefore what make the user's explicit
requirement achievable — *"we don't want to impact each other if one of the keys
needs to be rotated."* With per-tenant DEKs:

- Rotating hospital A's key rewrites hospital A's keyring rows only. Hospital B is
  untouched and never re-wrapped.
- Offboarding a tenant means destroying its DEK, which makes its PII permanently
  unreadable without touching anyone else's data.
- The blast radius of a compromised KEK is bounded to the tenants being
  re-wrapped at that moment, not the whole database.

**Deviation 2 — no KMS, so the KEK is a file.**

The KEK lives at `secrets/pii-kek.txt`, gitignored, mode 600. This is weaker
than an HSM and we treat it as such. Mitigations: per-tenant DEKs limit blast
radius; the keyring table stores only *wrapped* DEKs, so a database-only breach
yields nothing; `docs/operations.md` will document moving to AWS KMS / GCP KMS as
a future hardening step with the same interface.

### 4.4 Two distinct operations, frequently confused

These are different operations with very different costs. Conflating them is how
"rotate the key" turns into an outage.

| | **KEK re-wrap** | **DEK rotation** |
|---|---|---|
| Trigger | KEK rotation, routine hygiene, personnel change | DEK or blind-index key suspected compromised |
| What is rewritten | `tenant_keys` rows only | Every ciphertext row for that tenant |
| Patient data touched | No | Yes |
| Online / no downtime | Yes | Yes, in batches |
| Blast radius | Tenants being re-wrapped | One tenant |
| Frequency | Routine, scheduled | Rare, incident-driven |
| Reversible | Yes — restore old KEK file | No, unless a pre-rotation backup exists |

**KEK re-wrap is the routine operation** and is what §4.6 describes. It never
touches patient data, because the DEK itself is unchanged — only the wrapper
around it is. This is the property that makes rotation cheap, and it is why
envelope encryption was chosen.

**DEK rotation re-encrypts data** and is required only when the DEK itself is
suspect. It is scoped to one tenant, which is exactly the isolation the user
asked for: rotating hospital A's DEK leaves hospital B entirely untouched. A
backup must be taken first, because unlike a re-wrap this is not reversible by
restoring a key file.

### 4.5 Ciphertext versioning

Current ciphertext is `IV(12) ‖ ciphertext+tag(16)`, Base64, with no version
marker. Without one, neither operation is resumable or verifiable.

Note what the prefix must reference: **not** the KEK version. Because DEKs are
per-tenant, ciphertext is encrypted by a tenant's DEK and is entirely independent
of which KEK version wrapped it. A KEK re-wrap therefore changes no ciphertext at
all. The prefix identifies the **DEK generation** — needed only for DEK rotation,
so a partially-rotated tenant still reads correctly.

```
  kv1.d<dekGeneration>:<base64 IV ‖ ciphertext ‖ tag>
  e.g. kv1.d1:<base64…>   current tenant DEK
       kv1.d2:<base64…>   mid-rotation
```

`convertToEntityAttribute` parses the prefix and resolves that DEK generation for
the tenant. Values **without** a prefix are treated as legacy format and read via
the legacy path, so migration is incremental and a partially-migrated database
still serves reads. The legacy-plaintext tolerance already in `EncryptionUtil:99`
is preserved for pre-encryption rows.

A generation whose DEK has been destroyed must fail loudly rather than return
garbage, so the prefix parse distinguishes "unknown generation" from "legacy".

### 4.6 KEK re-wrap procedure

The routine rotation. Only `tenant_keys` rows change; patient data is never
rewritten.

1. **Dry run.** `KeyRewrapService` reports tenant count, DEKs to re-wrap, and any
   tenant whose DEK cannot be unwrapped. No writes.
2. **Generate** a new KEK version; write to `secrets/pii-kek.txt` (new version),
   retaining the previous version file. Startup requires that the file contains
   the current version plus every version needed to unwrap existing DEKs.
3. **Re-wrap** all tenant DEKs under the new KEK version. Resumable:
   already-rewrapped rows are skipped, so an interrupted run is completed by
   re-running it.
4. **Verify** every tenant's DEK unwraps under the new KEK and that a known
   patient's fields decrypt. This is the actual acceptance test.
5. **Restart** the application with the new KEK.
6. **Retire** the old KEK only after step 4 passes for all tenants. Never delete
   the old version first — that is what makes the whole thing reversible.

Rollback at any point: restore the previous KEK version file and restart. No data
was rewritten, so rollback is total.

### 4.7 DEK rotation procedure

Rare, incident-driven, and scoped to a single tenant. Because it rewrites
ciphertext, it is **not** reversible by restoring a key file.

1. Take and verify a backup. This is mandatory.
2. Increment the tenant's DEK generation. New writes immediately use `d2`;
   existing rows still read as `d1`.
3. Re-encrypt in batches: for each row, unwrap `d1`, decrypt, re-encrypt under
   `d2`, update `name_index` under the new BI key if that also rotated. Batch to
   keep transactions short; resumable via the version prefix.
4. Verify no `d1` ciphertext remains for that tenant, and that a sampled patient
   decrypts.
5. Destroy the `d1` DEK material only after step 4.

Tenant isolation is the acceptance criterion: another tenant's rows and keyring
rows must be byte-identical before and after.

### 4.8 FHIR interoperability

Expose a read-oriented FHIR R4 endpoint (`/fhir/R4`) over existing data, with:

- `CapabilityStatement` — required for conformance.
- `Patient` with `identifier` carrying the UHID and, once integrated, the ABHA
  number. `name`, `telecom`, `address` decrypted at serialisation time only.
- ABDM `Composition`-based artefacts for `OPConsultRecord`,
  `PrescriptionRecord`, `InvoiceRecord`, and `Appointment`, matching our existing
  entities.
- `AuditEvent` mapped from `audit_log` (`action` → `event.type`, `actor` →
  `agent.who`).
- `Provenance` from `created_by` / `created_at`.
- Terminology bindings: SNOMED CT, ICD-10, LOINC.

Auth is **SMART on FHIR** (OAuth2 + OIDC), layered on the existing tenant
scoping. ABHA linking and HIE-CM consent-based sharing are separate deliverables
that depend on NHA sandbox credentials and cannot be built speculatively.

**Design constraint on encryption:** FHIR serialisation needs plaintext PII. That
happens inside the FHIR resource assembler, after authorization and tenant
resolution, and is never written to a log. The blind index must **not** be
exported — it is an internal lookup accelerator.

### 4.9 ABHA integration

- Store the 14-digit ABHA number on `patients` as a nullable, uniquely-indexed
  column alongside `uhid`. Nullable because ABHA participation is **voluntary**
  under the Health Data Management Policy, and §19.3 requires no individual be
  denied access for lacking one.
- ABHA Address (`username@consent-manager`) stored separately, self-declared,
  retained under the patient's control per §15.6.
- Linking records to an ABHA requires recorded consent. Sharing onward to an HIU
  requires purpose- and duration-bound consent, enforced by a consent ledger, not
  a boolean flag.
- ABDM's own GDPR-equivalent caveat applies: ABHA is voluntary and revocable.

### 4.10 Data model changes

New migration `V2__pii_key_lifecycle.sql`:

- `tenant_keys(tenant_id PK, wrapped_dek BYTEA, wrapped_bi_key BYTEA,
  dek_generation INT NOT NULL, bi_key_generation INT NOT NULL,
  wrapped_kek_version INT NOT NULL, created_at, updated_at)`

  `wrapped_kek_version` records which KEK version currently wraps this row, which
  is what makes a re-wrap resumable and lets the dry run report progress.
- `key_rotations(id, operation TEXT, kek_version INT, dek_generation INT,
  tenant_id, started_at, completed_at, rows_rewritten INT, status, initiated_by,
  notes)`

  `operation` distinguishes `kek_rewrap` from `dek_rotation` (§4.4) and
  `tenant_id` is null for a fleet-wide KEK re-wrap.
- `abha_identifiers(patient_id PK/FK, abha_number VARCHAR(14) UNIQUE,
  abha_address, linked_at, consent_ref)`
- `consent_records(id, patient_id, purpose, granted_at, expires_at,
  revoked_at, abha_address, scope)`

All via the single-owner migration path (ADR-0005). V1 is frozen as of PR #80.

## 5. Error handling and failure modes

| Failure | Behaviour |
|---|---|
| KEK missing | Startup fails closed. No fallback to a default key. |
| KEK version cannot unwrap a DEK | Tenant's PII unreadable. Reported per tenant by the dry run, never silently skipped. Rotation aborts before writing. |
| Ciphertext version prefix unknown | Fail loudly on that field. A newer writer must not read older-unaware data silently. |
| Legacy plaintext value | Returned as-is, as today. Re-save encrypts it. |
| Rewrap interrupted | Resumable. Already-rewrapped rows skipped; re-run to completion. |
| Rewrap partially complete | Old KEK must be retained. Documented as a hard rule in `docs/operations.md`. |

## 6. Testing strategy

- **Unit** — `PiiKeyringService` round-trips a DEK; unwrap fails on a wrong KEK;
  version-prefixed ciphertext round-trips; legacy ciphertext still reads.
- **Blind index regression** — the specific bug this fixes: after a KEK rotation,
  `findByNameIndexOrderByCreatedAtDescIdDesc` must still return the patient. This
  test must fail against the pre-change code.
- **Tenant isolation** — rotating tenant A's key leaves tenant B's DEK byte-identical
  and B's rows decryptable. Directly asserts the user's stated requirement.
- **Rewrap idempotency and resumability** — run twice, second run is a no-op;
  interrupt and resume completes correctly.
- **Rollback** — restore the old KEK, assert full readability.
- **Integration** — real PostgreSQL, two tenants, real rows, full rotation.
- **FHIR** — `CapabilityStatement` validates; `Patient` serialises with
  decrypted PII and no blind index; `AuditEvent` maps from `audit_log`.
- **Security** — no plaintext PII or blind index in logs, FHIR responses, or
  audit rows.

## 7. Delivery plan

Sequenced so each step is independently mergeable and no step leaves the system
unable to read its own data. Tracked as GitHub issues; see §8.

**Phase 1 — Key lifecycle (unblocks DPDP §8(5))**

1. ADR-0009: envelope encryption, per-tenant DEKs, and the KEK-re-wrap vs
   DEK-rotation distinction.
2. `PiiKeyringService` + `tenant_keys` / `key_rotations` migration.
3. Ciphertext version prefix, with legacy read compatibility.
4. Per-tenant DEK resolution in the encryption path.
5. `BlindIndexKeyService` — independent wrapped BI key, plus the
   search-regression test that fails against current code.
6. `KeyRewrapService` — dry run plus resumable re-wrap. **Routine rotation.**
7. `PiiKeyRotationRunner` with `--apply`, and the rotation runbook in
   `docs/operations.md`.
8. Migrate existing ciphertext to the versioned format, and close the legacy
   plaintext branch once no legacy values remain.
9. Tenant-scoped DEK rotation (`DekRotationService`) — the rare,
   data-rewriting path, with mandatory backup. **Separate from #6 on purpose.**

**Phase 2 — FHIR R4 export (unlocks ABDM)**

10. FHIR server foundation, `CapabilityStatement`, base path.
11. `Patient` resource, ABHA-ready identifier.
12. `AuditEvent` mapping from `audit_log`.
13. ABDM `Composition` artefacts: OPConsult, Prescription, Invoice, Appointment.
14. SMART on FHIR authorization.

**Phase 3 — ABHA/ABDM (blocked on NHA sandbox credentials)**

15. `abha_identifiers` + ABHA linking with consent.
16. Consent ledger (`consent_records`).
17. ABHA creation/verification via ABDM sandbox APIs.
18. HIE-CM gateway integration (HIP/HIU), ABHA Address, QR scan-and-share.

**Phase 4 — Compliance evidence**

19. Data-principal access, correction, erasure endpoints (DPDP §11–12, 90-day).
20. Retention enforcement (DPDP §8(7)).
21. Key-rotation audit export as §8(5) evidence.

## 8. Issue breakdown

Created as GitHub issues, one per unit of work, ordered by dependency. The
user's instruction is explicit: **not to be implemented in a single pass.**
Each issue is self-contained, has acceptance criteria, and can be picked up
independently.

## 9. Open questions

1. **NHA sandbox credentials** are required before Phase 3 can start. Who holds
   them?
2. **SMART on FHIR** needs a registered OAuth2 AS with public keys. Self-host a
   small AS, or defer SMART and ship read-only FHIR behind the existing auth
   first? Recommendation: defer, ship Phase 2 without SMART, add SMART before any
   external access.
3. **Which FHIR version to pin.** ABDM IG 6.5.0 is on R4. Recommend pinning R4
   (v4.0.1) and tracking R5 separately.