package com.medos.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies {@code database/migrations/V7__enquiry_desk.sql} against real PostgreSQL
 * (env-gated, see {@link PiiKeyLifecycleMigrationTest} for how to run it).
 *
 * <p>Two enquiries tables arrive in Wave 1, and both are tenant-scoped entity
 * tables — the structural assertions here pin the parts the runtime can't
 * discover later from H2: the per-tenant uniqueness of the human reference
 * ({@code (tenant_id, enquiry_no)}), the status/type CHECKs that make an
 * unexpected enum value a database error rather than a silent badge, and the
 * status history's FK cascade (cancel-then-delete must not orphan trail rows).
 */
class EnquiryDeskMigrationTest {

    private static final Path MIGRATIONS = Path.of("..", "database", "migrations");

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

    private Connection freshDatabase() throws Exception {
        String url = System.getenv("MEDOS_MIGRATION_TEST_URL");
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

    private Map<String, String> columns(Connection conn, String table) throws Exception {
        Map<String, String> cols = new HashMap<>();
        try (var ps = conn.prepareStatement(
                "SELECT column_name, data_type||'|'||is_nullable||'|'||COALESCE(column_default,'-') "
                        + "FROM information_schema.columns WHERE table_schema='public' AND table_name=?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) cols.put(rs.getString(1), rs.getString(2));
            }
        }
        return cols;
    }

    private int namedConstraints(Connection conn, String table, String name) throws Exception {
        try (var ps = conn.prepareStatement(
                "SELECT count(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema='public' AND table_name=? AND constraint_name=?")) {
            ps.setString(1, table);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @Test
    @DisplayName("V7 applies cleanly and carries the enquiry desk's invariants")
    void migrationAppliesWithInvariants() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);

            Map<String, String> e = columns(conn, "enquiries");
            assertThat(e).containsOnlyKeys("id", "tenant_id", "enquiry_no", "contact_name",
                    "contact_phone", "contact_email", "enquiry_type", "department", "reason",
                    "status", "notes", "converted_patient_id", "created_by", "created_at", "updated_at");
            assertThat(e.get("id")).startsWith("uuid|NO");
            assertThat(e.get("tenant_id")).startsWith("uuid|NO");
            // Human reference: per-tenant integer, not nullable.
            assertThat(e.get("enquiry_no")).startsWith("integer|NO");
            assertThat(e.get("contact_name")).startsWith("text|NO");
            assertThat(e.get("reason")).startsWith("text|NO");
            assertThat(e.get("status")).startsWith("text|NO");
            // A conversion starts without a patient to point at.
            assertThat(e.get("converted_patient_id")).startsWith("uuid|YES");

            assertThat(namedConstraints(conn, "enquiries", "chk_enquiries_status")).isEqualTo(1);
            assertThat(namedConstraints(conn, "enquiries", "chk_enquiries_type")).isEqualTo(1);
            assertThat(namedConstraints(conn, "enquiries", "chk_enquiries_no")).isEqualTo(1);
            assertThat(namedConstraints(conn, "enquiries", "fk_enquiries_tenant")).isEqualTo(1);
            assertThat(namedConstraints(conn, "enquiries", "fk_enquiries_converted_patient")).isEqualTo(1);

            Map<String, String> h = columns(conn, "enquiry_status_history");
            assertThat(h).containsOnlyKeys("id", "tenant_id", "enquiry_id", "from_status",
                    "to_status", "note", "changed_by", "changed_at");
            assertThat(h.get("to_status")).startsWith("text|NO");
            assertThat(namedConstraints(conn, "enquiry_status_history",
                    "fk_enquiry_status_history_enquiry")).isEqualTo(1);

            UUID tenant = UUID.randomUUID();
            try (var ps = conn.prepareStatement(
                    "INSERT INTO tenants (id, name, type, slug, active) VALUES (?,?,?,?,true)")) {
                ps.setObject(1, tenant);
                ps.setString(2, "Enquiry Test Clinic");
                ps.setString(3, "HOSPITAL");
                ps.setString(4, "enquiry-test");
                ps.executeUpdate();
            }

            // Behavioural: the enum values the desk uses are legal...
            UUID enquiry = UUID.randomUUID();
            try (var ps = conn.prepareStatement(
                    "INSERT INTO enquiries (id, tenant_id, enquiry_no, contact_name, reason) "
                            + "VALUES (?,?,?,?,?)")) {
                ps.setObject(1, enquiry);
                ps.setObject(2, tenant);
                ps.setInt(3, 1);
                ps.setString(4, "Anita Sharma");
                ps.setString(5, "Wants a cardiology appointment");
                ps.executeUpdate();
            }
            // ...and an unknown status is a database error, not a silent badge.
            try (var ps = conn.prepareStatement(
                    "INSERT INTO enquiries (id, tenant_id, enquiry_no, contact_name, reason, status) "
                            + "VALUES (?,?,?,?,?,'renegotiating')")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenant);
                ps.setInt(3, 2);
                ps.setString(4, "Somebody");
                ps.setString(5, "reason");
                ps.execute();
                fail("unknown enquiry status must be refused by chk_enquiries_status");
            } catch (Exception expected) {
                // Postgres names the violated CHECK in the message.
                assertThat(expected.getMessage())
                        .containsIgnoringCase("chk_enquiries_status");
            }

            // The per-tenant uniqueness: same number inside one tenant is refused.
            try (var ps = conn.prepareStatement(
                    "INSERT INTO enquiries (id, tenant_id, enquiry_no, contact_name, reason) "
                            + "VALUES (?,?,?,?,?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenant);
                ps.setInt(3, 1);
                ps.setString(4, "Second Person");
                ps.setString(5, "reason");
                ps.execute();
                fail("duplicate per-tenant enquiry_no must be refused by uq_enquiries_tenant_no");
            } catch (Exception expected) {
                assertThat(expected.getMessage()).containsIgnoringCase("uq_enquiries_tenant_no");
            }
        }
    }

