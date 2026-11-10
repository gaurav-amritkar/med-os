package com.medos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.medos.entity.LabOrder;
import com.medos.util.EncryptionUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A lab result is clinical content, so it must not be stored in the clear.
 *
 * <p>{@code lab_orders.result} was plain TEXT from V1 until V17 (#237). The
 * converter was added without a rewrite because {@link EncryptionUtil}'s read
 * path tolerates a prefix-less value, which means a plaintext row is served
 * back as though it were fine — no exception and no warning. The failure mode
 * is silence, so this is a test and not a convention.
 *
 * <p>The companion assertions check the rotation registry names the column: a
 * column that is encrypted but absent from
 * {@link EncryptedPiiTables} is a column a compromise rotation will skip.
 */
class LabResultEncryptionTest {

    @Test
    @DisplayName("lab_orders.result carries the envelope-encryption converter")
    void resultColumnIsEncrypted() {
        Field result = Arrays.stream(LabOrder.class.getDeclaredFields())
                .filter(f -> f.getName().equals("result"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("LabOrder.result is missing"));

        Convert convert = result.getAnnotation(Convert.class);
        assertThat(convert)
                .as("a lab result is clinical content and must not be stored in the clear")
                .isNotNull();
        assertThat(convert.converter())
                .isEqualTo(EncryptionUtil.class);
    }

    @Test
    @DisplayName("the rotation registry includes lab_orders.result")
    void rotationCoversLabResults() {
        List<String> columns = EncryptedPiiTables.LAB_ORDERS.encryptedColumns();

        assertThat(columns)
                .as("an encrypted column absent from the registry is skipped by rotation")
                .contains("result");
    }

    @Test
    @DisplayName("test_name stays plaintext: it names a catalogue test, not a patient result")
    void testNameIsNotEncrypted() {
        // test_name and test_code are the columns the lab list filters on, and
        // they identify a catalogue test rather than a patient's result.
        List<String> encrypted = EncryptedPiiTables.LAB_ORDERS.encryptedColumns();

        assertThat(encrypted).doesNotContain("test_name", "test_code");
    }

    @Test
    @DisplayName("no other LabOrder column is encrypted without being registered")
    void registryMatchesTheEntity() {
        List<String> fromEntity = Arrays.stream(LabOrder.class.getDeclaredFields())
                .filter(f -> {
                    Convert c = f.getAnnotation(Convert.class);
                    return c != null && c.converter() == EncryptionUtil.class;
                })
                .map(LabResultEncryptionTest::columnName)
                .collect(Collectors.toList());

        assertThat(fromEntity)
                .containsExactlyInAnyOrderElementsOf(EncryptedPiiTables.LAB_ORDERS.encryptedColumns());
    }

    private static String columnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.name().isBlank()) {
            return column.name();
        }
        return field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
