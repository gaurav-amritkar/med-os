# ADR-0001: Deepen tenant_encryption_seam at key_rewrap_seam

## Status
Accepted

## Context
`KeyRewrapService` is a shallow module: its interface nearly equals its implementation, and `TenantKeyStore` (the adapter behind the seam) does not yet exist as a real type—only a hypothetical pass-through. One hypothetical adapter means no real seam (per codebase-design principle: "one adapter = hypothetical seam, two = real").

Investigation confirms:
- `TenantKeyHolder` provides per-tenant DEK isolation (`dekFor(UUID tenantId)`)
- `DekResolver` interface exists but is unconnected to the re-wrap path
- PII encryption path (`EncryptionUtil`, `PiiCiphertextFormat`) is separate and unaffected
- No external callers of `KeyRewrapService`—only internal self-use

## Decision
Introduce `TenantKeyStore` as a **real adapter** at the `key_rewrap_seam`, with a small interface:
- `tenantIdsWithKeys()` → all tenant UUIDs with stored keys
- `findTenantsPendingKekVersion(int target)` → tenants behind target KEK
- `wrappedDekOf(UUID)` / `wrappedBiKeyOf(UUID)` → fetch wrappers
- `replaceWrappedDek(UUID, byte[])`, `replaceWrappedBiKey(UUID, byte[])`, `setKekVersion(UUID, int)` → write back

This makes the seam real (two adapters: DB-backed + in-memory test fake), concentrates complexity inside the module (depth), and gives callers leverage + maintainers locality. The deletion test passes: removing `TenantKeyStore` would re-surface tenant-key logic across every rotation caller.

PII encrypt/decrypt path remains at a separate `tenant_isolation_seam` (`TenantKeyHolder` / `EncryptionUtil`). No change to `DekResolver`, `TenantConfig`, or entity-level `@Convert` usage.

## Consequences
- `KeyRewrapService` becomes a thin orchestrator over the deep module
- Test adapter enables fast unit tests for rotation logic without DB
- Future KEK rotation features (pause, resume, partial) extend the interface, not the callers
- Glossary updated with `tenant_isolation_seam` and `key_rewrap_seam`