package com.medos.security;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM wrapping of key material under a key-encryption key.
 *
 * <p>Extracted so that both the data key ({@link TenantKeyHolder}) and the blind-index
 * key ({@link BlindIndexKeyService}) wrap and unwrap through one implementation.
 * Duplicating the routine would put two subtly different wrapping schemes on the same
 * {@code tenant_keys} table, where a mismatch is undetectable until data is unreadable.
 *
 * <p>Only the wrapped form is ever persisted. A wrapped value is the IV followed by
 * ciphertext-with-tag, so a 32-byte key occupies 60 bytes.
 */
final class KeyWrapCipher {

    static final int KEY_LENGTH = 32;
    static final int IV_LENGTH = 12;
    static final int TAG_BITS = 128;

    private KeyWrapCipher() {
    }

    /**
     * AES-GCM(kek, plaintext). The IV is random per call, so wrapping the same key twice
     * produces different bytes and the wrapped value leaks nothing about the key.
     */
    static byte[] wrap(byte[] plaintext, byte[] kek, SecureRandom random) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kek, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext);
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
            buf.put(iv);
            buf.put(ct);
            return buf.array();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to wrap the tenant data key", e);
        }
    }

    /**
     * Unwrap with an explicit KEK rather than an implicit current one, so a rotation can
     * read material written under the previous KEK before re-wrapping it.
     */
    static byte[] unwrap(byte[] wrapped, byte[] kek) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(wrapped, 0, iv, 0, IV_LENGTH);
            byte[] ct = new byte[wrapped.length - IV_LENGTH];
            System.arraycopy(wrapped, IV_LENGTH, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kek, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            // Deliberately does not include the tenant or the material: this message
            // reaches logs, and a wrong KEK must not become a disclosure vector.
            throw new IllegalStateException(
                    "Failed to unwrap a tenant data key. The key-encryption key may have "
                            + "changed without a re-wrap, or the stored material is corrupt.", e);
        }
    }
}
