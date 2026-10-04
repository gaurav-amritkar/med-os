# Architecture Review: TenantKeyHolder Depth Enhancement

**Candidate:** TenantKeyHolder depth enhancement (Strong recommendation)
**Date:** 2026-10-04
**Reviewer:** Architecture analysis via codebase inspection

## Summary

`TenantKeyHolder` is the single most critical class in the MedOS security module: it resolves per-tenant data encryption keys, manages blind-index keys, maintains an in-memory cache, handles KEK rotation, and serves as a static singleton gateway. It currently holds all of these concerns in one 491-line class, mixing resolution logic, caching policy, rotation mechanics, and test scaffolding. This makes it a **shallow module** — its surface area nearly equals its implementation — and a **God object** that concentrates six orthogonal responsibilities behind a single type. Callers reach across file boundaries to understand the contracts they depend on, and the InMemoryTenantKeyHolder test subclass duplicates cache logic that should be testable through a seam.

The proposal: deepen `TenantKeyHolder` by extracting each responsibility behind a named seam, introduce a `TenantKeyResolver` interface for the resolution+cache layer, keep `TenantKeyStore` as the persistence seam, and give `BlindIndexKeyService` its own interface. Two adapters at each seam make the seam real; the `InMemoryTenantKeyHolder` subclass becomes a legitimate adapter instead of a test hack.

---

## Files Involved

| File | Role |
|---|---|
| `backend/src/main/java/com/medos/security/TenantKeyHolder.java` | Primary target — shallow module |
| `backend/src/main/java/com/medos/security/TenantKeyStore.java` | Persistence interface (already a seam, needs depth) |
| `backend/src/main/java/com/medos/security/TenantKeyStoreJdbc.java` | Real adapter for TenantKeyStore |
| `backend/src/main/java/com/medos/security/BlindIndexKeyService.java` | Blind-index key resolution (currently package-private, no interface) |
| `backend/src/main/java/com/medos/security/KeyWrapCipher.java` | AES-GCM wrap/unwrap utility (well-factored already) |
| `backend/src/main/java/com/medos/security/KeyRewrapService.java` | Rotation orchestrator (thin, good) |
| `backend/src/main/java/com/medos/security/FeatureFlags.java` | Feature flag enforcement (unrelated) |
| `backend/src/main/java/com/medos/util/EncryptionUtil.java` | JPA AttributeConverter — static accessor into TenantKeyHolder |
| `backend/src/main/java/com/medos/util/BlindIndexUtil.java` | HMAC blind index — static accessor into TenantKeyHolder |
| `backend/src/main/java/com/medos/util/PiiCiphertextFormat.java` | Versioned ciphertext format — calls `TenantKeyHolder.get()` via `tenantResolver()` |
| `backend/src/main/java/com/medos/config/EncryptionConfig.java` | Boot wiring — constructs TenantKeyHolder, sets static instance |
| `backend/src/main/java/com/medos/config/PatientNameIndexBackfillRunner.java` | Startup runner — static accessor for `ensureDekExists` + `ensureBlindIndexKeyExists` |
| `backend/src/main/java/com/medos/service/PatientOnboardingService.java` | Write-path caller — static accessor |
| `backend/src/main/java/com/medos/service/PatientService.java` | Write-path caller — static accessor |
| `backend/src/main/java/com/medos/service/EncounterService.java` | Write-path caller — static accessor |
| `backend/src/test/java/com/medos/security/TenantKeyIsolationTest.java` | Tests DEK isolation via `InMemoryTenantKeyHolder` |
| `backend/src/test/java/com/medos/security/BlindIndexKeyIndependenceTest.java` | Tests blind-index independence via `FakeTenantKeyStore` |
| `backend/src/test/java/com/medos/security/TenantIsolationTest.java` | Integration test via real `TenantKeyStoreJdbc` |
| `backend/src/test/java/com/medos/security/FakeTenantKeyStore.java` | Test adapter for TenantKeyStore |
| `backend/src/test/java/com/medos/security/TenantKeyStoreJdbcNullableColumnTest.java` | Tests nullable BYTEA handling in TenantKeyStoreJdbc |
| `docs/adr/0001-tenant-encryption-seam-deepening.md` | ADR-0001 — deepened `key_rewrap_seam` at `TenantKeyStore` |
| `docs/adr/0009-envelope-encryption-per-tenant-deks.md` | ADR-0009 — envelope encryption design, references `PiiKeyringService` (not yet extracted) |
| `GLOSSARY.md` | Defines `tenant_isolation_seam`, `key_rewrap_seam`, and design vocabulary |

