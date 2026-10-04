package com.medos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import com.medos.entity.KeyRotation;
import com.medos.repository.KeyRotationRepository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * #88 on real PostgreSQL, against the schema as Flyway actually leaves it.
 *
 * <p>The claim under test is narrow and load-bearing: a KEK re-wrap rewrites only
 * tenant_keys, and every patient row comes out byte-identical. An in-memory store
 * cannot settle that, because {@code bytea} and the real column types are exactly the
 * details a re-wrap depends on. So this applies the real migrations, writes real
 * ciphertext, rotates real key material, and compares a checksum over every column of
 * every patient row.
 *
 * <p>Skipped unless {@code MEDOS_MIGRATION_TEST_URL} is set, which CI does.
 */
class KeyRewrapPostgresTest {

    private static final Path MIGRATIONS = Path.of("..", "database", "migrations");
    private static final SecureRandom RANDOM = new SecureRandom();

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return key;
    }

    private static String jdbcUrl() {
        return System.getenv("MEDOS_MIGRATION_TEST_URL");
    }

    private Connection freshDatabase() throws Exception {
        String url = jdbcUrl();
        assumeTrue(url != null && !url.isBlank(),
                "set MEDOS_MIGRATION_TEST_URL to run the migration tests");
        Properties props = new Properties();
        String user = System.getenv("MEDOS_MIGRATION_TEST_USER");
        String pass = System.getenv("MEDOS_MIGRATION_TEST_PASSWORD");
        if (user != null) props.setProperty("user", user);
        if (pass != null) props.setProperty("password", pass);
        Connection conn = DriverManager.getConnection(url, props);
        conn.createStatement().execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
        return conn;
    }

    private void applyAll(Connection conn) throws Exception {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(MIGRATIONS)) {
            stream.filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted()
                    .forEach(files::add);
        }
        assertThat(files).as("migrations must be found in " + MIGRATIONS.toAbsolutePath()).isNotEmpty();
        for (Path f : files) {
            try (Statement st = conn.createStatement()) {
                st.execute(Files.readString(f));
            }
        }
    }

    @Test
    @DisplayName("a KEK re-wrap changes tenant_keys only; every patient row is byte-identical")
    void rewrapLeavesPatientRowsByteIdentical() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(conn, true));
            TenantKeyStoreJdbc keyStore = new TenantKeyStoreJdbc(jdbc);

            KeyRotationRepository rotations = mock(KeyRotationRepository.class);
            org.mockito.Mockito.lenient().when(rotations.save(any(KeyRotation.class)))
                    .thenAnswer(call -> call.getArgument(0));
            KeyRewrapService service = new KeyRewrapService(keyStore, rotations);

            byte[] kekV1 = randomKey();
            byte[] kekV2 = randomKey();
            byte[] dek = randomKey();

            UUID tenantA = createTenant(jdbc);
            UUID tenantB = createTenant(jdbc);
            keyStore.insert(tenantA, KeyWrapCipher.wrap(dek, kekV1, RANDOM),
                    KeyWrapCipher.wrap(randomKey(), kekV1, RANDOM));
            keyStore.insert(tenantB, KeyWrapCipher.wrap(randomKey(), kekV1, RANDOM),
                    KeyWrapCipher.wrap(randomKey(), kekV1, RANDOM));

            createPatient(jdbc, tenantA);
            createPatient(jdbc, tenantB);

            String patientsBefore = patientsChecksum(jdbc);
            byte[] wrappedBefore = keyStore.wrappedDekOf(tenantA).orElseThrow();

            KeyRewrapService.Result result = service.rotate(kekV1, 2, kekV2, "operator@example.test");

            assertThat(result.status()).isEqualTo(KeyRewrapService.Status.COMPLETED);
            assertThat(result.rowsRewrapped()).isEqualTo(2);

            assertThat(patientsChecksum(jdbc))
                    .as("a KEK re-wrap must not modify a single patient column")
                    .isEqualTo(patientsBefore);

            byte[] wrappedAfter = keyStore.wrappedDekOf(tenantA).orElseThrow();
            assertThat(wrappedAfter).as("the wrapper itself must have moved").isNotEqualTo(wrappedBefore);
            assertThat(KeyWrapCipher.unwrap(wrappedAfter, kekV2))
                    .as("the same DEK must unwrap under the new KEK")
                    .isEqualTo(dek);
            // AES-GCM authenticates, so the retired KEK cannot silently return garbage:
            // it fails with an operator-facing error. That also means the old KEK must be
            // retired from the store once a rotation completes, which is #89's job.
            assertThatThrownBy(() -> KeyWrapCipher.unwrap(wrappedAfter, kekV1))
                    .as("the retired KEK must no longer unwrap the wrapper")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("key-encryption key");
            assertThat(kekVersionOf(jdbc, tenantA)).isEqualTo(2);

            KeyRewrapService.Result second = service.rotate(kekV1, 2, kekV2, "operator@example.test");
            assertThat(second.rowsRewrapped()).isZero();
            assertThat(patientsChecksum(jdbc)).isEqualTo(patientsBefore);
        }
    }

    private int kekVersionOf(JdbcTemplate jdbc, UUID tenantId) {
        return jdbc.queryForObject(
                "SELECT wrapped_kek_version FROM tenant_keys WHERE tenant_id = ?",
                Integer.class, tenantId);
    }

    private UUID createTenant(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (id, name, type, slug, active)
                VALUES (?, ?, 'HOSPITAL', ?, true)
                """, id, "Rewrap Hospital " + id.toString().substring(0, 8),
                "rewrap-" + id.toString().substring(0, 8));
        return id;
    }

    private void createPatient(JdbcTemplate jdbc, UUID tenantId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO patients (id, version, uhid, tenant_id, name, phone, age,
                                      dpdp_consent, created_at, updated_at)
                VALUES (?, 0, ?, ?, ?, ?, 34, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, "UHID" + id.toString().substring(0, 8).toUpperCase(), tenantId,
                "kv1.d1:" + Base64.getEncoder().encodeToString(randomKey()),
                "kv1.d1:" + Base64.getEncoder().encodeToString(randomKey()));
    }

    /** SHA-256 over every column of every patient row, by position so new columns count. */
    private String patientsChecksum(JdbcTemplate jdbc) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            jdbc.query("SELECT * FROM patients ORDER BY id", rs -> {
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    Object value = rs.getObject(i);
                    digest.update(
                            (value == null ? "<null>" : value.toString()).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0x1f);
                }
                digest.update((byte) 0x1e);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JLS", e);
        }
    }
}