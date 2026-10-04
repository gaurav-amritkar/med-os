# Architecture Review: BlindIndexKeyService Deepening (Speculative)

**Candidate:** BlindIndexKeyService cache/structure deepening  
**File:** `backend/src/main/java/com/medos/security/BlindIndexKeyService.java`  
**Date:** 2026-10-04  
**Reviewer:** Claude Code (architecture review)

---

## Summary

`BlindIndexKeyService` is a package-private concrete class with no interface and no adapter layer. It is accessed only through `TenantKeyHolder`, which owns its entire lifecycle — construction, KEK-context mutation, and teardown. The class duplicates key-wrapping discipline (wrap/unwrap/length-check/cache-seed-before-write) already established in `TenantKeyHolder` and `KeyRewrapService`, though `KeyWrapCipher` already serves as the single extraction point for the actual AES-GCM routine.

This review challenges the speculative deepening. **The current shape is fine.** The duplication is bounded and intentional, the class has a well-defined seam boundary, and removing it would concentrate only wiring complexity — not meaningful logic — into `TenantKeyHolder`. Introducing an interface or consolidating the three re-wrap loops would add abstraction without a second adapter or a second caller that would benefit from it. ADR-0009 and ADR-0001 set the context but do not conflict with the current design.

---

## Files Involved

| File | Role |
|---|---|
| `backend/src/main/java/com/medos/security/BlindIndexKeyService.java` | The class under review |
| `backend/src/main/java/com/medos/security/TenantKeyHolder.java` | Sole owner/caller; lazy-creation, KEK rotation, rewrapAll delegation |
| `backend/src/main/java/com/medos/security/KeyWrapCipher.java` | Extracted wrap/unwrap primitive (shared by all three) |
| `backend/src/main/java/com/medos/security/KeyRewrapService.java` | Service-level orchestrator; re-wraps both DEK and BI key |
| `backend/src/main/java/com/medos/security/TenantKeyStore.java` | Adapter interface at the `key_rewrap_seam` |
| `backend/src/main/java/com/medos/util/BlindIndexUtil.java` | Consumer of `TenantKeyHolder.findBlindIndexKeyOrNull()` |
| `backend/src/test/java/com/medos/security/BlindIndexKeyIndependenceTest.java` | Direct instantiation of `BlindIndexKeyService` in tests |
| `backend/src/test/java/com/medos/security/FakeTenantKeyStore.java` | Test adapter for `TenantKeyStore` |
| `docs/adr/0001-tenant-encryption-seam-deepening.md` | Seam terminology and deletion-test framework |
| `docs/adr/0009-envelope-encryption-per-tenant-deks.md` | BI key independent lifecycle decision |

---

## Problem: Friction Evidence

### 1. No seam at all

`BlindIndexKeyService` is `final class` with package-private access (`BlindIndexKeyService.java:26`). It has no interface, no adapter, and no public API. Callers cannot reach it directly — they must traverse `TenantKeyHolder`:

- `TenantKeyHolder.findBlindIndexKeyOrNull()` (`TenantKeyHolder.java:154-156`) delegates to `blindIndexKeys().findOrNull()`.
- `TenantKeyHolder.ensureBlindIndexKeyExists()` (`TenantKeyHolder.java:164-166`) delegates to `blindIndexKeys().ensureExists()`.
- `TenantKeyHolder.rewrapAll()` (`TenantKeyHolder.java:382-405`) calls `blindIndexKeys().rewrapAll()` at line 397.

No other production class references `BlindIndexKeyService` directly. The only consumer in production is `BlindIndexUtil`, which calls `TenantKeyHolder.get().findBlindIndexKeyOrNull(tenant)` (`BlindIndexUtil.java:62`) — it never touches the service class.

The single test that directly instantiates it, `BlindIndexKeyIndependenceTest.biKeyRotationRewritesNoCiphertext()` (`BlindIndexKeyIndependenceTest.java:221-223`), constructs it manually with `new BlindIndexKeyService(store, kek, ttl, random)`. This is the only place outside `TenantKeyHolder` that touches the class.

### 2. State coupling to TenantKeyHolder's KEK rotation

`TenantKeyHolder` holds the `previousKek` field (line 224) and mutates `BlindIndexKeyService`'s state through two mechanisms:

- **Lazy recreation with KEK flag** (`TenantKeyHolder.java:168-177`): `blindIndexKeys()` checks `blindIndexKeysUsesCurrentKek`. When false, it creates a new `BlindIndexKeyService` instance, optionally calls `withPreviousKek()` (line 172), and resets the flag. This means the service instance is **recreated** every time the KEK context changes.
- **Nullification after rewrapAll** (`TenantKeyHolder.java:402-403`): After `rewrapAll()` completes, both `blindIndexKeys` and `blindIndexKeysUsesCurrentKek` are set to null/false, forcing recreation on next access.
- **Nullification in `withPreviousKek()`** (`TenantKeyHolder.java:415-416`): Same pattern — the service is discarded so the next `blindIndexKeys()` call rebuilds it with the old KEK.