---

## Problem: Friction Evidence

### 1. God object — six responsibilities in one class

`TenantKeyHolder.java` simultaneously owns:

- **DEK resolution** (`dekFor`, `findDekOrNull`, `generationFor`) — `TenantKeyHolder.java:133-247`
- **Cache management** (`ConcurrentHashMap<UUID, CacheEntry>`, TTL eviction, seed-before-insert re-entrancy workaround) — `TenantKeyHolder.java:47-48`, `:75-84`, `:137-143`, `:294-296`
- **Blind-index key management** (lazy `BlindIndexKeyService` creation, `findBlindIndexKeyOrNull`, `ensureBlindIndexKeyExists`) — `TenantKeyHolder.java:48-49`, `:154-177`
- **KEK rotation state** (`previousKek` field, `withPreviousKek`, `unwrapForRotation`, `rewrapAll`) — `TenantKeyHolder.java:224-429`
- **Static singleton accessors** (`setInstance`, `get`, `reset`, `isInitialised`) — `TenantKeyHolder.java:42-43`, `:105-125`
- **Test scaffolding** (`InMemoryTenantKeyHolder` inner class, `corruptStoredDekForTesting`) — `TenantKeyHolder.java:452-490`

A maintainer fixing a cache eviction bug must read rotation logic. A maintainer refactoring blind-index keys must understand DEK creation ordering. The **locality** of each concern is destroyed.

### 2. Static singleton couples every caller to the class, not an interface

All seven production callers reach `TenantKeyHolder.get()` or `TenantKeyHolder.isInitialised()` as static method calls:

| Caller | Line(s) | Method(s) called |
|---|---|---|
| `EncryptionUtil.java:45` | `isInitialised()` |
| `EncryptionUtil.java:46` | `PiiCiphertextFormat.tenantResolver()` → `TenantKeyHolder.get().dekFor(null)` |
| `BlindIndexUtil.java:57-58` | `isInitialised()` |
| `BlindIndexUtil.java:62` | `get().findBlindIndexKeyOrNull(tenant)` |
| `BlindIndexUtil.java:97` | `isInitialised()` |
| `BlindIndexUtil.java:111` | `get().findBlindIndexKeyOrNull(tenant)` |
| `EncryptionConfig.java:39` | `setInstance(...)` |
| `PatientNameIndexBackfillRunner.java:52` | `derivedKeyLength()` → `findBlindIndexKeyOrNull` |
| `PatientNameIndexBackfillRunner.java:84-85` | `ensureDekExists()`, `ensureBlindIndexKeyExists()` |
| `PatientOnboardingService.java:24-25` | `isInitialised()`, `ensureDekExists()` |
| `PatientOnboardingService.java:31-32` | `isInitialised()`, `ensureBlindIndexKeyExists()` |
| `PatientService.java:68-69` | `isInitialised()`, `ensureDekExists()` |
| `PatientService.java:75-76` | `isInitialised()`, `ensureBlindIndexKeyExists()` |
| `EncounterService.java:64-65` | `isInitialised()`, `ensureDekExists()` |

No caller depends on an interface. Every call is bound to the concrete class. The **deletion test** for `TenantKeyHolder` fails catastrophically: removing it requires editing 14 production files and 5 test files. That is the definition of a shallow module — the interface (7 public methods) nearly equals the blast radius of the implementation.

### 3. Test subclass leaks implementation details

`InMemoryTenantKeyHolder` extends `TenantKeyHolder` and overrides three protected methods (`loadOrCreate`, `readStoredDek`, `rewrapAll`) plus one package-private (`corruptStoredDekForTesting`). This is a **subclass coupling** smell: the test subclass must know the internal structure of the class it extends, including which methods are `protected` vs `private`, and what fields to duplicate. The subclass duplicates `stored` and `generations` maps that are structurally identical to the cache logic in the parent — two separate implementations of "store a key by tenant UUID." A bug fixed in the parent's cache logic must be duplicated in the test subclass.

