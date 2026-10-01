package com.medos.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the versioned ciphertext format introduced for the key lifecycle (#85).
 *
 * <p>The prefix exists so rotation can be resumable and verifiable. Two
 * distinctions matter and are easy to get wrong:
 *
 * <ul>
 *   <li>The prefix names a <b>DEK generation</b>, not a KEK version. Ciphertext is
 *       encrypted by a tenant's DEK and is independent of which KEK version
 *       wrapped that DEK, so a KEK re-wrap changes no ciphertext at all.
 *   <li>An <b>unknown generation must throw</b>. Treating it as "legacy" would
 *       hand back a value the caller cannot interpret, and treating it as
 *       plaintext would be worse: a decryption failure would silently surface as
 *       a name or diagnosis.
 * </ul>
 */
class PiiCiphertextFormatTest {

    private static final String KEY_A = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String KEY_B = Base64.getEncoder()
            .encodeToString("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));

    private PiiCiphertextFormat formatWithGeneration(int generation, String key) {
        return PiiCiphertextFormat.singleKeyResolver(generation, key).withWriteGeneration(generation);
    }

    @Test
    @DisplayName("round-trips a value and produces the kv1.d<generation> prefix")
    void roundTripsVersionedValue() {
        PiiCiphertextFormat fmt = formatWithGeneration(1, KEY_A);

        String stored = fmt.encrypt("Anita Sharma");

        assertThat(stored).startsWith("kv1.d1:");
        assertThat(fmt.decrypt(stored)).isEqualTo("Anita Sharma");
    }

    @Test
    @DisplayName("the prefix records the DEK generation that encrypted the value")
    void prefixCarriesDekGeneration() {
        PiiCiphertextFormat second = formatWithGeneration(2, KEY_B);

        String stored = second.encrypt("Anita Sharma");

        assertThat(stored).startsWith("kv1.d2:");
    }

    @Test
    @DisplayName("a value written under generation 1 is unreadable under generation 2")
    void generationIsMeaningful() {
        String underOne = formatWithGeneration(1, KEY_A).encrypt("Anita Sharma");

        assertThatThrownBy(() -> formatWithGeneration(2, KEY_B).decrypt(underOne))
                .as("decrypting with the wrong generation must fail, not return garbage")
                .isInstanceOf(PiiCiphertextFormat.UnknownGenerationException.class);
    }

    @Test
    @DisplayName("a legacy unversioned ciphertext still decrypts, so migration is incremental")
    void readsLegacyCiphertext() {
        // Written by the pre-#85 code path: no prefix, bare Base64(IV||ct||tag).
        PiiCiphertextFormat legacyWriter =
                new PiiCiphertextFormat(PiiCiphertextFormat.singleKeyResolver(1, KEY_A).dekResolver())
                        .withWriteGeneration(1);
        String legacy = legacyWriter.encryptUnversioned("Anita Sharma");
        assertThat(legacy).doesNotStartWith("kv1.");

        assertThat(formatWithGeneration(1, KEY_A).decrypt(legacy))
                .as("a partially-migrated database must still serve reads")
                .isEqualTo("Anita Sharma");
    }

    @Test
    @DisplayName("legacy plaintext is returned unchanged, preserving the pre-encryption tolerance")
    void readsLegacyPlaintext() {
        // Not Base64, so it cannot be our ciphertext. The old code returned such a
        // value as-is so rows written before encryption was enabled stayed readable.
        assertThat(formatWithGeneration(1, KEY_A).decrypt("Anita"))
                .isEqualTo("Anita");
        // Base64 but too short to contain IV+tag, also treated as legacy plaintext.
        assertThat(formatWithGeneration(1, KEY_A).decrypt("aGVsbG8="))
                .isEqualTo("aGVsbG8=");
    }

    @Test
    @DisplayName("an unknown DEK generation throws rather than returning the value")
    void unknownGenerationFailsLoudly() {
        String stored = formatWithGeneration(7, KEY_A).encrypt("Anita Sharma");

        // A resolver that only knows generation 1.
        PiiCiphertextFormat resolver = formatWithGeneration(1, KEY_A);

        assertThatThrownBy(() -> resolver.decrypt(stored))
                .isInstanceOf(PiiCiphertextFormat.UnknownGenerationException.class)
                .hasMessageContaining("7");
    }

    @Test
    @DisplayName("an unknown generation never yields the plaintext")
    void unknownGenerationNeverLeaksPlaintext() {
        String stored = formatWithGeneration(7, KEY_A).encrypt("Anita Sharma");

        // Even the assertion of "no leak": catching the throw and checking the
        // message does not carry the value.
        try {
            formatWithGeneration(1, KEY_A).decrypt(stored);
            org.junit.jupiter.api.Assertions.fail("expected a throw");
        } catch (PiiCiphertextFormat.UnknownGenerationException e) {
            assertThat(e.getMessage()).doesNotContain("Anita");
        }
    }

    @Test
    @DisplayName("a malformed prefix is rejected as corruption, not treated as legacy")
    void malformedPrefixIsRejected() {
        // "kv1.d1" without the colon, and a non-numeric generation.
        assertThatThrownBy(() -> formatWithGeneration(1, KEY_A).decrypt("kv1.d1:abc"))
                .isInstanceOfAny(PiiCiphertextFormat.UnknownGenerationException.class,
                        IllegalStateException.class);
        assertThatThrownBy(() -> formatWithGeneration(1, KEY_A).decrypt("kv1.dX:abc"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("null and blank values pass through untouched")
    void nullAndBlankPassThrough() {
        PiiCiphertextFormat fmt = formatWithGeneration(1, KEY_A);

        assertThat(fmt.encrypt(null)).isNull();
        assertThat(fmt.encrypt("")).isEmpty();
        assertThat(fmt.decrypt(null)).isNull();
        assertThat(fmt.decrypt("")).isEmpty();
    }

    @Test
    @DisplayName("encrypting the same value twice yields different ciphertext (fresh IV each time)")
    void freshIvPerWrite() {
        PiiCiphertextFormat fmt = formatWithGeneration(1, KEY_A);

        String a = fmt.encrypt("Anita Sharma");
        String b = fmt.encrypt("Anita Sharma");

        assertThat(a).isNotEqualTo(b);
        assertThat(fmt.decrypt(a)).isEqualTo(fmt.decrypt(b));
    }

    @Test
    @DisplayName("unicode survives a round trip")
    void unicodeRoundTrips() {
        PiiCiphertextFormat fmt = formatWithGeneration(1, KEY_A);

        String value = "अनिता शर्मा — Diabetes Type 2";

        assertThat(fmt.decrypt(fmt.encrypt(value))).isEqualTo(value);
    }

    @Test
    @DisplayName("the generation is parsed out of the prefix, not inferred from the key")
    void generationIsParsed() {
        PiiCiphertextFormat fmt = formatWithGeneration(3, KEY_A);

        String stored = fmt.encrypt("value");

        assertThat(PiiCiphertextFormat.parseGeneration(stored)).isEqualTo(3);
        assertThat(PiiCiphertextFormat.isVersioned(stored)).isTrue();
        assertThat(PiiCiphertextFormat.isVersioned("aGVsbG8=")).isFalse();
    }

    @Test
    @DisplayName("a long value needs the TEXT column V1 already allocates")
    void longValueRoundTrips() {
        PiiCiphertextFormat fmt = formatWithGeneration(1, KEY_A);
        String value = "x".repeat(4000);

        assertThat(fmt.decrypt(fmt.encrypt(value))).isEqualTo(value);
    }
}