The service itself holds `previousKek` as mutable state (`BlindIndexKeyService.java:37`), set via `withPreviousKek()` (line 49-52), and cleared by `rewrapAll()` (line 149). This is a two-way coupling: `TenantKeyHolder` mutates the service's internal state, and the service mutates its own state internally.

### 3. Duplicated key-wrapping discipline

Three classes contain the same loop pattern: iterate `keyStore.tenantIdsWithKeys()`, read wrapped key, unwrap with rotation-aware KEK, validate length, re-wrap under current KEK, clear cache.

**TenantKeyHolder.rewrapAll()** (`TenantKeyHolder.java:382-405`):
```java
for (UUID tenantId : keyStore.tenantIdsWithKeys()) {
    byte[] wrappedDek = keyStore.wrappedDekOf(tenantId).orElse(null);
    if (wrappedDek == null) continue;
    byte[] dek = requireDekLength(unwrapForRotation(wrappedDek));
    keyStore.replaceWrappedDek(tenantId, wrap(dek));
    cache.remove(tenantId);
    count++;
}
```

**BlindIndexKeyService.rewrapAll()** (`BlindIndexKeyService.java:133-152`):
```java
for (UUID tenantId : keyStore.tenantIdsWithKeys()) {
    byte[] wrapped = keyStore.wrappedBiKeyOf(tenantId).orElse(null);
    if (wrapped == null) continue;
    byte[] key = requireKeyLength(unwrapForRotation(wrapped));
    keyStore.replaceWrappedBiKey(tenantId, wrap(key));
    cache.remove(tenantId);
    count++;
}
```

**KeyRewrapService.rotate()** (`KeyRewrapService.java:88-101`):
```java
for (UUID id : keyStore.findTenantsPendingKekVersion(targetKekVersion)) {
    byte[] wrappedDek = keyStore.wrappedDekOf(id).orElse(null);
    if (wrappedDek == null) continue;
    byte[] dek = KeyWrapCipher.unwrap(wrappedDek, oldKek);
    keyStore.replaceWrappedDek(id, KeyWrapCipher.wrap(dek, newKek, random));
    // ... also re-wraps the BI key inline at lines 95-99
}
```

The `wrap()` / `unwrapForRotation()` / `requireXxxLength()` pattern is duplicated across `TenantKeyHolder` and `BlindIndexKeyService` (but `KeyRewrapService` uses `KeyWrapCipher` directly, bypassing the helper methods). `KeyWrapCipher` (`KeyWrapCipher.java`) already serves as the single extraction point for the AES-GCM primitive itself, so the wrap/unwrap implementation is not duplicated — but the loop orchestration, null-skipping, length validation, and cache eviction are.

The "seed cache before write" pattern (`BlindIndexKeyService.java:89-90`, `TenantKeyHolder.java:295`, and documented at `TenantKeyHolder.java:258-273`) is independently discovered and applied in three places.

---

## Deletion Test Result

**What happens if `BlindIndexKeyService` is deleted?**

The class contains approximately 170 lines. Its methods are:

| Method | Lines | What moves |
|---|---|---|
| `findOrNull()` | 61-74 | Cache lookup with TTL + store read + unwrap + cache seed. Moves into `TenantKeyHolder` as an inline block (similar to `findDekOrNull()` at lines 188-201). |
| `ensureExists()` | 82-102 | Create-if-absent + cache-seed-before-write + insert. Moves into `TenantKeyHolder` as an inline block (similar to `createDek()` at lines 291-306). |
| `rotateIndependently()` | 112-125 | Generate new key bytes + replace + advance generation + cache update. A new method on `TenantKeyHolder`, analogous to `dekFor()`'s generation tracking. |
| `rewrapAll()` | 133-152 | The re-wrap loop for BI keys. Moves into `TenantKeyHolder.rewrapAll()` at line 397 (currently delegates here already). |
| `clearCache()` | 154-156 | One line. Moves into `TenantKeyHolder.clearCache()`. |
| `wrap()` / `unwrapForRotation()` / `requireKeyLength()` | 158-182 | Private helpers. `wrap()` delegates to `KeyWrapCipher.wrap()`; `unwrapForRotation()` mirrors `TenantHolder.unwrapForRotation()` (line 427-429). `requireKeyLength()` mirrors `requireDekLength()` (line 231-238). These fold into existing `TenantKeyHolder` private methods. |
| `hasCachedKeyFor()` / `copyOf()` | 191-197 | Utility. `hasCachedKeyFor()` is unused in production code. `copyOf()` is a static utility. |

