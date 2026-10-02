package com.medos.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * {@link TenantKeyStore} backed by {@link JdbcTemplate}.
 *
 * <p>Used for the key INSERT specifically. A {@code @Modifying} native query issued
 * while a Hibernate session is open is added to that session's action queue rather than
 * executed against the database immediately; if the session closes or rolls back
 * afterwards, the write is discarded. The symptom is a key that "inserted" with
 * {@code rows=1}, a readable row in the same transaction, and no row on disk — so a
 * tenant encrypts under a DEK nothing can ever unwrap. Plain JDBC on the transaction's
 * own connection has no such queue.
 */
@Repository
public class TenantKeyStoreJdbc implements TenantKeyStore {

    // No ON CONFLICT: it is PostgreSQL syntax and the H2 test profile does not accept
    // it. The existence check below makes this idempotent on both.
    private static final String INSERT = """
            INSERT INTO tenant_keys (tenant_id, wrapped_dek, wrapped_bi_key,
                                    dek_generation, bi_key_generation, wrapped_kek_version,
                                    created_at, updated_at)
            VALUES (?, ?, ?, 1, 1, 1, now(), now())
            """;

    private final JdbcTemplate jdbc;

    public TenantKeyStoreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public java.util.Optional<Integer> dekGenerationOf(UUID tenantId) {
        return jdbc.query("SELECT dek_generation FROM tenant_keys WHERE tenant_id = ?",
                (rs, n) -> rs.getInt(1), tenantId).stream().findFirst();
    }

    @Override
    public java.util.Optional<byte[]> wrappedDekOf(UUID tenantId) {
        return firstNonNull(jdbc.query("SELECT wrapped_dek FROM tenant_keys WHERE tenant_id = ?",
                (rs, n) -> rs.getBytes(1), tenantId));
    }

    @Override
    public java.util.Optional<byte[]> wrappedBiKeyOf(UUID tenantId) {
        return firstNonNull(jdbc.query("SELECT wrapped_bi_key FROM tenant_keys WHERE tenant_id = ?",
                (rs, n) -> rs.getBytes(1), tenantId));
    }

    /**
     * A nullable BYTEA column maps to a null element, and {@code findFirst()} throws on
     * a null first element. Both key columns are nullable in the schema, so an absent
     * key has to read as an empty Optional; otherwise "no index key yet" surfaces as a
     * NullPointerException that looks like a database fault.
     */
    private static java.util.Optional<byte[]> firstNonNull(java.util.List<byte[]> rows) {
        return rows.stream().filter(java.util.Objects::nonNull).findFirst();
    }

    @Override
    public java.util.Optional<Integer> biKeyGenerationOf(UUID tenantId) {
        return jdbc.query("SELECT bi_key_generation FROM tenant_keys WHERE tenant_id = ?",
                (rs, n) -> rs.getInt(1), tenantId).stream().findFirst();
    }

    @Override
    public List<UUID> tenantIdsWithKeys() {
        return jdbc.queryForList("SELECT tenant_id FROM tenant_keys ORDER BY tenant_id", UUID.class);
    }

    @Override
    public int insert(UUID tenantId, byte[] wrappedDek, byte[] wrappedBiKey) {
        if (dekGenerationOf(tenantId).isPresent()) {
            return 0;
        }
        return jdbc.update(INSERT, tenantId, wrappedDek, wrappedBiKey);
    }

    private static final String INSERT_BI = """
            UPDATE tenant_keys SET wrapped_bi_key = ?, updated_at = now()
            WHERE tenant_id = ?
            """;

    @Override
    public int insertBlindIndexKey(UUID tenantId, byte[] wrappedBiKey) {
        if (dekGenerationOf(tenantId).isEmpty()) {
            return insert(tenantId, new byte[32], wrappedBiKey);
        }
        return jdbc.update(INSERT_BI, wrappedBiKey, tenantId);
    }

    @Override
    public int replaceWrappedDek(UUID tenantId, byte[] wrappedDek) {
        return jdbc.update("UPDATE tenant_keys SET wrapped_dek = ?, updated_at = now() "
                + "WHERE tenant_id = ?", wrappedDek, tenantId);
    }

    @Override
    public int replaceWrappedBiKey(UUID tenantId, byte[] wrappedBiKey) {
        return jdbc.update("UPDATE tenant_keys SET wrapped_bi_key = ?, updated_at = now() "
                + "WHERE tenant_id = ?", wrappedBiKey, tenantId);
    }

    @Override
    public int setKekVersion(UUID tenantId, int kekVersion) {
        return jdbc.update("UPDATE tenant_keys SET wrapped_kek_version = ?, updated_at = now() "
                + "WHERE tenant_id = ?", kekVersion, tenantId);
    }

    @Override
    public int advanceDekGeneration(UUID tenantId, int generation) {
        return jdbc.update("UPDATE tenant_keys SET dek_generation = ?, updated_at = now() "
                + "WHERE tenant_id = ?", generation, tenantId);
    }

    @Override
    public int advanceBiKeyGeneration(UUID tenantId, int generation) {
        return jdbc.update("UPDATE tenant_keys SET bi_key_generation = ?, updated_at = now() "
                + "WHERE tenant_id = ?", generation, tenantId);
    }

    @Override
    public List<UUID> findTenantsPendingKekVersion(int kekVersion) {
        return jdbc.queryForList("SELECT tenant_id FROM tenant_keys WHERE wrapped_kek_version < ? "
                + "ORDER BY tenant_id", UUID.class, kekVersion);
    }
}
