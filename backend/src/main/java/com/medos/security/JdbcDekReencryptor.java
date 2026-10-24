package com.medos.security;

import com.medos.util.BlindIndexUtil;
import com.medos.util.PiiCiphertextFormat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC implementation of {@link DekReencryptor}.
 *
 * <p>Plain JDBC rather than JPA on purpose. A compromise rotation needs to write the
 * stored ciphertext string itself, not a converted entity attribute: going through an
 * entity would decrypt with the acting tenant's key and re-encrypt at whatever
 * generation the write supplier reports, which is exactly the implicit behaviour this
 * operation must not rely on. Reading and writing the column directly also lets the
 * batch update statement carry every encrypted column of a row at once, so ciphertext
 * and {@code name_index} cannot diverge if the process dies between them.
 *
 * <p>Every batch runs in its own transaction. A tenant with a million rows therefore
 * needs bounded undo/redo and lock hold time, and a failure leaves completed batches
 * committed and resumable rather than rolling the whole tenant back.
 */
@Component
public class JdbcDekReencryptor implements DekReencryptor {

    /** Rows read/written per table per transaction when the caller does not specify. */
    public static final int DEFAULT_BATCH_SIZE = 500;

    private static final int VERIFY_BATCH_SIZE = 1000;

    private final JdbcTemplate jdbc;
    private final Supplier<TenantKeyResolver> resolvers;
    private final TransactionTemplate transactions;

    @Autowired
    public JdbcDekReencryptor(JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this(jdbc, TenantKeyResolverFactory::get, new TransactionTemplate(txManager));
    }

    /** Test constructor: a resolver supplier and an optional transaction manager. */
    public JdbcDekReencryptor(JdbcTemplate jdbc, Supplier<TenantKeyResolver> resolvers,
                              TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.resolvers = resolvers;
        this.transactions = transactions;
    }

    @Override
    public Batch reencryptBatch(UUID tenantId, int toGeneration, boolean rebuildNameIndex,
                                Cursor cursor, int batchSize) {
        int size = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        return inTransaction(() -> doBatch(tenantId, toGeneration, rebuildNameIndex,
                cursor == null ? Cursor.start() : cursor, size));
    }

    private Batch doBatch(UUID tenantId, int toGeneration, boolean rebuildNameIndex,
                          Cursor cursor, int batchSize) {
        PiiCiphertextFormat format = formatFor(tenantId, toGeneration);
        int rewritten = 0;
        long remaining = 0;
        UUID afterPatients = cursor.afterPatientId();
        UUID afterEncounters = cursor.afterEncounterId();

        for (EncryptedPiiTables.Table table : EncryptedPiiTables.ALL) {
            UUID after = table.namesIndexed() ? afterPatients : afterEncounters;
            List<Row> rows = readRows(table, tenantId, after, batchSize);
            for (Row row : rows) {
                if (rewriteRow(table, row, tenantId, toGeneration, rebuildNameIndex, format)) {
                    rewritten++;
                }
            }
            UUID last = rows.isEmpty() ? after : rows.get(rows.size() - 1).id();
            if (table.namesIndexed()) {
                afterPatients = last;
            } else {
                afterEncounters = last;
            }
            if (rows.size() == batchSize) {
                remaining += countRowsAfter(table, tenantId, last);
            }
        }
        return new Batch(rewritten, remaining, new Cursor(afterPatients, afterEncounters));
    }

    /**
     * Rewrite one row. Returns true when any column changed, so the caller can report
     * how much work the batch did without counting rows it merely re-read.
     */
    private boolean rewriteRow(EncryptedPiiTables.Table table, Row row, UUID tenantId,
                               int toGeneration, boolean rebuildNameIndex,
                               PiiCiphertextFormat format) {
        Map<String, String> rewritten = new LinkedHashMap<>();
        boolean changed = false;
        for (String column : table.encryptedColumns()) {
            String stored = row.values().get(column);
            String next = rewriteValue(stored, toGeneration, format);
            rewritten.put(column, next);
            if (!Objects.equals(stored, next)) {
                changed = true;
            }
        }

        String nameIndex = row.nameIndex();
        if (rebuildNameIndex && table.namesIndexed()) {
            String plainName = format.decrypt(row.values().get("name"));
            if (plainName != null && !plainName.isBlank()) {
                byte[] key = resolvers.get().findBlindIndexKeyOrNull(tenantId);
                if (key == null) {
                    throw new IllegalStateException("Tenant " + tenantId
                            + " has no blind-index key, so name_index cannot be rebuilt");
                }
                String recomputed = BlindIndexUtil.indexName(plainName, key);
                if (!Objects.equals(recomputed, nameIndex)) {
                    nameIndex = recomputed;
                    changed = true;
                }
            }
        }

        if (changed) {
            updateRow(table, row.id(), tenantId, rewritten, nameIndex);
        }
        return changed;
    }