**Does this concentrate complexity or just move it?**

It **concentrates wiring complexity** in `TenantKeyHolder` but does not reduce total complexity. `TenantKeyHolder` already manages the DEK cache with the same TTL pattern, the same cache-seed-before-write discipline, and the same KEK-rotation unwrap logic. Adding the BI key methods inline would make `TenantKeyHolder` larger but not fundamentally more complex — the BI key is structurally a second key with the same lifecycle as the DEK, just independently rotatable.

The duplication of loop orchestration across `TenantKeyHolder.rewrapAll()` and `BlindIndexKeyService.rewrapAll()` is the only meaningful reduction — but `KeyRewrapService.rotate()` already has its own third loop (with KEK-version filtering), so a shared loop owner would need to serve three callers with different iteration criteria. Consolidating into one owner would require parameterizing the iteration, which adds abstraction without a clear second adapter or second caller set that would benefit.

**Verdict:** Deletion moves 170 lines into `TenantKeyHolder` (~20 more lines of wiring, ~150 lines of inlined logic). The class is already at its natural depth — it is a deep module for the BI key, just like `TenantKeyHolder` is a deep module for the DEK. The duplication is tolerable because `KeyWrapCipher` already eliminates the actual cryptographic duplication, and the loop patterns are simple enough that divergence risk is low.

---

## Proposed Change (or Justified No-Change)

### Recommendation: **No change warranted at this time.**

The evidence for leaving the shape as-is:

1. **One caller set, one seam.** `BlindIndexKeyService` is accessed only through `TenantKeyHolder`. Adding an interface would create a seam that has only one real adapter (the class itself), which ADR-0001 explicitly identifies as a "hypothetical seam" — it does not provide the leverage or locality benefits of a real seam until a second adapter exists (e.g., a test fake that is not `FakeTenantKeyStore`, or an alternative store backend).

2. **The duplication is bounded and extractable.** The actual cryptographic routine (`KeyWrapCipher.wrap`/`unwrap`) is already extracted. The duplicated patterns are loop orchestration and cache seeding — both are ~5-line patterns that are unlikely to diverge because the KEK rotation semantics are the same for both key types.

3. **The lazy-recreation pattern is stateful and context-dependent.** `BlindIndexKeyService` holds mutable `previousKek` that is set/cleared by `TenantKeyHolder` at rotation boundaries. This state is tightly coupled to the KEK rotation lifecycle, which lives in `TenantKeyHolder`. Extracting it behind an interface would require either exposing mutation methods on the interface (weakening encapsulation) or moving KEK-rotation state management into the interface layer (adding indirection without benefit).

4. **Deletion does not simplify.** As shown above, deleting the class moves 170 lines into `TenantKeyHolder`. The class is already a focused, well-documented module with a clear purpose (per ADR-0009, section "separately, with its own lifecycle"). Merging it back into `TenantKeyHolder` would conflate two keys that the design explicitly separates.

### If Deepening Were Warranted Later

If a future change introduces a second adapter path (e.g., a KMS-backed store, or a batch re-index operation that needs direct access), the interface would be:

```java
package com.medos.security;

import java.util.UUID;

/**
 * Resolves and manages the per-tenant blind-index key, with its own lifecycle
 * independent of the data encryption key (ADR-0009).
 *
 * <p>Callers access this only through {@link TenantKeyHolder}; the interface
 * exists so alternative store backends can be plugged in at the
 * key_rewrap_seam (ADR-0001).
 */
interface BlindIndexKeyHandle {

    /**
     * The current blind-index key for {@code tenantId}, or null if the tenant
     * has none. Never creates.
     */
    byte[] findOrNull(UUID tenantId);

    /**
     * Create a blind-index key if the tenant has none. Must be called after
     * the data key exists, because both keys share the same {@code tenant_keys} row.
     */
    void ensureExists(UUID tenantId);

    /**
     * Replace the index key with fresh bytes and advance its generation.
     * The data key and stored ciphertext are untouched — existing digests
     * stop matching by construction, so the caller must re-index.
     */
    void rotateIndependently(UUID tenantId);

    /**
     * Re-wrap every tenant's index key under the current KEK. The key bytes
     * are unchanged, so existing {@code name_index} digests keep matching.
     *
     * @return how many tenants were re-wrapped
     */
    int rewrapAll();

    /** Flush the in-memory cache. */
    void clearCache();

    /** Whether a key is currently cached for {@code tenantId}. */
    boolean hasCachedKeyFor(UUID tenantId);
}
```

