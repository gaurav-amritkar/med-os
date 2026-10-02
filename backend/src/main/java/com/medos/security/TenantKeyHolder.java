package com.medos.security;

import com.medos.entity.Tenant;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the data encryption key for the acting tenant.
 *
 * <p>There is a static accessor because {@code EncryptionUtil} is a JPA
 * {@code AttributeConverter}, which Hibernate instantiates itself and cannot be given
 * constructor dependencies. The indirection is safe only because the key resolved is
 * always the one for {@link TenantContext}'s tenant, never a process-wide default.
 * That is the property {@code TenantKeyIsolationTest} pins down.
 *
 * <p>DEKs are per tenant and stable across writes: a new DEK per write would break
 * name search and any join across encrypted columns, which is the record-linking
 * the application exists to perform.
 *
 * <p>The KEK is touched here and nowhere else. Unwrapped DEKs are cached with a
 * bounded TTL so a request does not hit the database per field; a tenant's
 * ciphertext cannot be read with another tenant's key, so a stale cache entry is a
 * performance cost, not a disclosure.
 */
@Slf4j
public class TenantKeyHolder {

    private static final int DEK_LENGTH = 32;
    private static final int WRAP_IV_LENGTH = 12;
    private static final int WRAP_TAG_BITS = 128;

    private static volatile TenantKeyHolder instance;

    private final byte[] kek;
    private final TenantKeyStore keyStore;
        private final SecureRandom random = new SecureRandom();
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private BlindIndexKeyService blindIndexKeys;
    private boolean blindIndexKeysUsesCurrentKek = true;
    private final long cacheTtlMillis;
    private final org.springframework.transaction.support.TransactionTemplate txTemplate;

    public TenantKeyHolder(String base64Kek, TenantKeyStore keyStore) {
        this(base64Kek, keyStore, DurationConst.DEK_CACHE_TTL_MILLIS, null);
    }

    public TenantKeyHolder(String base64Kek, TenantKeyStore keyStore, long cacheTtlMillis) {
        this(base64Kek, keyStore, cacheTtlMillis, null);
    }

    public TenantKeyHolder(String base64Kek, TenantKeyStore keyStore,
                           org.springframework.transaction.PlatformTransactionManager txManager) {
        this(base64Kek, keyStore, DurationConst.DEK_CACHE_TTL_MILLIS, txManager);
    }

    public TenantKeyHolder(String base64Kek, TenantKeyStore keyStore, long cacheTtlMillis,
                           org.springframework.transaction.PlatformTransactionManager txManager) {
        this.kek = decodeKek(base64Kek);
        this.keyStore = keyStore;
        this.cacheTtlMillis = cacheTtlMillis;
        this.txTemplate = txManager == null ? null
                : new org.springframework.transaction.support.TransactionTemplate(txManager);
    }

    private static final class DurationConst {
        static final long DEK_CACHE_TTL_MILLIS = 5 * 60 * 1000L;
    }


    private record CacheEntry(byte[] dek, int generation, long loadedAt) {
        boolean isFresh(long now, long ttl) {
            return now - loadedAt < ttl;
        }
    }

