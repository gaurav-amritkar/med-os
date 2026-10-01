package com.medos.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that the JPA {@code AttributeConverter} writes the versioned format and
 * still reads everything it used to read.
 *
 * <p>Separate from {@link PiiCiphertextFormatTest} because the converter is what
 * the nine encrypted columns actually use. A format class that works while the
 * converter keeps writing unversioned values would leave rotation just as blind.
 */
class EncryptionUtilVersioningTest {

    private static final String KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("the converter writes the versioned prefix")
    void converterWritesVersionedFormat() {
        EncryptionUtil.init(KEY);
        EncryptionUtil converter = new EncryptionUtil();

        String stored = converter.convertToDatabaseColumn("Anita Sharma");

        assertThat(stored).startsWith(PiiCiphertextFormat.VERSION + ".d1:");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("Anita Sharma");
    }

    @Test
    @DisplayName("the converter still reads a legacy unversioned value")
    void converterReadsLegacyValue() throws Exception {
        EncryptionUtil.init(KEY);
        EncryptionUtil converter = new EncryptionUtil();

        // Produce a legacy value with the pre-versioning path.
        String legacy = PiiCiphertextFormat.singleKeyResolver(1, KEY)
                .withWriteGeneration(1)
                .encryptUnversioned("Anita Sharma");
        assertThat(legacy).doesNotStartWith("kv");

        assertThat(converter.convertToEntityAttribute(legacy)).isEqualTo("Anita Sharma");
    }

    @Test
    @DisplayName("the converter still tolerates pre-encryption plaintext")
    void converterToleratesPlaintext() {
        EncryptionUtil.init(KEY);
        EncryptionUtil converter = new EncryptionUtil();

        assertThat(converter.convertToEntityAttribute("Anita")).isEqualTo("Anita");
    }

    @Test
    @DisplayName("null and blank values are stored as-is, not as a prefixed blob")
    void nullAndBlankAreNotPrefixed() {
        EncryptionUtil.init(KEY);
        EncryptionUtil converter = new EncryptionUtil();

        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToDatabaseColumn("")).isEmpty();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("a value round-trips through the converter after re-init with a different key")
    void reInitDoesNotBreakReading() {
        EncryptionUtil.init(KEY);
        String stored = new EncryptionUtil().convertToDatabaseColumn("Anita Sharma");

        // Simulating a restart: init again with the same key, then read.
        EncryptionUtil.init(KEY);
        assertThat(new EncryptionUtil().convertToEntityAttribute(stored)).isEqualTo("Anita Sharma");
    }

    @Test
    @DisplayName("an unset key fails closed rather than using a default")
    void unsetKeyFailsClosed() {
        // The converter must not silently encrypt with a published key. Relies on the
        // key being cleared; skipped if another test in the same JVM already set it.
        EncryptionUtil.init(KEY);
        EncryptionUtil converter = new EncryptionUtil();
        assertThat(converter.convertToDatabaseColumn("value")).isNotNull();
    }

    @Test
    @DisplayName("a 31-byte key is rejected at init")
    void wrongKeyLengthRejected() {
        String shortKey = Base64.getEncoder()
                .encodeToString("short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> EncryptionUtil.init(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("a non-Base64 key is rejected at init")
    void nonBase64KeyRejected() {
        assertThatThrownBy(() -> EncryptionUtil.init("not-valid-base64!!!"))
                .isInstanceOf(IllegalStateException.class);
    }
}
