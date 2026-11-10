package com.medos.security;

import java.util.List;

/**
 * The columns a compromise rotation must rewrite.
 *
 * <p>Re-encryption has to reach every column that holds {@code kv1.d<N>:} data, and
 * missing one is the worst possible outcome: verification would still pass, the old
 * DEK would be destroyed, and the missed column would become unreadable. So the list
 * is small, explicit, and checked against the entity model by
 * {@code EncryptedPiiTablesTest}; it is not discovered at runtime by guessing table
 * names.
 *
 * <p>Adding an encrypted field to an entity without adding it here fails that test
 * rather than silently leaving the column behind.
 */
public final class EncryptedPiiTables {

    private EncryptedPiiTables() {
    }

    /**
     * One table's encrypted columns.
     *
     * @param name            physical table name
     * @param encryptedColumns columns holding {@code EncryptionUtil} ciphertext
     * @param namesIndexed    whether the table also carries a derived name index that
     *                        must be rebuilt when the blind-index key rotates
     */
    public record Table(String name, List<String> encryptedColumns, boolean namesIndexed) {
        public Table {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("table name is required");
            }
            encryptedColumns = List.copyOf(encryptedColumns);
            if (encryptedColumns.isEmpty()) {
                throw new IllegalArgumentException("an encrypted table needs at least one column");
            }
        }
    }

    /** Patient PII. {@code name_index} is derived from {@code name}. */
    public static final Table PATIENTS = new Table(
            "patients",
            List.of("name", "phone", "email", "address", "blood_group"),
            true);

    /** Clinical free text. No blind index: these fields are not searched by equality. */
    public static final Table ENCOUNTERS = new Table(
            "encounters",
            List.of("chief_complaint", "diagnosis", "clinical_notes", "ai_note"),
            false);

    /**
     * The signed discharge summary. Every clinical field is encrypted; the
     * clinician name and the identifiers are not.
     *
     * <p>This table was missing from the registry, so a compromise rotation
     * reported success while discharge content stayed under the superseded key
     * (#225).
     */
    public static final Table DISCHARGE_SUMMARIES = new Table(
            "discharge_summaries",
            List.of("admission_diagnosis", "discharge_diagnosis", "clinical_course",
                    "procedures_performed", "investigations_summary", "vitals_summary",
                    "medications_at_discharge", "condition_at_discharge", "follow_up_advice"),
            false);

    /** A correction appended to a signed discharge summary. */
    public static final Table DISCHARGE_SUMMARY_ADDENDA = new Table(
            "discharge_summary_addenda",
            List.of("text"),
            false);

    /**
     * Lab results. The result text is clinical content and was stored in the
     * clear until V17 (#237).
     *
     * <p>{@code test_name} and {@code test_code} are deliberately NOT listed:
     * they name a catalogue test, not a patient's result, and they are the
     * columns the lab list screen filters on.
     */
    public static final Table LAB_ORDERS = new Table(
            "lab_orders",
            List.of("result"),
            false);

    public static final List<Table> ALL =
            List.of(PATIENTS, ENCOUNTERS, DISCHARGE_SUMMARIES, DISCHARGE_SUMMARY_ADDENDA, LAB_ORDERS);
}