    private static byte[] decodeKek(String base64Kek) {
        if (base64Kek == null || base64Kek.isBlank()) {
            throw new IllegalStateException(
                    "PII key-encryption key is not configured. Set PII_ENCRYPTION_KEY "
                            + "(e.g. `openssl rand -base64 32`) so tenant keys can be unwrapped.");
        }
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(base64Kek);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PII key-encryption key is not valid Base64", e);
        }
        if (bytes.length != DEK_LENGTH) {
            throw new IllegalStateException(
                    "PII key-encryption key must be 32 bytes (256 bits) after Base64 decode");
        }
        return bytes;
    }

    public static void setInstance(TenantKeyHolder holder) {
        instance = holder;
    }

    public static TenantKeyHolder get() {
        TenantKeyHolder local = instance;
        if (local == null) {
            throw new IllegalStateException(
                    "Tenant key holder is not initialised. It must be configured at startup "
                            + "before any PII field is read or written.");
        }
        return local;
    }

    public static boolean isInitialised() {
        return instance != null;
    }

    public static void reset() {
        instance = null;
    }

    /**
     * Resolve the DEK for the acting tenant, creating one on first use.
     *
     * <p>No tenant means no key. Falling back to a shared key here would silently
     * re-introduce the cross-tenant disclosure this class exists to prevent.
     */
    public byte[] dekFor(UUID tenantId) {
        UUID acting = requireTenant(tenantId);
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(acting);
        if (cached != null && cached.isFresh(now, cacheTtlMillis)) {
            return cached.dek();
        }
        int generation = loadOrCreate(acting);
        byte[] dek = readStoredDek(acting);
        cache.put(acting, new CacheEntry(dek, generation, now));
        return dek;
    }

    /**
     * The blind-index key for a tenant, or null if it has none.
     *
     * <p>Delegates to {@link BlindIndexKeyService}: a separate wrapped key with its own
     * generation, so the index key is not tied to the data key. Sharing one secret is what
     * made a rotation silently break name search — the digests stopped matching and search
     * returned nothing, with nothing reporting an error.
     */
    public byte[] findBlindIndexKeyOrNull(UUID tenantId) {
        return blindIndexKeys().findOrNull(requireTenant(tenantId));
    }

    /**
     * Ensure a blind-index key exists for the acting tenant.
     *
     * <p>Separate from {@link #ensureDekExists()} so an index key can be rotated without
     * touching the data key, and vice versa.
     */
    public void ensureBlindIndexKeyExists() {
        blindIndexKeys().ensureExists(requireTenant(null));
    }

    private BlindIndexKeyService blindIndexKeys() {
        if (blindIndexKeys == null || !blindIndexKeysUsesCurrentKek) {
            blindIndexKeys = new BlindIndexKeyService(keyStore, kek, cacheTtlMillis, random);
            if (previousKek != null) {
                blindIndexKeys.withPreviousKek(previousKek);
            }
            blindIndexKeysUsesCurrentKek = true;
        }
        return blindIndexKeys;
    }

    /**
     * The DEK for the acting tenant, or null if it has none.
     *
     * <p>Never creates. A read must not write: reads run in transactions Spring may
     * treat as read-only, so an insert issued while decrypting a query result is
     * discarded, and the symptom is a tenant permanently without a key — every read
     * re-attempts the insert and every read fails. Use {@link #ensureDekExists()} on
     * the write path, or treat null as "this tenant has no key yet".
     */
    public byte[] findDekOrNull(UUID tenantId) {
        UUID acting = requireTenant(tenantId);
        CacheEntry cached = cache.get(acting);
        long now = System.currentTimeMillis();
        if (cached != null && cached.isFresh(now, cacheTtlMillis)) {
            return cached.dek();
        }
        if (keyStore.dekGenerationOf(acting).isEmpty()) {
            return null;
        }
        byte[] dek = readStoredDek(acting);
        cache.put(acting, new CacheEntry(dek, 1, now));
        return dek;
    }

    /**
     * The acting tenant, or a refusal.
     *
     * <p>Falling back to a shared key here would silently re-introduce the
     * cross-tenant disclosure this class exists to prevent.
     */
    protected UUID requireTenant(UUID tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantContext.getTenantId().orElseThrow(() -> new IllegalStateException(
                "No tenant is in scope, so no PII key can be resolved. Refusing to use a "
                        + "shared key, which would make every tenant's PII mutually readable."));
    }

    /** Read the tenant's DEK from wherever it is stored, already unwrapped. */
    protected byte[] readStoredDek(UUID tenantId) {
        return requireDekLength(unwrapStoredDek(tenantId));
    }

    /** The KEK that material was written with, when known. Null until set. */
    private byte[] previousKek;

    /**
     * A DEK that is not 32 bytes is corruption, not a short key to tolerate.
     * Passing it on would make AES fail later with an opaque error, or worse, make
     * a zero-padded key look valid.
     */
    protected static byte[] requireDekLength(byte[] dek) {
        if (dek == null || dek.length != DEK_LENGTH) {
            throw new IllegalStateException("Tenant data key has the wrong length: expected "
                    + DEK_LENGTH + " bytes, got " + (dek == null ? "null" : dek.length)
                    + ". Stored key material is corrupt.");
        }
        return dek;
    }

    public int generationFor(UUID tenantId) {
        UUID acting = requireTenant(tenantId);
        CacheEntry cached = cache.get(acting);
        if (cached != null && cached.isFresh(System.currentTimeMillis(), cacheTtlMillis)) {
            return cached.generation();
        }
        return loadOrCreate(acting);
    }

    /**
     * Load the tenant's wrapped DEK, generating and storing one if absent.
     *
     * <p>Deliberately NOT annotated {@code @Transactional}. It is called by plain
     * self-invocation from {@link #dekFor}, so a proxy-based annotation would never
     * apply, and worse, the native INSERT would join the caller's unit of work. The
     * insert then flushes the caller's pending entities — which are themselves
     * encrypted, so each flush re-enters this method and the stack overflows. The
     * cache is seeded immediately after the insert, so the re-entrant call is served
     * without touching the store.
     */
    protected int loadOrCreate(UUID tenantId) {
        return keyStore.dekGenerationOf(tenantId)
                .orElseGet(() -> createDek(tenantId));
    }

    /**
     * Generate and store a DEK for a tenant that has none.
     *
     * <p>The cache is seeded <b>before</b> the insert, not after. The insert flushes
     * the caller's pending entities, and those entities are themselves encrypted, so
     * the flush calls back into {@link #dekFor}. Seeding afterwards left that re-entrant
     * call finding no cached key, generating another DEK, flushing again, and recursing
     * until the stack overflowed. Seeding first makes the re-entrant call a cache hit.
     *
     * <p>No existence probe on the tenant: the FK on {@code tenant_id} is the real
     * guarantee, and a SELECT here would trigger the same flush cycle.
     */
    /**
     * Generate and store a DEK for a tenant that has none.
     *
     * <p>Must be called <b>before</b> any entity holding encrypted fields is added to the
     * persistence context. The reason is ordering, not aesthetics: a key insert issued
     * from inside the converter runs while Hibernate is still flushing the caller's
     * entities, so the key row lands between two of the caller's INSERTs. The
     * re-entrant converter call then produces a second INSERT for the same entity and
     * the database rejects it on the unique uhid index — reported as a constraint
     * violation with no mention of keys, which is a genuinely misleading symptom.
     *
     * <p>The cache is seeded before the insert, so a re-entrant call is served without
     * touching the store.
     */
    private int createDek(UUID tenantId) {
        byte[] dek = new byte[DEK_LENGTH];
        random.nextBytes(dek);
        // Seed first, so a flush triggered by insert() cannot re-enter and loop.
        cache.put(tenantId, new CacheEntry(dek, 1, System.currentTimeMillis()));
        try {
            insertIsolated(tenantId, wrap(dek));
        } catch (RuntimeException e) {
            // Do not leave a cached key that was never persisted, or the tenant would
            // encrypt under a DEK nothing else can unwrap.
            cache.remove(tenantId);
            throw e;
        }
        log.info("Created a data encryption key for tenant {}", tenantId);
        return 1;
    }

    /**
     * Ensure a DEK exists for the acting tenant, without reading it.
     *
     * <p>Called by services before they add or save an entity that carries encrypted
     * fields. This is the supported way to avoid the ordering problem above: the key
     * exists, so the converter never has to create one mid-flush.
     */
    public void ensureDekExists() {
        dekFor(null);
    }

    /**
     * The store may require an ambient transaction, and a converter can be invoked
     * outside one (tests, and any non-transactional caller). Wrapping the insert keeps
     * key creation independent of the caller's unit of work.
     */
    /**
     * Insert the key row in the <b>caller's</b> transaction.
     *
     * <p>Deliberately not wrapped in a REQUIRES_NEW template. A separate transaction
     * runs on a different connection, which cannot see the caller's uncommitted
     * {@code tenants} row, so the foreign key fails; worse, the failure is swallowed
     * and the tenant ends up encrypting under a DEK that was never stored. Joining the
     * caller's transaction makes the key row commit atomically with the data it
     * protects — which is the correct semantics anyway: a key without data is harmless,
     * but data without a key is unreadable.
     */
    private int insertIsolated(UUID tenantId, byte[] wrapped) {
        return keyStore.insert(tenantId, wrapped, null);
    }

    private byte[] unwrapStoredDek(UUID tenantId) {
        byte[] wrapped = keyStore.wrappedDekOf(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "No stored key material for tenant " + tenantId));
        return unwrap(wrapped);
    }

    private byte[] wrap(byte[] dek) {
        return KeyWrapCipher.wrap(dek, kek, random);
    }

    private byte[] unwrap(byte[] wrapped) {
        return unwrapWith(wrapped, kek);
    }

    /** Unwrap with an explicit KEK, so a rotation can read with the old one. */
    private byte[] unwrapWith(byte[] wrapped, byte[] keyMaterial) {
        return KeyWrapCipher.unwrap(wrapped, keyMaterial);
    }

    /**
     * Re-wrap every tenant's DEK under the current KEK.
     *
     * <p>The DEK bytes do not change — only the wrapper around them — so no patient
     * data is rewritten and no ciphertext changes. This is what makes rotation an
     * online operation. The full implementation, with resumability and a dry run, is
     * #88; this exists so the property is testable now.
     */
    /**
     * Re-wrap every tenant's key material under this holder's KEK.
     *
     * <p><b>Requires the previous KEK to be present.</b> Reading the stored material
     * needs the KEK it was wrapped with, and writing needs the new one. A holder built
     * with only the new KEK cannot re-wrap by itself: {@link #unwrap} fails with "the
     * key-encryption key may have changed without a re-wrap", which is the correct
     * refusal and a common confusion. Build it with {@link #withPreviousKek}, or use
     * the rotation service in #88, which reads the old version before installing the
     * new one.
     *
     * <p>Only the wrapper changes. No DEK or index key is regenerated, so no patient
     * ciphertext and no stored digest needs rewriting — that is what makes rotation
     * an online operation.
     */
    public int rewrapAll() {
        int count = 0;
        for (UUID tenantId : keyStore.tenantIdsWithKeys()) {
            byte[] wrappedDek = keyStore.wrappedDekOf(tenantId).orElse(null);
            if (wrappedDek == null) {
                continue;
            }
            byte[] dek = requireDekLength(unwrapForRotation(wrappedDek));
            keyStore.replaceWrappedDek(tenantId, wrap(dek));
            cache.remove(tenantId);
            count++;
        }
        // The index key's wrapper must move too, or it becomes unreadable while the data key
        // survives. Re-wrapping leaves the index key bytes — and therefore every name_index
        // digest — unchanged, which is exactly what keeps search working across a rotation.
        blindIndexKeys().rewrapAll();
        // Everything that could be read with the previous KEK has now been re-wrapped, so
        // the rotation is over: drop it so subsequent reads use the current KEK. Keeping it
        // would leave the just-written material unreadable.
        previousKek = null;
        blindIndexKeys = null;
        blindIndexKeysUsesCurrentKek = false;
        return count;
    }

    /**
     * A holder that can re-wrap material written under {@code previousKekBase64} into
     * the current KEK.
     */
    public TenantKeyHolder withPreviousKek(String previousKekBase64) {
        this.previousKek = decodeKek(previousKekBase64);
        // Drop the index-key service so it is rebuilt holding the previous KEK: a rotation
        // that cannot read the material it is meant to re-wrap is the failure this guards.
        this.blindIndexKeys = null;
        this.blindIndexKeysUsesCurrentKek = false;
        return this;
    }

    /**
     * Unwrap using the KEK the material was written with.
     *
     * <p>When a previous KEK is supplied, that is the only one tried. Silently
     * falling back to the current KEK on failure hides the real problem behind a
     * generic "corrupt material" message, so the retry is the caller's choice.
     */
    private byte[] unwrapForRotation(byte[] wrapped) {
        return previousKek == null ? unwrap(wrapped) : unwrapWith(wrapped, previousKek);
    }

    public void clearCache() {
        cache.clear();
        blindIndexKeys = null;
        blindIndexKeysUsesCurrentKek = true;
    }

    // ---------------------------------------------------------------- test support

    /**
     * An in-memory holder for tests, so key resolution can be verified without a
     * database. Production always uses the Spring-managed instance.
     */
    public static TenantKeyHolder inMemoryForTesting(String base64Kek) {
        return new InMemoryTenantKeyHolder(base64Kek);
    }

    void corruptStoredDekForTesting(UUID tenantId) {
        throw new UnsupportedOperationException("only supported by the in-memory holder");
    }

    /** Holds generated DEKs in memory, keyed by tenant. */
    static final class InMemoryTenantKeyHolder extends TenantKeyHolder {
        private final Map<UUID, byte[]> stored = new ConcurrentHashMap<>();
        private final Map<UUID, Integer> generations = new ConcurrentHashMap<>();

        InMemoryTenantKeyHolder(String base64Kek) {
            super(base64Kek, null, 60_000L, null);
        }

        @Override
        protected int loadOrCreate(UUID tenantId) {
            return generations.computeIfAbsent(tenantId, t -> {
                byte[] dek = new byte[32];
                new SecureRandom().nextBytes(dek);
                stored.put(t, dek);
                return 1;
            });
        }

        @Override
        protected byte[] readStoredDek(UUID tenantId) {
            return requireDekLength(stored.get(tenantId));
        }

        @Override
        public int rewrapAll() {
            // Nothing is persisted, so a re-wrap is a no-op by construction: the point
            // is that no DEK bytes change.
            return stored.size();
        }

        @Override
        void corruptStoredDekForTesting(UUID tenantId) {
            stored.put(tenantId, new byte[4]);
        }

        Optional<byte[]> peek(UUID tenantId) {
            return Optional.ofNullable(stored.get(tenantId));
        }
    }
}
