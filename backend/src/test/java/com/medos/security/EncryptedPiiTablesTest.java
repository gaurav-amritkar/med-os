package com.medos.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.medos.entity.DischargeSummary;
import com.medos.entity.DischargeSummaryAddendum;
import com.medos.entity.Encounter;
import com.medos.entity.LabOrder;
import com.medos.entity.Patient;
import com.medos.util.EncryptionUtil;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The re-encryption registry must name every encrypted column.
 *
 * <p>A missed column is the worst failure mode a compromise rotation has: verification
 * would still pass (it only checks the columns it knows about), the old key would be
 * destroyed, and the missed column would become permanently unreadable. So this test
 * compares the registry against the entity annotations in both directions rather than
 * trusting a hand-maintained list.
 */
class EncryptedPiiTablesTest {

    @Test
    @DisplayName("the registry covers exactly the encrypted columns on every encrypted entity")
    void registryMatchesTheEntityModel() {
        assertTable(Patient.class, EncryptedPiiTables.PATIENTS);
        assertTable(Encounter.class, EncryptedPiiTables.ENCOUNTERS);
        assertTable(DischargeSummary.class, EncryptedPiiTables.DISCHARGE_SUMMARIES);
        assertTable(DischargeSummaryAddendum.class, EncryptedPiiTables.DISCHARGE_SUMMARY_ADDENDA);
        assertTable(LabOrder.class, EncryptedPiiTables.LAB_ORDERS);

        assertThat(EncryptedPiiTables.ALL)
                .extracting(EncryptedPiiTables.Table::name)
                .containsExactly("patients", "encounters",
                        "discharge_summaries", "discharge_summary_addenda", "lab_orders");
    }

    @Test
    @DisplayName("a new encrypted entity column fails this suite until it is registered")
    void everyEncryptedEntityIsRegistered() {
        // Discharge records and lab results were the omissions this guard now
        // closes (#225, #237): the registry only knew patients and encounters,
        // so a rotation reported success while signed discharge content and lab
        // results stayed under the old key.
        assertThat(EncryptedPiiTables.ALL)
                .extracting(EncryptedPiiTables.Table::name)
                .contains("discharge_summaries", "discharge_summary_addenda", "lab_orders");
    }

    @Test
    @DisplayName("patients are the only table with a derived name index")
    void onlyPatientsAreNamesIndexed() {
        assertThat(EncryptedPiiTables.PATIENTS.namesIndexed()).isTrue();
        assertThat(EncryptedPiiTables.ENCOUNTERS.namesIndexed()).isFalse();
    }

    private void assertTable(Class<?> entity, EncryptedPiiTables.Table table) {
        jakarta.persistence.Table annotation = entity.getAnnotation(jakarta.persistence.Table.class);
        assertThat(annotation).as("%s must declare @Table", entity.getSimpleName()).isNotNull();
        assertThat(annotation.name()).isEqualTo(table.name());

        Set<String> fromEntity = Arrays.stream(entity.getDeclaredFields())
                .filter(EncryptedPiiTablesTest::isEncrypted)
                .map(EncryptedPiiTablesTest::columnName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(fromEntity)
                .as("every @Convert(EncryptionUtil) column on %s must be re-encrypted",
                        entity.getSimpleName())
                .containsExactlyInAnyOrderElementsOf(table.encryptedColumns());
    }

    private static boolean isEncrypted(Field field) {
        Convert convert = field.getAnnotation(Convert.class);
        return convert != null && convert.converter() == EncryptionUtil.class;
    }

    private static String columnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.name().isBlank()) {
            return column.name();
        }
        return field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
