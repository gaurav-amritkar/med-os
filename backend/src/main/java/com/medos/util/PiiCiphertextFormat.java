package com.medos.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The stored form of an encrypted PII value, versioned so key rotation can be
 * resumable and verifiable.
 *
 * <p>Format:
 * <pre>kv1.d&lt;dekGeneration&gt;:Base64(IV ‖ ciphertext ‖ authTag)</pre>
 *
 * <p><b>The generation is a DEK generation, not a KEK version.</b> Ciphertext is
 * encrypted by a tenant's DEK and is entirely independent of which KEK version
 * wrapped that DEK, so a KEK re-wrap changes no ciphertext at all. Referencing the
 * KEK here would be meaningless: after a re-wrap every row would still claim the
 * old KEK version, while its actual protection came from a newer one.
 *
 * <p>Values with no prefix predate this format and are read through the legacy
 * path, so migration is incremental and a partially-migrated database still serves
 * reads. A row that is not Base64, or is too short to hold an IV and a tag, is
 * treated as pre-encryption plaintext and returned as-is.
 *
 * <p>An <b>unknown generation throws</b> rather than falling back. Silently
 * returning the raw value would hand the caller a string it cannot interpret, and
 * treating it as plaintext would be worse still: a key mismatch would surface as a
 * plausible-looking name or diagnosis.
 */
public class PiiCiphertextFormat {

    /** Version tag for the current format. Bump only for a structural change. */
    public static final String VERSION = "kv1";

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 16;
    private static final int KEY_LENGTH = 32;

    /** {@code kv1.d<generation>:} — anything after the colon is the payload. */
    private static final Pattern VERSIONED = Pattern.compile("^kv(\\d+)\\.d(\\d+):(.+)$", Pattern.DOTALL);

    private final DekResolver dekResolver;
    private final SecureRandom random = new SecureRandom();

    /** Supplies the DEK for a generation, or null when that generation is unknown. */
    public interface DekResolver {
        SecretKeySpec resolveDek(int generation);
    }

    /**
     * Thrown when ciphertext names a DEK generation this process cannot decrypt.
     *
     * <p>Distinct from a generic failure so callers can tell "this database was
     * written by a newer version" or "this key has been retired" from "this value
     * is corrupt" — the first two are operational, the third is a data problem.
     */
    public static class UnknownGenerationException extends IllegalStateException {
        public UnknownGenerationException(int generation) {
            super("Cannot decrypt PII: no key is available for DEK generation " + generation
                    + ". A DEK rotation may be incomplete, or this key has been retired.");
        }
    }

    public PiiCiphertextFormat(DekResolver dekResolver) {
        this.dekResolver = dekResolver;
    }

    /** The resolver in use. Exposed so a caller can build a sibling format on the same keys. */
    public DekResolver dekResolver() {
        return dekResolver;
    }

    /**
     * A resolver that delegates to the tenant-aware {@code TenantKeyHolder}.
     *
     * <p>The generation argument is honoured, not ignored: the tenant's current
     * generation resolves through the normal path (the same key new writes use),
     * and a superseded generation resolves through the rotation archive, so
     * ciphertext labelled {@code kv1.d<N>:} keeps decrypting after the tenant moved
     * to N+1. A generation that was never issued resolves to null, which decrypt
     * turns into a loud {@code UnknownGenerationException} — never into a
     * plausible-but-wrong read.
     */
    public static DekResolver tenantResolver() {
        return generation -> {
            var holder = com.medos.security.TenantKeyResolverFactory.get();
            Integer current = null;
            try {
                current = holder.currentGeneration();
            } catch (RuntimeException ignored) {
                // A yet-keyless tenant has no generation; fall through to the null
                // resolution, which encrypt refuses loudly below.
            }
            byte[] dek = (current != null && current == generation)
                    ? holder.resolveDek(null)
                    : holder.dekForGenerationOrNull(null, generation);
            return dek == null ? null : new javax.crypto.spec.SecretKeySpec(dek, "AES");
        };
    }

    /** Convenience for the single-key case, used by tests. */
    public static PiiCiphertextFormat singleKeyResolver(int generation, String base64Key) {
        byte[] keyBytes = decodeKey(base64Key);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        return new PiiCiphertextFormat(gen -> generation == gen ? key : null);
    }

