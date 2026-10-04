package com.medos.security;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a tenant's patient-name blind-index key, a separate wrapped key with its own
 * generation.
 *
 * <p>Named and shaped by design spec §4.2. Its lifecycle is deliberately independent of
 * the data key's: the index key can be rotated on its own if a name index is suspected,
 * and the data key can be re-wrapped without the search index having to follow it.
 *
 * <p>Sharing one secret between the two keys is what made rotation silently break search.
 * With a shared secret, re-wrapping changed the HMAC key, every {@code patients.name_index}
 * stopped matching, and search returned zero rows with no error — presenting as broken
 * search rather than as a key problem. Keeping a separately wrapped key means a re-wrap
 * changes only the wrapper around the same index key bytes, so the digests still match.
 */
class BlindIndexKeyService implements BlindIndexKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(BlindIndexKeyService.class);

    private final TenantKeyStore keyStore;
    private final byte[] kek;
    private final SecureRandom random;
    private final long cacheTtlMillis;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    /** Set during a KEK rotation so material written under the old KEK stays readable. */
    private byte[] previousKek;

    private record Entry(byte[] key, long loadedAt) {}

    BlindIndexKeyService(TenantKeyStore keyStore, byte[] kek, long cacheTtlMillis,
                         SecureRandom random) {
        this.keyStore = keyStore;
        this.kek = kek.clone();
        this.cacheTtlMillis = cacheTtlMillis;
        this.random = random;
    }

    BlindIndexKeyService withPreviousKek(byte[] previousKek) {
        this.previousKek = previousKek == null ? null : previousKek.clone();
        return this;
    }

    /**
     * The blind-index key for a tenant, or null if it has none.
     *
     * <p>Never creates. A search must not write: reads run inside transactions Spring may
     * roll back, so a key INSERT issued during a lookup is discarded and the tenant stays
     * permanently keyless.
     */
    public byte[] findOrNull(UUID tenantId) {
        long now = System.currentTimeMillis();
        Entry cached = cache.get(tenantId);
        if (cached != null && now - cached.loadedAt() < cacheTtlMillis) {
            return cached.key();
        }
        byte[] wrapped = keyStore.wrappedBiKeyOf(tenantId).orElse(null);
        if (wrapped == null) {
            return null;
        }
        byte[] key = requireKeyLength(unwrapForRotation(wrapped));
        cache.put(tenantId, new Entry(key, now));
        return key;
    }

    /**
     * Create the index key if the tenant has none.
     *
     * <p>Ordered after the data key by its callers: the index key lives on the same
     * {@code tenant_keys} row, so there is nothing to attach it to until that row exists.
     */
    public void ensureExists(UUID tenantId) {
        if (findOrNull(tenantId) != null) {
            return;
        }
        byte[] key = new byte[KeyWrapCipher.KEY_LENGTH];
        random.nextBytes(key);
        // Seed the cache before writing, so a flush caused by the write cannot re-enter
        // and observe the key as absent.
        cache.put(tenantId, new Entry(key, System.currentTimeMillis()));
        try {
            if (keyStore.insertBlindIndexKey(tenantId, wrap(key)) == 0) {
                throw new IllegalStateException(
                        "Cannot create a blind index key for tenant " + tenantId
                                + ": the tenant has no key material to store it on");
            }
        } catch (RuntimeException e) {
            cache.remove(tenantId);
            throw e;
        }
        log.info("Created a patient name blind index key for tenant {}", tenantId);
    }

    /**
     * Replace the index key with fresh key bytes and bump its generation, leaving the data
     * key and every stored ciphertext untouched.
     *
     * <p>Existing {@code name_index} digests stop matching by construction — that is the
     * point of a rotation suspected of index-key compromise — so the caller must re-index.
     * No patient row is rewritten here.
     */
    public void rotateIndependently(UUID tenantId) {
        byte[] key = new byte[KeyWrapCipher.KEY_LENGTH];
        random.nextBytes(key);
        int generation = keyStore.biKeyGenerationOf(tenantId).orElse(0) + 1;
        if (keyStore.replaceWrappedBiKey(tenantId, wrap(key)) == 0) {
            throw new IllegalStateException(
                    "Cannot rotate the blind index key for tenant " + tenantId
                            + ": the tenant has no key material to store it on");
        }
        keyStore.advanceBiKeyGeneration(tenantId, generation);
        cache.put(tenantId, new Entry(key, System.currentTimeMillis()));
        log.info("Rotated the patient name blind index key for tenant {} to generation {}",
                tenantId, generation);
    }

    /**
     * Re-wrap the index key under the current KEK. The key bytes are unchanged, so the
     * digests in {@code patients.name_index} keep matching and search survives.
     *
     * @return how many tenants were re-wrapped
     */
    public int rewrapAll() {
        int count = 0;
        for (UUID tenantId : keyStore.tenantIdsWithKeys()) {
            byte[] wrapped = keyStore.wrappedBiKeyOf(tenantId).orElse(null);
            if (wrapped == null) {
                continue;
            }
            byte[] key = requireKeyLength(unwrapForRotation(wrapped));
            keyStore.replaceWrappedBiKey(tenantId, wrap(key));
            cache.remove(tenantId);
            count++;
        }
        // Every row that could be read has now been re-wrapped, so reads must go back to
        // the current KEK. Leaving the previous KEK in place would make the material just
        // written unreadable, which looks exactly like the silent-search-breakage defect
        // this class exists to prevent.
        previousKek = null;
        cache.clear();
        return count;
    }

    public void clearCache() {
        cache.clear();
    }

    private byte[] wrap(byte[] key) {
        return KeyWrapCipher.wrap(key, kek, random);
    }

    /**
     * Unwrap with the KEK the material was written with.
     *
     * <p>When a previous KEK is supplied, that is the only one tried. Silently falling back
     * to the current KEK on failure hides the real problem behind a generic "corrupt
     * material" message, so the retry is the caller's choice.
     */
    private byte[] unwrapForRotation(byte[] wrapped) {
        return previousKek == null
                ? KeyWrapCipher.unwrap(wrapped, kek)
                : KeyWrapCipher.unwrap(wrapped, previousKek);
    }

    private static byte[] requireKeyLength(byte[] key) {
        if (key == null || key.length != KeyWrapCipher.KEY_LENGTH) {
            throw new IllegalStateException(
                    "Blind index key must be 32 bytes after unwrapping, was "
                            + (key == null ? "null" : key.length));
        }
        return key;
    }

    @Override
    public String toString() {
        // Never render key material; this reaches logs.
        return "BlindIndexKeyService[keyMaterial=redacted]";
    }

    /** Convenience for assertions and diagnostics that must not expose the key. */
    boolean hasCachedKeyFor(UUID tenantId) {
        return cache.containsKey(tenantId);
    }

    static byte[] copyOf(byte[] key) {
        return key == null ? null : Arrays.copyOf(key, key.length);
    }
}
