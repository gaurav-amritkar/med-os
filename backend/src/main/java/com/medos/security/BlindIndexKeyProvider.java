package com.medos.security;

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