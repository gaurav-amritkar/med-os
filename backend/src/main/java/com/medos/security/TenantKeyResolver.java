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

    /**
     * Rotate the tenant's blind-index key independently of the data key.
     *
     * <p>Existing {@code patients.name_index} digests stop matching by construction;
     * a caller rotating it as part of a compromise response must re-index every named
     * patient. Kept separate from {@link #rotateDek(UUID)} because a suspected index
     * key does not justify rewriting ciphertext.
     */
    void rotateBlindIndexKey(UUID tenantId);

    /** Ensure the acting tenant has a DEK. Idempotent. */
    void ensureDekExists();

    /** Ensure the acting tenant has a blind-index key. Must follow ensureDekExists. */
    void ensureBlindIndexKeyExists();

    /** Current KEK rotation version (generation counter for new writes). */
    int currentGeneration();

    /**
     * The DEK for one specific generation of tenantId, or null when that generation
     * cannot be resolved (never issued, or never archived). The current generation
     * resolves through the normal path; superseded ones come from the archive. This
     * is what keeps ciphertext labelled {@code kv1.d<N>:} readable after the tenant
     * rotated to N+1.
     */
    byte[] dekForGenerationOrNull(UUID tenantId, int generation);

    /**
     * Rotate the tenant's data key: new DEK bytes, generation N → N+1, generation N
     * archived. A tenant without a key creates its first (generation 1, nothing to
     * archive). Returns the generation new writes will be labelled with.
     */
    int rotateDek(UUID tenantId);

    /**
     * Destroy an archived DEK generation after a compromise rotation has verified that
     * no ciphertext still names it.
     *
     * <p>This is the one irreversible step in the key lifecycle. It is on the resolver
     * seam rather than the store so a caller cannot reach past verification: the only
     * supported path is {@link DekRotationService}, which destroys only after a full
     * read-back pass. Returns 0 when the generation was already destroyed, so a resumed
     * run after a crash between verification and destruction is a no-op.
     */
    int destroyArchivedDek(UUID tenantId, int generation);

    /** Re-wrap all tenant key material under the current KEK. Requires previous KEK. */
    int rewrapAll();

    /** Prepare this resolver to read material written under a previous KEK. */
    TenantKeyResolver withPreviousKek(String previousKekBase64);

    /** Clear in-memory caches. */
    void clearCache();
}