The `FakeTenantKeyStore` test adapter for `TenantKeyStore` is the correct pattern: it implements an interface, no subclass coupling, no duplication. `InMemoryTenantKeyHolder` should follow the same pattern.

### 4. BlindIndexKeyService has no interface

`BlindIndexKeyService` is package-private (`final class`, `TenantKeyHolder.java:168`), created inside `TenantKeyHolder.blindIndexKeys()`. It has its own cache, its own rotation state (`previousKek`), and its own wrap/unwrap logic — but no interface means:

- `BlindIndexKeyIndependenceTest.java:221-223` must instantiate it directly with raw byte arrays, bypassing `TenantKeyHolder`
- It cannot be swapped for a fake in tests without package access
- Its `rotateIndependently` method is a public operation that exists independently of `TenantKeyHolder`'s lifecycle, yet has no seam

### 5. Javadoc explains ordering constraints across files

The comments on `createDek` (`TenantKeyHolder.java:277-290`) explain that the key must be created *before* any entity reaches the persistence context, and that the cache must be seeded *before* the INSERT to avoid re-entrant stack overflow. This is critical safety knowledge that is documented inside `TenantKeyHolder` but must be replicated by every caller (`PatientService.java:63-67`, `PatientOnboardingService.java:19-23`, `EncounterService.java:59-63`, `PatientNameIndexBackfillRunner.java:80-86`). The constraint is enforced by convention across files, not by the type system or a single seam.

### 6. Previous KEK state leaks across two objects

`TenantKeyHolder` holds `previousKek` (field at line 224), and `BlindIndexKeyService` holds its own copy (field at line 37). When `withPreviousKek` is called, it nulls the `BlindIndexKeyService` so it will be rebuilt with the old KEK (`TenantKeyHolder.java:414-416`). This state duplication means:

- The rotation precondition ("the holder must be built with the previous KEK") is enforced by a side-effect (nulling a field), not by the interface
- If someone forgets to call `withPreviousKek`, `BlindIndexKeyService` silently uses the current KEK and unwrap fails with a misleading "corrupt material" error
- Two objects track the same conceptual state (which KEK version was current) independently

---

## Proposed Deepened Module

### Naming and Glossary

The GLOSSARY.md already defines:

- **`tenant_isolation_seam`** — where per-tenant key material (DEK/KEK) is resolved and isolated, satisfied by `TenantKeyStore`
- **`key_rewrap_seam`** — where `KeyRewrapService` rewraps tenant key wrappers under a new KEK version

These names remain correct. The proposal does not introduce new seams but deepens the existing ones. One addition to GLOSSARY.md is warranted:

- **`tenant_key_resolution_seam`** — the module boundary where a tenant's current DEK, generation, and blind-index key are resolved from whatever backing store, with caching policy, rotation awareness, and isolation guarantees. In MedOS, satisfied by the `TenantKeyResolver` interface behind which `TenantKeyHolder` (production) and `InMemoryTenantKeyStore` (test) are adapters.

No existing glossary terms are contradicted or superseded.

### Interface Sketch: `TenantKeyResolver`