    private static byte[] decodeKey(String base64Key) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PII encryption key is not valid Base64", e);
        }
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalStateException("PII encryption key must be 32 bytes (256 bits) after Base64 decode");
        }
        return keyBytes;
    }

    /** True when the value starts like {@code kv<version>.d<generation>:} but does not parse. */
    private static boolean looksLikePrefix(String stored) {
        return stored != null && stored.length() > 3
                && stored.startsWith("kv")
                && stored.indexOf(':') > 0
                && !VERSIONED.matcher(stored).matches();
    }

    public static boolean isVersioned(String stored) {
        return stored != null && VERSIONED.matcher(stored).matches();
    }

    /**
     * True when the value is ciphertext written before the generation prefix existed.
     *
     * <p>Used by a compromise rotation to decide whether a prefix-less value must be
     * decrypted and rewritten, or is simply plaintext and must be left alone. The test
     * is deliberately the same one {@link #decrypt} uses: Base64 that decodes to at
     * least an IV and a tag. Anything else is treated as plaintext, exactly as the read
     * path treats it.
     */
    public static boolean isLegacyCiphertext(String stored) {
        if (stored == null || stored.isBlank() || isVersioned(stored) || looksLikePrefix(stored)) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(stored).length >= IV_LENGTH + TAG_LENGTH;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * The DEK generation named by the prefix, or null for a legacy value.
     */
    public static Integer parseGeneration(String stored) {
        if (stored == null) {
            return null;
        }
        Matcher m = VERSIONED.matcher(stored);
        return m.matches() ? Integer.parseInt(m.group(2)) : null;
    }

    /** Encrypt with the current generation and the versioned prefix. */
    public String encrypt(String plaintext) {
        // A null or blank value is not ciphertext, so it must not acquire a prefix.
        // Prefixing it would turn an absent value into a versioned blob that then
        // has to be recognised as "no value" on the way back out.
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        int generation = currentGenerationForWrite();
        return VERSION + ".d" + generation + ":" + encryptUnversioned(plaintext);
    }

    /**
     * Encrypt without a version prefix, producing the legacy format. Only used by
     * tests and by a deliberate downgrade; normal writes must be versioned so
     * rotation can identify them.
     */
    String encryptUnversioned(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            int generation = currentGenerationForWrite();
            SecretKeySpec key = dekResolver.resolveDek(generation);
            if (key == null) {
                throw new UnknownGenerationException(generation);
            }
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH * 8, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(IV_LENGTH + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt PII field", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }

        Matcher m = VERSIONED.matcher(stored);
        if (m.matches()) {
            String version = m.group(1);
            if (!VERSION.equals("kv" + version)) {
                throw new IllegalStateException("Cannot read PII written in ciphertext format 'kv" + version
                        + "'; this build understands " + VERSION + ". Rolling back below the version that wrote "
                        + "a value makes that value unreadable.");
            }
            int generation;
            try {
                generation = Integer.parseInt(m.group(2));
            } catch (NumberFormatException e) {
                throw new IllegalStateException("PII ciphertext has an unreadable DEK generation: " + m.group(2), e);
            }
            return decryptPayload(m.group(3), generation);
        }

        // Looks like it was meant to be versioned but is not parseable. Treating it as
        // legacy plaintext would return a mangled value to the caller as if it were
        // a name or a diagnosis, so it is rejected as corruption instead.
        if (looksLikePrefix(stored)) {
            throw new IllegalStateException(
                    "PII ciphertext is corrupt: it carries a version prefix but does not parse");
        }

        // No prefix. Either legacy ciphertext or pre-encryption plaintext.
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(stored);
        } catch (IllegalArgumentException e) {
            return stored;
        }
        if (decoded.length < IV_LENGTH + TAG_LENGTH) {
            return stored;
        }
        return decryptPayload(stored, LEGACY_GENERATION);
    }

    private String decryptPayload(String base64Payload, int generation) {
        SecretKeySpec key = dekResolver.resolveDek(generation);
        if (key == null) {
            throw new UnknownGenerationException(generation);
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Payload);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PII ciphertext is not valid Base64", e);
        }
        if (decoded.length < IV_LENGTH + TAG_LENGTH) {
            throw new IllegalStateException("PII ciphertext is truncated: expected at least "
                    + (IV_LENGTH + TAG_LENGTH) + " bytes, got " + decoded.length);
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);
            byte[] ciphertext = new byte[decoded.length - IV_LENGTH];
            System.arraycopy(decoded, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH * 8, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (UnknownGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt PII field (wrong key for DEK generation "
                    + generation + ", or corrupted data)", e);
        }
    }

    /**
     * The generation new writes should use.
     *
     * <p>An explicit {@code writeGenerationSupplier} wins — that is the tenant-aware
     * path, because the current generation is per tenant and must answer for the
     * acting tenant at write time, not once at startup. Without a supplier the
     * explicit {@code writeGeneration} applies, then this conservative probe: until
     * rotation-aware resolution is wired there is one generation, and guessing a
     * higher one would write data no key can read.
     */
    private int currentGenerationForWrite() {
        if (writeGenerationSupplier != null) {
            return writeGenerationSupplier.getAsInt();
        }
        Integer parsed = currentGeneration();
        if (parsed != null) {
            return parsed;
        }
        for (int g = MAX_PROBE_GENERATION; g >= 1; g--) {
            if (dekResolver.resolveDek(g) != null) {
                return g;
            }
        }
        throw new IllegalStateException("No PII decryption key is available");
    }

    private Integer currentGeneration() {
        return writeGeneration;
    }

    private static final int MAX_PROBE_GENERATION = 64;

    /**
     * The generation unversioned (pre-versioning) ciphertext was written under.
     *
     * <p>Values without a {@code kv1.d<N>:} prefix predate generation tracking, which
     * began at generation 1. Decrypting them with the <em>current</em> generation
     * instead made an old value unreadable the moment its tenant rotated: the
     * prefix-less path tried the new key and failed even though the old generation
     * was still archived. A compromise rotation must not turn "old but readable"
     * into "old and lost", so this path is pinned to the only generation such a
     * value can have used.
     */
    private static final int LEGACY_GENERATION = 1;

    private Integer writeGeneration;
    private java.util.function.IntSupplier writeGenerationSupplier;

    /** Set the generation used for new writes. Default 1. */
    public PiiCiphertextFormat withWriteGeneration(int generation) {
        this.writeGeneration = generation;
        return this;
    }

    /**
     * Supply the write generation per call. For the tenant-aware path this is the
     * acting tenant's current DEK generation, so a write is always labelled with the
     * generation whose key encrypts it — including across a concurrent rotation,
     * where a label and a key are then read as one pair from the store, never as
     * two independent facts read at different moments.
     */
    public PiiCiphertextFormat withWriteGenerationSupplier(java.util.function.IntSupplier supplier) {
        this.writeGenerationSupplier = supplier;
        return this;
    }
}
