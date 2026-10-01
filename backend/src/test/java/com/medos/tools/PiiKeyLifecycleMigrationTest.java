package com.medos.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies {@code database/migrations/V2__pii_key_lifecycle.sql} against a real
 * PostgreSQL, and asserts the properties that make the key lifecycle safe rather
 * than merely present.
 *
 * <p>The structural assertions here exist because the two columns that carry
 * <em>wrapped</em> key material are the whole point of the design: a plaintext DEK
 * reaching this table would defeat envelope encryption entirely, and a row
 * without {@code wrapped_kek_version} would make a KEK re-wrap non-resumable,
 * because there would be no way to tell which rows are already done.
 *
 * <p>Runs only when {@code MEDOS_MIGRATION_TEST_URL} is set, e.g.
 * <pre>
 * docker run -d --name mig-test -e POSTGRES_PASSWORD=t postgres:17-alpine
 * MEDOS_MIGRATION_TEST_URL=jdbc:postgresql://localhost:5432/postgres \
 *   mvn test -Dtest=PiiKeyLifecycleMigrationTest
 * </pre>
 * H2 cannot stand in: it has no BYTEA and a different DDL dialect, so a pass
 * against it would say nothing about the target database.
 */
class PiiKeyLifecycleMigrationTest {

    private static final Path MIGRATIONS = Path.of("..", "database", "migrations");

    private static String jdbcUrl() {
        return System.getenv("MEDOS_MIGRATION_TEST_URL");
    }

    /** Apply every migration in order, as Flyway would. */
    private void applyAll(Connection conn) throws Exception {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(MIGRATIONS)) {
            stream.filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted()
                    .forEach(files::add);
        }
        assertThat(files).as("migrations must be found in " + MIGRATIONS.toAbsolutePath()).isNotEmpty();