```java
package com.medos.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a tenant's data encryption key and blind-index key, with caching
 * and rotation awareness.
 *
 * <p>This is the tenant_key_resolution_seam. All PII encryption and blind-index
 * computation flows through this interface. Callers never touch TenantKeyStore
 * or BlindIndexKeyService directly.
 *
 * <p>Invariants:
 * <ul>
 *   <li>resolveDek(tenantId) returns the same 32-byte key for the same tenant
 *       within the cache TTL, or throws IllegalStateException if no key exists.</li>
 *   <li>resolveDek refuses to resolve for a null/absent tenant — it never returns
 *       a shared key, which would re-introduce cross-tenant disclosure.</li>
 *   <li>findBlindIndexKey returns null when no index key exists; it never creates one.</li>
 *   <li>ensureBlindIndexKeyExists must be called after ensureDekExists — the index
 *       key is stored on the same tenant_keys row as the data key.</li>
 * </ul>
 *
 * <p>Error modes:
 * <ul>
 *   <li>IllegalStateException — no tenant in scope, no KEK configured, or stored
 *       key material is corrupt (wrong length).</li>
 *   <li>RuntimeException from the underlying store — propagated without swallowing,
 *       because a failed key creation must not leave a cached-but-unpersisted key.</li>
 * </ul>
 */
public interface TenantKeyResolver {

    /** Resolve the current DEK for tenantId. Creates one if absent. Never returns null. */
    byte[] resolveDek(UUID tenantId);

    /** The DEK for tenantId, or null if the tenant has no key yet. Never creates. */
    byte[] findDekOrNull(UUID tenantId);

    /** DEK generation for tenantId, loading from the store if not cached. */
    int generationFor(UUID tenantId);

    /** Blind-index key for tenantId, or null if none exists. Never creates. */
    byte[] findBlindIndexKeyOrNull(UUID tenantId);

    /** Ensure the acting tenant has a DEK. Idempotent. */
    void ensureDekExists();

    /** Ensure the acting tenant has a blind-index key. Must follow ensureDekExists. */
    void ensureBlindIndexKeyExists();

    /** Current KEK rotation version (generation counter for new writes). */
    int currentGeneration();

    /** Re-wrap all tenant key material under the current KEK. Requires previous KEK. */
    int rewrapAll();

    /** Prepare this resolver to read material written under a previous KEK. */
    TenantKeyResolver withPreviousKek(String previousKekBase64);

    /** Clear in-memory caches. */
    void clearCache();
}
```

### What stays behind the seam

Inside the `TenantKeyResolver` implementation (renamed from `TenantKeyHolder`):

- **DEK resolution logic** — `dekFor`, `findDekOrNull`, `generationFor`, cache lookup/insert/eviction
- **Blind-index key resolution** — delegates to a `BlindIndexKeyProvider` interface (see below)
- **KEK rotation orchestration** — `rewrapAll`, `withPreviousKek`, `previousKek` state
- **Cache policy** — TTL, seed-before-insert re-entrancy workaround, ConcurrentHashMap
- **Key wrapping/unwrapping** — delegates to `KeyWrapCipher` (already well-factored)
- **Static singleton accessors** — `setInstance`, `get`, `reset`, `isInitialised` remain as the bridge for `EncryptionUtil` (JPA converter cannot receive constructor deps), but become thin facades over the interface

### What moves out: `BlindIndexKeyProvider`

```java
package com.medos.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves and manages a tenant's blind-index key.
 *
 * <p>This is a sub-seam of tenant_key_resolution_seam. Its lifecycle is
 * independent of the data key: it can be rotated without re-encrypting ciphertext,
 * and the data key can be re-wrapped without touching the index.
 */
public interface BlindIndexKeyProvider {

    /** Blind-index key for tenantId, or null. Never creates. */
    byte[] findOrNull(UUID tenantId);

    /** Create an index key for tenantId if absent. */
    void ensureExists(UUID tenantId);

    /** Rotate to fresh key bytes, bumping generation. Stored digests stop matching. */
    void rotateIndependently(UUID tenantId);

    /** Re-wrap all index keys under the current KEK. Digests keep matching. */
    int rewrapAll();

    /** Clear in-memory cache. */
    void clearCache();
}
```

### Adapters

**Production adapter:** `TenantKeyHolder` implements `TenantKeyResolver` — the existing class, renamed or refactored to implement the interface. It composes a `BlindIndexKeyProvider` implementation (the extracted `BlindIndexKeyService` now implementing the interface) and a `TenantKeyStore` (existing interface, already has two adapters: `TenantKeyStoreJdbc` + `FakeTenantKeyStore`).

**Test adapter:** `InMemoryTenantKeyStore` implements `TenantKeyResolver` — the current `InMemoryTenantKeyHolder` inner class extracted to a top-level class that implements the interface, not extends the parent. Its `stored` map becomes its own field, not a duplicate of the parent's cache.

Two adapters at the `tenant_key_resolution_seam` = real seam (per codebase-design principle: "one adapter = hypothetical seam, two = real seam"). The current state has one adapter (`TenantKeyHolder` + `InMemoryTenantKeyHolder` as a subclass, not an independent adapter). This proposal makes it two.

### Seam placement diagram

