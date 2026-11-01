# Archived DEK Retirement: Safe Cleanup of Old Data Encryption Key Generations

* **Author:** MedOS team
* **Date:** 2026-10-31

## Overview

Implement a safe procedure to retire archived Data Encryption Key (DEK) generations after per-tenant DEK rotation. Currently, rotation retains old DEK generations forever so that "late commits" (data encrypted with the old key but written after rotation) remain readable. Old generations accumulate in `tenant_dek_history` and `tenant_keys` without a cleanup path. This brief defines how to identify unused generations, attest a backup, and retire them safely.

## Analysis

**Contrarian / YAGNI** — Does this feature need to exist?
Per-tenant DEK rotation already exists and keeps old generations for late-commit readability. The counter-argument against retirement is that old ciphertext is harmless: it occupies little space and cannot be decrypted without the archived key. The counter-argument for retirement is attack surface and compliance: every retained key generation is a secret that must be protected, backed up, and audited. Regulations may require key minimization. Accumulating generations forever also makes key management harder to reason about over time. Retirement is justified, but only if it is provably safe — no data must reference the retired generation.

**Cost, Quota & Token Economics** — What is the footprint?
Retirement requires scanning encrypted columns across `patients` and `encounters` to determine which DEK generations are still in use. This is a one-time read operation per tenant, plus occasional re-scans. Storage savings are modest (small metadata rows), but operational clarity improves. No LLM or token cost.

**UX & Developer Ergonomics** — Is it intuitive?
Transparent to application users. Operations staff run a retirement command or procedure per tenant. The command must require an explicit backup attestation and a target tenant, making it deliberate and hard to run by accident.

**Migration & Backward Compatibility** — Does it break anything?
No schema changes. `tenant_dek_history` already stores archived DEKs. Retirement deletes rows from archive tables only after verifying no ciphertext references them. If a bug causes premature deletion, data encrypted under that generation becomes unrecoverable — fail-closed design is essential.

**Observability & SRE Debuggability** — Is it traceable and actionable?
Metrics on archive size, generations in use, and retirement operations help oncall understand retention state. Every retirement is recorded in `key_rotations` (already the audit log for key operations). Logs should record which generations were retired, when, and by whom.

**Long-Term Tech Debt & Maintenance** — Will it hurt in two years?
Adds a rotation lifecycle concept: active, archived, retired. This state machine must be carefully implemented and tested. Poorly implemented retirement risks data loss. Well-implemented retirement reduces long-term key-management complexity.

## Goals vs. Non-Goals

**In-Scope Goals**
- A procedure to list DEK generations per tenant and identify which are still referenced by ciphertext in `patients` and `encounters`.
- A backup attestation step before any retirement (verified backup exists).
- A retirement command that deletes archived DEKs and history rows only for generations with zero references.
- Record every retirement in `key_rotations` with tenant, generation, timestamp, and operator.
- Fail closed: if any ciphertext references a generation, do not retire it; report instead.
- Idempotent: running the procedure twice has no extra effect.

**Out-of-Scope Non-Goals**
- Automatic retirement without human attestation (must remain manual or explicitly scheduled with approval).
- Deleting `tenant_keys` rows (the current wrapped DEK must remain).
- Re-encrypting all patient data under a new key (that is DEK rotation, already implemented).
- Retiring KEK generations (separate concern from DEK retirement).

## Alternatives Weighed

**Option A: Manual safe retirement with backup attestation (chosen)**
Reason: Guarantees no data loss by requiring proof of backup and zero-references verification before deletion. Fits the existing fail-closed security posture.

**Option B: Never retire, keep all generations forever (status quo)**
Reason rejected: Accumulating archive rows increases key-management surface and does not align with key-minimization principles. Already flagged as a known gap.

**Option C: Time-based automatic retirement (e.g., retire after 90 days)**
Reason rejected: Unsafe. Data encrypted under an old generation might still be written (late commits) beyond 90 days, making it unrecoverable.

## Key Decisions & Staging for Next Steps

1. A generation is "in use" if any row in `patients` or `encounters` contains ciphertext prefixed with that generation (format `kv1.d<generation>:`).
2. Retirement requires a tenant identifier and a backup attestation (backup ID or checksum reference) as inputs.
3. The procedure runs outside the normal request path (admin CLI or one-off backend job), not per-request.
4. `tenant_dek_history` rows are deleted only after confirming zero references across all encrypted columns for that tenant.
5. Next step: implement the reference scanner and retirement procedure as a backend tool or management command, test against staging with synthetic data, then document the runbook in `docs/operations.md`.
