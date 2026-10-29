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
 * Verifies {@code database/migrations/V14__discharge_summaries.sql} against real
 * PostgreSQL (env-gated, see {@link TenantActiveNotNullMigrationTest} for how to
 * run it).
 *
 * <p>The discharge summary is a signed clinical document, so the structural
 * assertions here pin the invariants the runtime cannot discover later from H2:
 * one summary per admission ({@code uq_discharge_summaries_tenant_admission}),
 * the composite tenant FKs that make a cross-tenant reference a storage error,
 * and the addendum cascade. The "replaying V14 fails" case is the Flyway
 * checksum contract, asserted the same way for V7.
 */
class DischargeSummaryMigrationTest {

    private static final Path MIGRATIONS = Path.of("..", "database", "migrations");

    private void applyAll(Connection conn) throws Exception {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(MIGRATIONS)) {
            stream.filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted(TenantActiveNotNullMigrationTest.byVersion())
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
                "SELECT column_name, data_type||'|'||is_nullable "
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
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private UUID insertTenant(Connection conn, String slug) throws Exception {
        UUID tenant = UUID.randomUUID();
        try (var ps = conn.prepareStatement(
                "INSERT INTO tenants (id, name, type, slug, active) VALUES (?,?,?,?,true)")) {
            ps.setObject(1, tenant);
            ps.setString(2, "Discharge Test Clinic (" + slug + ")");
            ps.setString(3, "HOSPITAL");
            ps.setString(4, slug);
            ps.executeUpdate();
        }
        return tenant;
    }

    /**
     * A patient, an admitted bed, and the signed discharge document for it —
     * the minimum chain an addendum or a duplicate-discharge attempt hangs from.
     */
    private UUID insertSignedSummary(Connection conn, UUID tenant) throws Exception {
        UUID patient = UUID.randomUUID();
        UUID room = UUID.randomUUID();
        UUID admission = UUID.randomUUID();
        UUID summary = UUID.randomUUID();

        try (var ps = conn.prepareStatement(
                "INSERT INTO patients (id, tenant_id, version, uhid, name, dpdp_consent) "
                        + "VALUES (?,?,?,?,?,false)")) {
            ps.setObject(1, patient);
            ps.setObject(2, tenant);
            ps.setLong(3, 0L);
            ps.setString(4, "UHID-" + patient.toString().substring(0, 8));
            ps.setString(5, "Patient " + patient.toString().substring(0, 4));
            ps.executeUpdate();
        }
        try (var ps = conn.prepareStatement(
                "INSERT INTO rooms (id, tenant_id, room_number, room_type, occupied, version, ward, "
                        + "capacity, daily_rate) "
                        + "VALUES (?,?,?,?,false,0,'ward-a',1,1.00)")) {
            ps.setObject(1, room);
            ps.setObject(2, tenant);
            ps.setString(3, "D-" + room.toString().substring(0, 4));
            ps.setString(4, "general");
            ps.executeUpdate();
        }
        try (var ps = conn.prepareStatement(
                "INSERT INTO admissions (id, tenant_id, patient_id, room_id, version, status) "
                        + "VALUES (?,?,?,?,0,'admitted')")) {
            ps.setObject(1, admission);
            ps.setObject(2, tenant);
            ps.setObject(3, patient);
            ps.setObject(4, room);
            ps.executeUpdate();
        }
        try (var ps = conn.prepareStatement(
                "INSERT INTO discharge_summaries (id, tenant_id, admission_id, patient_id, "
                        + "discharging_clinician_id, discharging_clinician_name, "
                        + "admission_diagnosis, discharge_diagnosis, clinical_course, "
                        + "procedures_performed, investigations_summary, vitals_summary, "
                        + "medications_at_discharge, condition_at_discharge, follow_up_advice, "
                        + "signed_at) VALUES (?,?,?,?,?,'Dr A Sharma',"
                        + "'enc','enc','Not recorded','Not recorded','Not recorded',"
                        + "'Not recorded','Not recorded','stable','Not recorded',now())")) {
            ps.setObject(1, summary);
            ps.setObject(2, tenant);
            ps.setObject(3, admission);
            ps.setObject(4, patient);
            ps.setObject(5, UUID.randomUUID());
            ps.executeUpdate();
        }
        return summary;
    }

    @Test
    @DisplayName("V14 applies cleanly and carries the discharge document's invariants")
    void migrationAppliesWithInvariants() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);

            Map<String, String> s = columns(conn, "discharge_summaries");
            assertThat(s.keySet()).containsOnly("id", "tenant_id", "admission_id", "patient_id",
                    "discharging_clinician_id", "discharging_clinician_name",
                    "admission_diagnosis", "discharge_diagnosis", "clinical_course",
                    "procedures_performed", "investigations_summary", "vitals_summary",
                    "medications_at_discharge", "condition_at_discharge", "follow_up_advice",
                    "next_review_date", "signed_at");
            assertThat(s.get("tenant_id")).startsWith("uuid|NO");
            // Signed sections are definite text, never an ambiguous NULL.
            for (String section : List.of("admission_diagnosis", "discharge_diagnosis",
                    "clinical_course", "procedures_performed", "investigations_summary",
                    "vitals_summary", "medications_at_discharge", "condition_at_discharge",
                    "follow_up_advice")) {
                assertThat(s.get(section)).as(section).startsWith("text|NO");
            }
            assertThat(s.get("next_review_date")).startsWith("date|YES");
            // Postgres names the plain timestamp type "timestamp without time zone".
            assertThat(s.get("signed_at")).startsWith("timestamp without time zone|NO");

