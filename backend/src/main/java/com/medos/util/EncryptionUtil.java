package com.medos.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;

/**
 * AES-GCM encryption for PII fields at rest, via {@link PiiCiphertextFormat}.
 *
 * <p>Uses a 256-bit key from the system property or {@code PII_ENCRYPTION_KEY}.
 * There is deliberately no default: a missing or malformed key stops startup rather
 * than falling back to a published value, because a fallback here would mean every
 * deployment that forgot to configure a key shares one.
 *
 * <p>Stored form is versioned ({@code kv1.d<generation>:...}) so a key rotation can
 * identify which key each value needs. See {@link PiiCiphertextFormat} for why the
 * prefix names a DEK generation rather than a KEK version, and why an unknown
 * generation throws instead of degrading to a legacy read.
 */
@Converter(autoApply = false)
@Slf4j
public class EncryptionUtil implements AttributeConverter<String, String> {

    private static final int KEY_LENGTH = 32; // 256 bits

    private static PiiCiphertextFormat format;

    /**
     * Initialise the encryption key. Must be called during application startup.
     *
     * @param base64Key 32 bytes, Base64-encoded
     * @throws IllegalStateException if the key is missing, not Base64, or not 32 bytes
     */
    public static synchronized void init(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "PII encryption key is not configured. Set PII_ENCRYPTION_KEY "
                            + "(e.g. `openssl rand -base64 32`) and call EncryptionUtil.init() at startup.");
        }
        format = PiiCiphertextFormat.singleKeyResolver(1, base64Key)
                .withWriteGeneration(1);
    }

    private static synchronized PiiCiphertextFormat format() {
        if (format == null) {
            // Test hook: allows a test to set the key through a system property
            // rather than calling init() explicitly.
            String key = System.getProperty("medos.security.pii-encryption-key");
            if (key != null && !key.isBlank()) {
                init(key);
            } else {
                throw new IllegalStateException("PII encryption key not initialized. "
                        + "Call EncryptionUtil.init() at startup.");
            }
        }
        return format;
    }

    /** Test hook: forget the configured key, so the next use must re-initialise. */
    static synchronized void reset() {
        format = null;
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        return format().encrypt(plaintext);
    }

    @Override
    public String convertToEntityAttribute(String encrypted) {
        return format().decrypt(encrypted);
    }

    /**
     * Generate a new 32-byte encryption key and print it.
     *
     * <p>Run: {@code openssl rand -base64 32}
     */
    public static void main(String[] args) {
        byte[] key = new byte[KEY_LENGTH];
        new java.security.SecureRandom().nextBytes(key);
        System.out.println("PII_ENCRYPTION_KEY=" + Base64.getEncoder().encodeToString(key));
    }
}