```
                        tenant_key_resolution_seam
                         (TenantKeyResolver interface)
                         /                        \
               TenantKeyHolder                 InMemoryTenantKeyStore
               (production adapter)            (test adapter)
               /            \
  TenantKeyStore    BlindIndexKeyProvider
  (key_rewrap_seam)   (sub-seam)
  /              \
TenantKeyStoreJdbc  FakeTenantKeyStore
(real adapter)     (test adapter)
```

### What deletes or consolidates

1. **`InMemoryTenantKeyHolder` inner class** (`TenantKeyHolder.java:452-490`) — extracted to top-level `InMemoryTenantKeyStore` implementing `TenantKeyResolver`. No subclass coupling. Cache logic is in one place.
2. **Static accessor methods** (`setInstance`, `get`, `reset`, `isInitialised`) — remain on `TenantKeyResolverFactory` (a new thin static bridge class) so `EncryptionUtil` and `PiiCiphertextFormat` do not need constructor injection. The factory is the only class that holds a static instance.
3. **Duplicate cache logic** between `TenantKeyHolder.CacheEntry` and `BlindIndexKeyService.Entry` — both are `record CacheEntry(byte[] key, long loadedAt)` with identical TTL-check. Extract a shared `TimedCache` utility or accept the 4-line duplication as acceptable (YAGNI — it is 4 lines, not worth an abstraction).
4. **`corruptStoredDekForTesting`** — becomes a method on `InMemoryTenantKeyStore` (public, not package-private), since the test adapter is now a first-class implementation.

---

## Before-After Comparison

| Aspect | Before | After |
|---|---|---|
| Module depth | 7 public methods, ~490 LOC, no interface | Small interface (8 methods), implementation holds complexity, leverage across 14 call sites |
| Caller coupling | 14 production files call `TenantKeyHolder.get()` statically | Callers receive `TenantKeyResolver` via constructor or factory; `EncryptionUtil` still uses factory (JPA constraint) |
| Test adapter | `InMemoryTenantKeyHolder` extends `TenantKeyHolder`, overrides protected methods | `InMemoryTenantKeyStore` implements `TenantKeyResolver`, no subclass coupling |
| Blind index seam | Package-private `BlindIndexKeyService`, no interface | `BlindIndexKeyProvider` interface, swappable adapter |
| Rotation state | `previousKek` duplicated across `TenantKeyHolder` and `BlindIndexKeyService` | Rotation state lives in `TenantKeyHolder`; `BlindIndexKeyProvider` receives KEK via constructor when rebuilt |
| Locality | Cache bugs touch rotation code; rotation bugs touch cache code | Each concern in its own class behind its own interface |
| Deletion test | Removing `TenantKeyHolder` requires editing 14+ production files + 5 test files | Removing `TenantKeyResolver` interface requires editing the same callers, but the *interface* is 8 lines; the implementation is swappable |

---

## Test Impact

### Tests that survive unchanged

| Test | Why |
|---|---|
| `TenantKeyIsolationTest` | Uses `TenantKeyHolder.inMemoryForTesting()` → becomes `TenantKeyResolverFactory.setInstance(InMemoryTenantKeyStore)`. The test's assertions (distinct DEKs, cross-tenant isolation, stability, re-wrap preservation) are on the interface, so they transfer directly. |
| `BlindIndexKeyIndependenceTest` | Uses `FakeTenantKeyStore` + `TenantKeyHolder` → uses `FakeTenantKeyStore` + production `TenantKeyHolder` adapter. Assertions on the interface (`dekFor`, `rewrapAll`, `findBlindIndexKeyOrNull`) are unchanged. The direct `BlindIndexKeyService` instantiation at line 221-223 becomes a test of `BlindIndexKeyProvider` implementations. |
| `TenantIsolationTest` | Full integration test through `TenantKeyStoreJdbc`. Unaffected — the production adapter is the same JDBC-backed implementation. |
| `TenantKeyStoreJdbcNullableColumnTest` | Tests `TenantKeyStoreJdbc` directly. Unaffected — `TenantKeyStore` interface and its JDBC adapter are unchanged. |
| `FakeTenantKeyStore` | Unaffected — already implements `TenantKeyStore`. |

### Tests that change