    @Test
    @DisplayName("V7 is not idempotent — replaying it fails the way Flyway must catch")
    void migrationIsNotAccidentallyIdempotent() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);
            String v7 = Files.readString(
                    Path.of("..", "database", "migrations", "V7__enquiry_desk.sql"));
            try (Statement st = conn.createStatement()) {
                st.execute(v7);
                fail("replaying V7 must fail (CREATE TABLE without IF NOT EXISTS)");
            } catch (Exception expected) {
                assertThat(expected.getMessage()).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("status history cascades with its enquiry and records transitions")
    void historyCascadesAndRecords() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);
            UUID tenant = UUID.randomUUID();
            UUID enquiry = UUID.randomUUID();
            try (var ps = conn.prepareStatement(
                    "INSERT INTO tenants (id, name, type, slug, active) VALUES (?,?,?,?,true)")) {
                ps.setObject(1, tenant);
                ps.setString(2, "Cascade Test");
                ps.setString(3, "HOSPITAL");
                ps.setString(4, "cascade-test");
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement(
                    "INSERT INTO enquiries (id, tenant_id, enquiry_no, contact_name, reason) "
                            + "VALUES (?,?,?,?,?)")) {
                ps.setObject(1, enquiry);
                ps.setObject(2, tenant);
                ps.setInt(3, 1);
                ps.setString(4, "Anita Sharma");
                ps.setString(5, "reason");
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement(
                    "INSERT INTO enquiry_status_history (id, tenant_id, enquiry_id, from_status, to_status) "
                            + "VALUES (?,?,?,?,?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenant);
                ps.setObject(3, enquiry);
                ps.setString(4, "new");
                ps.setString(5, "follow_up");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM enquiry_status_history WHERE enquiry_id=?")) {
                ps.setObject(1, enquiry);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next() ? rs.getInt(1) : 0).isEqualTo(1);
                }
            }
            // Deleting the enquiry cascades: the desk's trail never outlives its subject.
            try (var ps = conn.prepareStatement("DELETE FROM enquiries WHERE id=?")) {
                ps.setObject(1, enquiry);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM enquiry_status_history WHERE enquiry_id=?")) {
                ps.setObject(1, enquiry);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next() ? rs.getInt(1) : 0).isZero();
                }
            }
        }
    }
}