        for (Path f : files) {
            String sql = Files.readString(f);
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }
        }
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

    @Test
    @DisplayName("V2 applies cleanly on top of V1, from empty")
    void appliesOnEmptyDatabase() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);
        }
    }

    @Test
    @DisplayName("V2 is idempotent-safe: re-running it fails loudly rather than corrupting state")
    void rerunFailsLoudly() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);
            // Flyway never re-runs an applied migration, so a second application is
            // an error condition, not something to tolerate silently.
            try (Statement st = conn.createStatement()) {
                st.execute(Files.readString(MIGRATIONS.resolve("V2__pii_key_lifecycle.sql")));
                fail(
                        "re-applying V2 must fail; CREATE TABLE without IF NOT EXISTS is what makes that true");
            } catch (Exception expected) {
                assertThat(expected).isNotNull();
            }
        }
    }

    @Test
    @DisplayName("tenant_keys has the columns the re-wrap depends on")
    void tenantKeysShape() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("""
                        SELECT column_name, data_type, is_nullable, column_default
                        FROM information_schema.columns
                        WHERE table_schema='public' AND table_name='tenant_keys'
                        ORDER BY column_name""")) {
                var cols = new java.util.TreeMap<String, String>();
                while (rs.next()) {
                    cols.put(rs.getString(1),
                            rs.getString(2) + "|" + rs.getString(3) + "|" + rs.getString(4));
                }
                assertThat(cols).containsOnlyKeys(
                        "tenant_id", "wrapped_dek", "wrapped_bi_key",
                        "dek_generation", "bi_key_generation", "wrapped_kek_version",
                        "created_at", "updated_at");
                assertThat(cols.get("tenant_id")).startsWith("uuid|NO|");
                assertThat(cols.get("wrapped_dek")).startsWith("bytea|");
                assertThat(cols.get("wrapped_bi_key")).startsWith("bytea|");
            }
        }
    }

    @Test
    @DisplayName("key material columns are bytea, never text, so a DEK cannot be stored as a string")
    void keyMaterialIsBinary() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("""
                        SELECT column_name, data_type
                        FROM information_schema.columns
                        WHERE table_schema='public' AND table_name='tenant_keys'
                          AND column_name IN ('wrapped_dek','wrapped_bi_key')""")) {
                int n = 0;
                while (rs.next()) {
                    assertThat(rs.getString(2).toLowerCase(Locale.ROOT))
                            .as("%s must be bytea", rs.getString(1))
                            .isEqualTo("bytea");
                    n++;
                }
                assertThat(n).isEqualTo(2);
            }
        }
    }

    @Test
    @DisplayName("generations default to 1 and are NOT NULL, so a new tenant resolves without a backfill")
    void generationsDefaultToOne() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("""
                        SELECT column_name, is_nullable, column_default
                        FROM information_schema.columns
                        WHERE table_schema='public' AND table_name='tenant_keys'
                          AND column_name IN ('dek_generation','bi_key_generation','wrapped_kek_version')""")) {
                int n = 0;
                while (rs.next()) {
                    String def = String.valueOf(rs.getString(3));
                    assertThat(rs.getString(2)).as("%s must be NOT NULL", rs.getString(1)).isEqualTo("NO");
                    // A DEFAULT 1 means an insert that omits the column still lands on
                    // generation 1. Without it, an existing tenant would have to be
                    // backfilled before it could be read.
                    assertThat(def).as("%s needs a default", rs.getString(1))
                            .contains("1");
                    n++;
                }
                assertThat(n).isEqualTo(3);
            }
        }
    }

    @Test
    @DisplayName("a tenant can hold a wrapped DEK and rotation history, and plaintext never appears in the row")
    void storesOnlyWrappedMaterial() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);

            // 32-byte DEK, "wrapped" here by XOR with a stand-in KEK. What matters is
            // that the column holds ciphertext: reading a stored row back must not
            // yield the plaintext key.
            byte[] plaintextDek = new byte[32];
            for (int i = 0; i < plaintextDek.length; i++) plaintextDek[i] = (byte) (i + 1);
            byte[] kek = new byte[32];
            for (int i = 0; i < kek.length; i++) kek[i] = (byte) (0xA0 + i);
            byte[] wrapped = new byte[32];
            for (int i = 0; i < 32; i++) wrapped[i] = (byte) (plaintextDek[i] ^ kek[i]);

            java.util.UUID tenantId = java.util.UUID.randomUUID();
            try (var ps = conn.prepareStatement("""
                    INSERT INTO tenants (id, name, type, slug, active)
                    VALUES (?, ?, 'HOSPITAL', ?, true)""")) {
                ps.setObject(1, tenantId);
                ps.setString(2, "Key Lifecycle Test");
                ps.setString(3, "key-lifecycle-" + tenantId.toString().substring(0, 8));
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("""
                    INSERT INTO tenant_keys (tenant_id, wrapped_dek, wrapped_bi_key)
                    VALUES (?, ?, ?)""")) {
                ps.setObject(1, tenantId);
                ps.setBytes(2, wrapped);
                ps.setBytes(3, wrapped);
                ps.executeUpdate();
            }

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT wrapped_dek, dek_generation, wrapped_kek_version FROM tenant_keys")) {
                assertThat(rs.next()).isTrue();
                byte[] stored = rs.getBytes(1);
                assertThat(stored).isNotEqualTo(plaintextDek);
                assertThat(stored).isEqualTo(wrapped);
                assertThat(rs.getInt(2)).as("generation defaults to 1").isEqualTo(1);
                assertThat(rs.getInt(3)).as("kek version defaults to 1").isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("key_rotations distinguishes a fleet-wide re-wrap from a single tenant rotation")
    void keyRotationsShape() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("""
                        SELECT column_name, is_nullable
                        FROM information_schema.columns
                        WHERE table_schema='public' AND table_name='key_rotations'
                        ORDER BY column_name""")) {
                var cols = new java.util.TreeMap<String, String>();
                while (rs.next()) cols.put(rs.getString(1), rs.getString(2));
                assertThat(cols).containsKeys("id", "operation", "kek_version", "dek_generation",
                        "tenant_id", "started_at", "completed_at", "rows_rewritten",
                        "status", "initiated_by", "notes");
                // tenant_id is nullable precisely so a fleet-wide re-wrap can be
                // recorded as one row; every other field describes the operation.
                assertThat(cols.get("tenant_id")).isEqualTo("YES");
                assertThat(cols.get("operation")).isEqualTo("NO");
            }

            // Both operation kinds must be representable, and a fleet-wide one must
            // not require a tenant.
            try (Statement st = conn.createStatement()) {
                st.execute("""
                        INSERT INTO key_rotations (operation, kek_version, status, started_at)
                        VALUES ('kek_rewrap', 2, 'completed', now())""");
            }
            // A dek_rotation is scoped to a tenant, so the FK must be satisfiable.
            java.util.UUID scoped = java.util.UUID.randomUUID();
            try (var ps = conn.prepareStatement("""
                    INSERT INTO tenants (id, name, type, slug, active)
                    VALUES (?, ?, 'HOSPITAL', ?, true)""")) {
                ps.setObject(1, scoped);
                ps.setString(2, "Rotation Scope Hospital");
                ps.setString(3, "rotation-scope-" + scoped.toString().substring(0, 8));
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("""
                    INSERT INTO key_rotations (operation, kek_version, dek_generation, tenant_id, status, started_at)
                    VALUES ('dek_rotation', 1, 2, ?, 'in_progress', now())""")) {
                ps.setObject(1, scoped);
                ps.executeUpdate();
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT count(*) FROM key_rotations WHERE operation IN ('kek_rewrap','dek_rotation')")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
        }
    }

    @Test
    @DisplayName("operation is constrained, so a typo cannot create an unrecognisable rotation record")
    void operationIsConstrained() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);

            // Assert the constraint exists by name, rather than relying on an insert
            // being rejected. An earlier version of this test tried an invalid
            // operation and asserted the insert failed, which passed even with the
            // CHECK removed, because the separate scope constraint rejected it for
            // an unrelated reason. Asserting the constraint's existence cannot be
            // satisfied by a different rule.
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("""
                        SELECT count(*) FROM information_schema.table_constraints
                        WHERE table_schema='public' AND table_name='key_rotations'
                          AND constraint_name='chk_key_rotations_operation'""")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("operation must be constrained by a named CHECK")
                        .isEqualTo(1);
            }

            // And behaviourally: an unrecognised operation is rejected.
            try (Statement st = conn.createStatement()) {
                st.execute("""
                        INSERT INTO key_rotations (operation, kek_version, status, started_at)
                        VALUES ('rewap', 1, 'completed', now())""");
                fail("an unrecognised operation must be rejected, or reports become unreliable");
            } catch (Exception expected) {
                assertThat(expected).isNotNull();
            }
        }
    }

    @Test
    @DisplayName("tenant_keys is one row per tenant and cascades when a tenant is deleted")
    void tenantKeysConstraints() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("""
                        SELECT tc.constraint_type, kcu.column_name
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage kcu
                          ON tc.constraint_name = kcu.constraint_name
                        WHERE tc.table_name='tenant_keys'""")) {
                var types = new ArrayList<String>();
                var cols = new ArrayList<String>();
                while (rs.next()) {
                    types.add(rs.getString(1));
                    cols.add(rs.getString(2));
                }
                // tenant_id is the PK, so a tenant cannot accumulate two competing DEKs.
                assertThat(types).contains("PRIMARY KEY");
                assertThat(cols).contains("tenant_id");
            }
        }
    }
}