| Test | Change |
|---|---|
| `TenantKeyIsolationTest` constructor calls | `TenantKeyHolder.inMemoryForTesting(KEK_B64)` → `new InMemoryTenantKeyStore(KEK_B64)`, then `TenantKeyResolverFactory.setInstance(...)`. Minimal. |
| `TenantKeyIsolationTest.corruptedWrappedDekFailsLoudly` | `corruptStoredDekForTesting` becomes a public method on `InMemoryTenantKeyStore`. |
| `BlindIndexKeyIndependenceTest` setup | `new TenantKeyHolder(KEK_B64, store)` → `new TenantKeyHolder(KEK_B64, store)` (same constructor, just implements interface). No change to the test body. |
| Any test that calls `TenantKeyHolder.reset()` | Calls `TenantKeyResolverFactory.reset()` instead. One-line rename. |

### Tests that become possible

- **Swap a slow adapter for a fast one in integration tests** — currently, integration tests must go through `TenantKeyStoreJdbc`. With the interface, a faster in-memory adapter could replace it for tests that need real wrap/unwrap but not JDBC overhead.
- **Test rotation with a mock `BlindIndexKeyProvider`** — currently, `BlindIndexKeyIndependenceTest` must instantiate `BlindIndexKeyService` directly to test `rotateIndependently`. With the interface, a fake could verify that `rewrapAll` delegates correctly without knowing the cache implementation.

---

## ADR Conflicts

### ADR-0001: "Deepen tenant_encryption_seam at key_rewrap_seam"

**No conflict.** ADR-0001 deepened the `key_rewrap_seam` by introducing `TenantKeyStore` as a real adapter (two adapters: `TenantKeyStoreJdbc` + `FakeTenantKeyStore`). This proposal deepens a *different* seam — `tenant_key_resolution_seam` — at a higher level. The `TenantKeyStore` interface from ADR-0001 remains exactly as specified and is unaffected.

### ADR-0009: "Envelope encryption with per-tenant data encryption keys"

**No conflict, but one naming gap to close.** ADR-0009 diagrams the KEK → wrapped DEK → DEK → ciphertext chain and references `PiiKeyringService` as the single component that touches the KEK (`docs/adr/0009-envelope-encryption-per-tenant-deks.md:110-111`). No such class exists yet — the KEK is handled inside `TenantKeyHolder` (field `kek` at line 44). This proposal does not extract a `PiiKeyringService`; that is a future step once the `tenant_key_resolution_seam` is established. ADR-0009's design intent (one stable DEK per tenant, separate blind-index key, KEK re-wraps without touching data) is preserved and reinforced by the interface design.

### Glossary consistency

GLOSSARY.md defines `tenant_isolation_seam` and `key_rewrap_seam`. The proposed `tenant_key_resolution_seam` is a new term that sits *above* both: it is the seam at which the resolver sits, and it *uses* `TenantKeyStore` (the `key_rewrap_seam`) internally. No existing terms are contradicted. Recommended addition:

```
- **tenant_key_resolution_seam**: the module boundary where a tenant's current DEK,
  generation, and blind-index key are resolved from whatever backing store, with
  caching policy, rotation awareness, and isolation guarantees. In MedOS, satisfied
  by the TenantKeyResolver interface; the production adapter is TenantKeyHolder.
```

---

## Recommendation Strength: Strong

This is a **strong** recommendation because:

1. The deletion test fails: `TenantKeyHolder` is referenced in 14 production files and 5 test files, making it impossible to change without touching the entire encryption stack.
2. The class already has two adapters in practice (`TenantKeyHolder` + `InMemoryTenantKeyHolder`) but they are connected by subclassing, not by a shared interface — the seam is nearly real but not yet.
3. `TenantKeyStore` was already deepened per ADR-0001. `TenantKeyHolder` is the natural next level up: it consumes `TenantKeyStore` but presents no interface of its own, so callers cannot depend on a stable contract.
4. The static singleton is not removable (JPA `AttributeConverter` constraint) but it *can* be a thin facade over an interface — which is the smallest change that yields the locality benefit.
5. No ADR is contradicted, and the glossary gains one term rather than changing existing ones.

The minimum viable first step is: extract `TenantKeyResolver` interface, make `TenantKeyHolder` implement it, extract `InMemoryTenantKeyStore` as a standalone implementation, and create `TenantKeyResolverFactory` as the static bridge. That is a 3-file change that makes the seam real and yields locality immediately. The `BlindIndexKeyProvider` extraction can follow as a second step.
