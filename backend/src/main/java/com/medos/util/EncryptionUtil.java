package com.medos.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption for PII fields at rest.
 * Uses 256-bit key from system property/environment variable (PII_ENCRYPTION_KEY).
 * Each encrypted value includes: IV (12 bytes) + ciphertext + auth tag (16 bytes) encoded as Base64.
 */
@Converter(autoApply = false)
@Slf4j
public class EncryptionUtil implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12; // GCM recommended IV length
    private static final int TAG_LENGTH = 16; // GCM auth tag length
    private static final int KEY_LENGTH = 32; // 256 bits

    private static SecretKeySpec secretKey;

    /**
     * Initialize the encryption key from environment variable.
     * Must be called during application startup.
     */
    public static void init(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalStateException("PII encryption key must be 32 bytes (256 bits) after Base64 decode");
        }
        secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    private static SecretKeySpec getSecretKey() {
        if (secretKey == null) {
            // Try to get from system property (for tests)
            String key = System.getProperty("medos.security.pii-encryption-key");
            if (key != null && !key.isBlank()) {
                init(key);
            } else {
                throw new IllegalStateException("PII encryption key not initialized. Call EncryptionUtil.init() at startup.");
            }
        }
        return secretKey;
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        try {
            // Generate random IV for each encryption
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext (which includes auth tag at the end)
            ByteBuffer buffer = ByteBuffer.allocate(IV_LENGTH + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            log.error("PII encryption failed", e);
            throw new IllegalStateException("Failed to encrypt PII field", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return encrypted;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            if (decoded.length < IV_LENGTH + TAG_LENGTH) {
                throw new IllegalStateException("Invalid encrypted data format");
            }

            // Extract IV (first 12 bytes)
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);

            // Extract ciphertext + auth tag (remaining bytes)
            byte[] ciphertext = new byte[decoded.length - IV_LENGTH];
            System.arraycopy(decoded, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("PII decryption failed", e);
            throw new IllegalStateException("Failed to decrypt PII field", e);
        }
    }

    /**
     * Utility method to generate a new encryption key.
     * Run: openssl rand -base64 32
     */
    public static void main(String[] args) {
        byte[] key = new byte[KEY_LENGTH];
        new SecureRandom().nextBytes(key);
        System.out.println("PII_ENCRYPTION_KEY=" + Base64.getEncoder().encodeToString(key));
    }
}