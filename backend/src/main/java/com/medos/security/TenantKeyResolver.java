package com.medos.security;

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