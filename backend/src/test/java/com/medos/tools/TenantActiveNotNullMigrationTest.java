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
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies {@code database/migrations/V10__tenant_active_not_null.sql} against
 * real PostgreSQL (issue #53, schema half): {@code tenants.active} ends up
 * NOT NULL with a TRUE default, and the NULL rows that existed before the
 * migration are still active — the documented NULL decision, because a NULL
 * was a never-decided row, not a suspended hospital.
 *
 * <p>Runs only when {@code MEDOS_MIGRATION_TEST_URL} is set, e.g.
 * <pre>
 * docker run -d --name mig-test -e POSTGRES_PASSWORD=t postgres:17-alpine
 * MEDOS_MIGRATION_TEST_URL=jdbc:postgresql://localhost:5432/postgres \
 *   mvn test -Dtest=TenantActiveNotNullMigrationTest
 * </pre>
 * H2 cannot stand in: it does not reproduce Postgres ALTER COLUMN behaviour.
 */
public class TenantActiveNotNullMigrationTest {

    private static final Path MIGRATIONS = Path.of("..", "database", "migrations");

    /**
     * File-name sorting breaks at V10: "V10__…" lexicographically precedes
     * "V1__…" ('0' sorts before '_' in "V1__"), so the naive
     * {@code Files.list(...)} {@code .sorted()} that served V1..V9 would apply
     * V10 while its only dependency (V1) has not run — and V10 references
     * {@code tenants}. Order by the numeric version in the migration name
     * instead, which is the ordering Flyway itself uses.
     */
    public static Comparator<Path> byVersion() {
        return Comparator.comparingInt(p -> {
            String n = p.getFileName().toString();
            return Integer.parseInt(n.substring(1, n.indexOf("__")));
        });
    }

    private static String jdbcUrl() {
        return System.getenv("MEDOS_MIGRATION_TEST_URL");
    }

    /** Apply named migration files in order, whole-file, as Flyway would. */
    private void applyMigrations(Connection conn, List<String> names) throws Exception {
        for (String name : names) {
            String sql = Files.readString(MIGRATIONS.resolve(name));
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }
        }
    }

    /** Execute inline SQL, one entry per statement — simulating migration contents. */
    private void exec(Connection conn, List<String> statements) throws Exception {
        for (String sql : statements) {
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
    @DisplayName("V10 leaves existing NULL rows active, defaults new rows to active, and forbids NULL")
    void activeBecomesNotNullWithTrueDefault() throws Exception {
        try (Connection conn = freshDatabase()) {
            // V1 ships the nullable, default-less column; nothing between V2 and
            // V9 touches tenants.active, so V1 + V10 reproduces the upgrade path.
            applyMigrations(conn, List.of("V1__initial_schema.sql"));

            // Simulate the pre-V10 mess: two rows whose flag was never written
            // (the "never-decided" state), plus one explicitly deactivated hospital.
            exec(conn, List.of(
                    "INSERT INTO tenants (id, name, slug, type) VALUES "
                            + "('11111111-1111-4111-8111-111111111111', 'First General', 'first', 'HOSPITAL'), "
                            + "('22222222-2222-4222-8222-222222222222', 'Second Clinic', 'second', 'CLINIC')",
                    "INSERT INTO tenants (id, name, slug, type, active) VALUES "
                            + "('33333333-3333-4333-8333-333333333333', 'Suspended General', 'suspended', 'HOSPITAL', FALSE)"));

            applyMigrations(conn, List.of("V10__tenant_active_not_null.sql"));

            // NULL backfill: a never-decided row is active, not disabled.
            try (Statement q = conn.createStatement();
                 ResultSet rs = q.executeQuery("SELECT slug, active FROM tenants ORDER BY slug")) {
                List<String> seen = new ArrayList<>();
                while (rs.next()) {
                    seen.add(rs.getString(1) + "=" + rs.getBoolean(2));
                }
                assertThat(seen).containsExactlyInAnyOrder(
                        "first=true", "second=true", "suspended=false");
            }

            // The new default is TRUE: a bare post-V10 insert is active without
            // naming the column at all.
            exec(conn, List.of(
                    "INSERT INTO tenants (id, name, slug) VALUES "
                            + "('44444444-4444-4444-8444-444444444444', 'St Neue', 'st-neue')"));
            try (Statement q = conn.createStatement();
                 ResultSet rs = q.executeQuery(
                         "SELECT active FROM tenants WHERE slug = 'st-neue'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean(1)).isTrue();
            }

            // NOT NULL is enforced, not just defaulted: an explicit NULL insert
            // must be rejected.
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO tenants (id, name, slug, type, active) VALUES (?,?,?,?,NULL)")) {
                ps.setObject(1, java.util.UUID.fromString("55555555-5555-4555-8555-555555555555"));
                ps.setString(2, "Bad");
                ps.setString(3, "bad");
                ps.setString(4, "HOSPITAL");
                try {
                    ps.executeUpdate();
                    fail("active is NOT NULL — a null insert must be rejected");
                } catch (Exception expected) {
                    // constraint violation, as required
                }
            }
        }
    }
}