    /**
     * One value's move to the target generation.
     *
     * <p>Already-current ciphertext and plaintext are returned unchanged. A value that
     * names a generation newer than the target is refused: that means another rotation
     * overtook this one, and silently downgrading it would destroy data the newer key
     * protects.
     */
    private String rewriteValue(String stored, int toGeneration, PiiCiphertextFormat format) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        Integer generation = PiiCiphertextFormat.parseGeneration(stored);
        if (generation != null) {
            if (generation == toGeneration) {
                return stored;
            }
            if (generation > toGeneration) {
                throw new IllegalStateException("Ciphertext names DEK generation " + generation
                        + ", which is newer than the rotation target " + toGeneration
                        + ". Refusing to downgrade it; another rotation may have overtaken this run.");
            }
            return format.encrypt(format.decrypt(stored));
        }
        if (!PiiCiphertextFormat.isLegacyCiphertext(stored)) {
            // Pre-encryption plaintext, or a value that was never encrypted. Leave it.
            return stored;
        }
        return format.encrypt(format.decrypt(stored));
    }

    @Override
    public Verification verify(UUID tenantId, int targetGeneration, boolean nameIndexRebuilt) {
        PiiCiphertextFormat format = formatFor(tenantId, targetGeneration);
        long valuesChecked = 0;
        long notAtTarget = 0;
        long staleNameIndexes = 0;

        for (EncryptedPiiTables.Table table : EncryptedPiiTables.ALL) {
            UUID after = null;
            while (true) {
                List<Row> rows = readRows(table, tenantId, after, VERIFY_BATCH_SIZE);
                for (Row row : rows) {
                    String plainName = null;
                    for (String column : table.encryptedColumns()) {
                        String stored = row.values().get(column);
                        if (stored == null || stored.isBlank()) {
                            continue;
                        }
                        // Decrypting every value is the read-back: a value encrypted
                        // under a generation this process cannot resolve fails here,
                        // not at the first user request after the old key is gone.
                        String plain = format.decrypt(stored);
                        if ("name".equals(column)) {
                            plainName = plain;
                        }
                        valuesChecked++;
                        Integer generation = PiiCiphertextFormat.parseGeneration(stored);
                        if (generation != null) {
                            if (generation != targetGeneration) {
                                notAtTarget++;
                            }
                        } else if (PiiCiphertextFormat.isLegacyCiphertext(stored)) {
                            notAtTarget++;
                        }
                    }

                    if (nameIndexRebuilt && table.namesIndexed()
                            && plainName != null && !plainName.isBlank()) {
                        byte[] key = resolvers.get().findBlindIndexKeyOrNull(tenantId);
                        if (key == null) {
                            throw new IllegalStateException("Tenant " + tenantId
                                    + " has no blind-index key, so name_index cannot be verified");
                        }
                        String expected = BlindIndexUtil.indexName(plainName, key);
                        if (!Objects.equals(expected, row.nameIndex())) {
                            staleNameIndexes++;
                        }
                    }
                }
                if (rows.size() < VERIFY_BATCH_SIZE) {
                    break;
                }
                after = rows.get(rows.size() - 1).id();
            }
        }
        return new Verification(valuesChecked, notAtTarget, staleNameIndexes);
    }

    // ------------------------------------------------------------------ SQL helpers

    private PiiCiphertextFormat formatFor(UUID tenantId, int writeGeneration) {
        return new PiiCiphertextFormat(generation -> {
            byte[] dek = resolvers.get().dekForGenerationOrNull(tenantId, generation);
            return dek == null ? null : new SecretKeySpec(dek, "AES");
        }).withWriteGeneration(writeGeneration);
    }

    private List<Row> readRows(EncryptedPiiTables.Table table, UUID tenantId, UUID after,
                               int limit) {
        StringBuilder sql = new StringBuilder("SELECT id, ")
                .append(String.join(", ", table.encryptedColumns()));
        if (table.namesIndexed()) {
            sql.append(", name_index");
        }
        sql.append(" FROM ").append(table.name()).append(" WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (after != null) {
            sql.append(" AND id > CAST(? AS UUID)");
            args.add(after);
        }
        sql.append(" ORDER BY id LIMIT ?");
        args.add(limit);

        return jdbc.query(sql.toString(), (rs, n) -> {
            Map<String, String> values = new LinkedHashMap<>();
            for (String column : table.encryptedColumns()) {
                values.put(column, rs.getString(column));
            }
            String nameIndex = table.namesIndexed() ? rs.getString("name_index") : null;
            return new Row(rs.getObject("id", UUID.class), values, nameIndex);
        }, args.toArray());
    }

    private long countRowsAfter(EncryptedPiiTables.Table table, UUID tenantId, UUID after) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table.name()
                + " WHERE tenant_id = ? AND id > CAST(? AS UUID)", Long.class, tenantId, after);
        return count == null ? 0 : count;
    }

    private void updateRow(EncryptedPiiTables.Table table, UUID id, UUID tenantId,
                           Map<String, String> values, String nameIndex) {
        StringBuilder sql = new StringBuilder("UPDATE ").append(table.name()).append(" SET ");
        List<Object> args = new ArrayList<>();
        for (String column : table.encryptedColumns()) {
            sql.append(column).append(" = ?, ");
            args.add(values.get(column));
        }
        if (table.namesIndexed()) {
            sql.append("name_index = ?, ");
            args.add(nameIndex);
        }
        // Nothing else is intentionally touched. `version` is left for Hibernate's
        // optimistic locking on the entity path; this direct update must not reset it.
        sql.setLength(sql.length() - 2);
        sql.append(" WHERE id = ? AND tenant_id = ?");
        args.add(id);
        args.add(tenantId);
        jdbc.update(sql.toString(), args.toArray());
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactions == null ? work.get() : transactions.execute(status -> work.get());
    }

    private record Row(UUID id, Map<String, String> values, String nameIndex) {
    }
}
