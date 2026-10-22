package com.medos.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies {@code database/migrations/V8__appointments_deepening.sql} against real
 * PostgreSQL. The five assertions pin the per-tenant serial, per-day token,
 * the named CHECKs, and the backfill.
 */
class AppointmentDeskMigrationTest {

    private static final Path MIGRATIONS = Path.of("..", "database", "migrations");

    private List<Path> migrationFiles() throws IOException {
        try (var stream = Files.list(MIGRATIONS)) {
            return stream.filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted().collect(Collectors.toList());
        }
    }

    /** V8 migration file path. */
    private Path v8() throws IOException {
        return migrationFiles().stream()
                .filter(p -> p.getFileName().toString().startsWith("V8"))
                .findFirst().orElseThrow(() -> new IllegalStateException("V8 not found"));
    }

    /** Apply all migrations UP TO (but not including) V8. */
    private void applyUpToV7(Connection conn) throws Exception {
        for (Path f : migrationFiles()) {
            if (f.getFileName().toString().startsWith("V8")) break;
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

    private int indexCount(Connection conn, String name) throws Exception {
        try (var ps = conn.prepareStatement(
                "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND indexname=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int namedConstraint(Connection conn, String table, String name) throws Exception {
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

    private void insertLegacyAppointment(Connection conn, UUID tenant, UUID id) throws Exception {
        try (var ps = conn.prepareStatement(
                "INSERT INTO appointments (id, tenant_id, patient_id, doctor_id, "
                        + "appointment_date, status, reason, created_at) "
                        + "VALUES (?,?,?,?,?::timestamp,?,?,?::timestamp)")) {
            ps.setObject(1, id);
            ps.setObject(2, tenant);
            ps.setObject(3, UUID.randomUUID());
            ps.setObject(4, UUID.randomUUID());
            ps.setString(5, "2026-08-01 10:00:00");
            ps.setString(6, "scheduled");
            ps.setString(7, "legacy");
            ps.setString(8, "2026-08-01 10:00:00");
            ps.executeUpdate();
        }
    }

    private Integer appointmentNo(Connection conn, UUID id) throws Exception {
        try (var ps = conn.prepareStatement(
                "SELECT appointment_no FROM appointments WHERE id=?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? (rs.getObject(1) == null ? null : rs.getInt(1)) : null;
            }
        }
    }

    @Test
    @DisplayName("V8 deepens appointments: per-tenant serial, per-day token, checks, backfill live")
    void migrationAppliesWithInvariants() throws Exception {
        try (Connection conn = freshDatabase()) {
            // Apply V1-V7 (legacy shape)
            applyUpToV7(conn);

            UUID tenantA = UUID.randomUUID();
            try (var ps = conn.prepareStatement(
                    "INSERT INTO tenants (id, name, type, slug, active) VALUES (?,?,?,?,true)")) {
                ps.setObject(1, tenantA);
                ps.setString(2, "Appt Test A");
                ps.setString(3, "HOSPITAL");
                ps.setString(4, "appt-test-a");
                ps.executeUpdate();
            }
            UUID legacyA = UUID.randomUUID();
            insertLegacyAppointment(conn, tenantA, legacyA);

            // V8 applies the deepened columns and backfill
            try (Statement st = conn.createStatement()) {
                st.execute(Files.readString(v8()));
            }

            // 1. Per-tenant numbering: the legacy row got a number (order by created_at)
            assertThat(appointmentNo(conn, legacyA)).isNotNull();

            // 2. appointment_type CHECK (first_visit/follow_up legal)
            assertThat(namedConstraint(conn, "appointments", "chk_appointments_type")).isEqualTo(1);

            // 3. Per-day token uniqueness index exists
            assertThat(indexCount(conn, "uq_appointments_tenant_day_token")).isEqualTo(1);

            // 4. Per-tenant appointment_no uniqueness index exists
            assertThat(indexCount(conn, "uq_appointments_tenant_no")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("V8 is not idempotent")
    void migrationIsNotAccidentallyIdempotent() throws Exception {
        try (Connection conn = freshDatabase()) {
            // Apply V1-V7 only (legacy shape)
            applyUpToV7(conn);

            UUID tenant = UUID.randomUUID();
            UUID row = UUID.randomUUID();
            try (var ps = conn.prepareStatement(
                    "INSERT INTO tenants (id, name, type, slug, active) VALUES (?,?,?,?,true)")) {
                ps.setObject(1, tenant);
                ps.setString(2, "Idempotent Test");
                ps.setString(3, "HOSPITAL");
                ps.setString(4, "idempotent-test");
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement(
                    "INSERT INTO appointments (id, tenant_id, patient_id, doctor_id, "
                            + "appointment_date, status) VALUES (?,?,?,?,?::timestamp,?)")) {
                ps.setObject(1, row);
                ps.setObject(2, tenant);
                ps.setObject(3, UUID.randomUUID());
                ps.setObject(4, UUID.randomUUID());
                ps.setString(5, "2026-08-01 10:00:00");
                ps.setString(6, "scheduled");
                ps.executeUpdate();
            }

            // Now apply V8 once (creates the index)
            try (Statement st = conn.createStatement()) {
                st.execute(Files.readString(v8()));
            }
            // Replay V8 (should fail because the index already exists)
            try (Statement st = conn.createStatement()) {
                st.execute(Files.readString(v8()));
                fail("replaying V8 should fail (index already exists)");
            } catch (Exception expected) {
                assertThat(expected.getMessage()).isNotBlank();
            }
        }
    }
}