Consolidating the three re-wrap loops (`TenantKeyHolder.rewrapAll()` lines 382-405, `BlindIndexKeyService.rewrapAll()` lines 133-152, `KeyRewrapService.rotate()` lines 87-101) into one owner would require parameterizing the iteration by:
- Which store accessor to call (`wrappedDekOf` vs `wrappedBiKeyOf`)
- Which replace method to call (`replaceWrappedDek` vs `replaceWrappedBiKey`)
- Which length validator to apply (`requireDekLength` vs `requireKeyLength`)
- Whether to also touch the BI key (only `KeyRewrapService` does both in one loop)

This is a 4-parameter template method or strategy, applied to three loops with different iteration sets (`tenantIdsWithKeys` vs `findTenantsPendingKekVersion`). The abstraction cost outweighs the duplication cost given that the loops are stable (KEK rotation logic changes infrequently) and already share `KeyWrapCipher` for the actual encryption.

---

## Test Impact

### Direct instantiation

`BlindIndexKeyService` is instantiated directly in one test:

- `BlindIndexKeyIndependenceTest.biKeyRotationRewritesNoCiphertext()` (`BlindIndexKeyIndependenceTest.java:221-223`): `new BlindIndexKeyService(store, kek, ttl, new SecureRandom())`

If the class were deleted and its logic moved into `TenantKeyHolder`, this test would need to call `holder.rotateBlindIndexKeyIndependently(tenant)` or equivalent through `TenantKeyHolder`. The test validates a specific behavior (index key rotation leaves DEK untouched) that would still be testable — the surface area of the test does not shrink, only the instantiation path changes.

### Indirect access through TenantKeyHolder

All other tests access the BI key through `TenantKeyHolder`:
- `BlindIndexKeyIndependenceTest` uses `holder.ensureBlindIndexKeyExists()` and `holder.findBlindIndexKeyOrNull()` (implicitly via `BlindIndexUtil`) throughout.
- `PatientServiceTest` uses `TenantKeyHolder.get().ensureBlindIndexKeyExists()` (`PatientServiceTest.java:87`).
- `PatientNameContainsSearchTest` uses `new BlindIndexUtil()` which calls through `TenantKeyHolder`.

These tests are unaffected by any structural change to `BlindIndexKeyService` as long as `TenantKeyHolder`'s public API remains stable.

### No new tests required for no-change

Since the recommendation is to leave the code as-is, no test changes are needed.

---

## ADR Conflicts

### ADR-0009 (Envelope encryption with per-tenant DEKs)

**No conflict.** ADR-0009 explicitly calls for the blind index key to have "a lifecycle independent of the DEK" (line 65) and its own wrapped key. `BlindIndexKeyService` implements exactly this: it is a separate class with separate generation tracking (`biKeyGenerationOf`), separate re-wrap logic (`rewrapAll()`), and independent rotation (`rotateIndependently()`). The ADR says "the blind index must be separated from the PII secret" (line 148) — the separation exists and works.

### ADR-0001 (Deepen tenant_encryption_seam at key_rewrap_seam)

**No conflict, but the seam vocabulary does not apply here.** ADR-0001 defines the `key_rewrap_seam` at the `TenantKeyStore` adapter boundary, where `KeyRewrapService` operates. `BlindIndexKeyService` sits *inside* that seam — it is a consumer of `TenantKeyStore`, not a module at the seam boundary. The ADR's deletion test ("removing `TenantKeyStore` would re-surface tenant-key logic across every rotation caller") passes for `TenantKeyStore` and would also pass for `BlindIndexKeyService` — both are deep modules. But the ADR does not mandate deepening every deep module into an interface; it deepens the seam where multiple adapters exist. There is currently one adapter (`TenantKeyStoreJdbc`) plus one fake (`FakeTenantKeyStore`), and both serve all three consumers (`TenantKeyHolder`, `BlindIndexKeyService`, `KeyRewrapService`) through the same interface.

### No other ADRs reference `BlindIndexKeyService` directly.

---

## Recommendation Strength

**Weak recommendation to maintain current shape.** This is a speculative candidate that does not hold up under the deletion test or the seam analysis. The class is well-scoped, has a single caller, and the duplication it contains is already mitigated by `KeyWrapCipher`. Deepening would add an interface and adapter layer for a seam that currently has one real adapter — which ADR-0001 identifies as a hypothetical seam, not a real one.

Revisit if:
- A second store adapter for BI keys is introduced (then the interface becomes warranted).
- `KeyRewrapService.rotate()` grows additional BI-key-specific logic (then consolidating the re-wrap loop becomes justified).
- The KEK rotation path diverges between DEK and BI key handling (then the shared `unwrapForRotation` pattern needs to be parametrized).

None of these conditions are present today.
