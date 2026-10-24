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

    public static final List<Table> ALL = List.of(PATIENTS, ENCOUNTERS);
}