            Map<String, String> a = columns(conn, "discharge_summary_addenda");
            assertThat(a.keySet()).containsOnly("id", "tenant_id", "discharge_summary_id",
                    "text", "authored_by", "author_name", "created_at");
            assertThat(a.get("text")).startsWith("text|NO");

            assertThat(namedConstraints(conn, "discharge_summaries",
                    "uq_discharge_summaries_tenant_admission")).isEqualTo(1);
            assertThat(namedConstraints(conn, "discharge_summaries",
                    "uq_discharge_summaries_tenant_id")).isEqualTo(1);
            assertThat(namedConstraints(conn, "discharge_summaries",
                    "fk_discharge_summaries_admission")).isEqualTo(1);
            assertThat(namedConstraints(conn, "discharge_summaries",
                    "fk_discharge_summaries_patient")).isEqualTo(1);
            assertThat(namedConstraints(conn, "discharge_summary_addenda",
                    "fk_discharge_summary_addenda_summary")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a second signed summary for the same admission is refused")
    void secondDischargeSummaryIsRefused() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);
            UUID tenant = insertTenant(conn, "dup-test");
            UUID summary = insertSignedSummary(conn, tenant);

            assertThat(count(conn, "SELECT count(*) FROM discharge_summaries")).isEqualTo(1);
            assertThat(summary).isNotNull();

            // Duplicate: same (tenant_id, admission_id), new identity.
            try (var ps = conn.prepareStatement(
                    "INSERT INTO discharge_summaries (id, tenant_id, admission_id, patient_id, "
                            + "discharging_clinician_id, discharging_clinician_name, "
                            + "admission_diagnosis, discharge_diagnosis, clinical_course, "
                            + "procedures_performed, investigations_summary, vitals_summary, "
                            + "medications_at_discharge, condition_at_discharge, follow_up_advice, "
                            + "signed_at) "
                            + "SELECT ?, s.tenant_id, s.admission_id, s.patient_id, s.discharging_clinician_id, "
                            + "s.discharging_clinician_name, s.admission_diagnosis, s.discharge_diagnosis, "
                            + "s.clinical_course, s.procedures_performed, s.investigations_summary, "
                            + "s.vitals_summary, s.medications_at_discharge, s.condition_at_discharge, "
                            + "s.follow_up_advice, now() "
                            + "FROM discharge_summaries s WHERE s.id = ?")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, summary);
                try {
                    ps.executeUpdate();
                    fail("a second summary for the same admission must be refused by "
                            + "uq_discharge_summaries_tenant_admission");
                } catch (Exception expected) {
                    assertThat(expected.getMessage())
                            .containsIgnoringCase("uq_discharge_summaries_tenant_admission");
                }
            }
        }
    }

    @Test
    @DisplayName("cross-tenant references and the addendum cascade hold at the storage layer")
    void tenantBoundaryAndCascadeHold() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);
            UUID tenant = insertTenant(conn, "boundary-test");
            UUID summary = insertSignedSummary(conn, tenant);

            UUID addendum = appendAddendum(conn, tenant, summary, "ctx");

            // A correction cannot hang off a summary in another tenant: the
            // composite FK pairs the id with the tenant every time.
            try {
                appendAddendum(conn, UUID.randomUUID(), summary, "ctx");
                fail("a cross-tenant addendum must be refused by "
                        + "fk_discharge_summary_addenda_summary");
            } catch (Exception expected) {
                assertThat(expected.getMessage())
                        .containsIgnoringCase("fk_discharge_summary_addenda_summary");
            }

            // Deleting the summary cascades: corrections never outlive their document.
            try (var ps = conn.prepareStatement("DELETE FROM discharge_summaries WHERE id=?")) {
                ps.setObject(1, summary);
                ps.executeUpdate();
            }
            assertThat(count(conn, "SELECT count(*) FROM discharge_summary_addenda"))
                    .as("addendum rows after cascade").isZero();
        }
    }

    private UUID appendAddendum(Connection conn, UUID tenant, UUID summary, String text)
            throws Exception {
        UUID addendum = UUID.randomUUID();
        try (var ps = conn.prepareStatement(
                "INSERT INTO discharge_summary_addenda (id, tenant_id, discharge_summary_id, "
                        + "text, authored_by, author_name, created_at) VALUES (?,?,?,?,?,?,now())")) {
            ps.setObject(1, addendum);
            ps.setObject(2, tenant);
            ps.setObject(3, summary);
            ps.setString(4, text);
            ps.setObject(5, UUID.randomUUID());
            ps.setString(6, "Dr A Sharma");
            ps.executeUpdate();
        }
        return addendum;
    }

    private int count(Connection conn, String sql) throws Exception {
        try (var st = conn.createStatement(); var rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    @DisplayName("V14 is not idempotent — replaying it fails the way Flyway must catch")
    void migrationIsNotAccidentallyIdempotent() throws Exception {
        try (Connection conn = freshDatabase()) {
            applyAll(conn);
            String v14 = Files.readString(
                    Path.of("..", "database", "migrations", "V14__discharge_summaries.sql"));
            try (Statement st = conn.createStatement()) {
                st.execute(v14);
                fail("replaying V14 must fail (CREATE TABLE without IF NOT EXISTS)");
            } catch (Exception expected) {
                assertThat(expected.getMessage()).isNotBlank();
            }
        }
    }
}
