package com.medos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Seed and migration scripts must not write plaintext into an encrypted column.
 *
 * <p>{@code name}, {@code phone}, {@code email} and {@code address} on {@code patients} hold
 * AES-GCM ciphertext, versioned as {@code kv1.d<generation>:<base64>}. A row inserted by raw
 * SQL lands in that column unencrypted.
 *
 * <p>The consequence is not a visible failure. {@link com.medos.util.PiiCiphertextFormat}
 * deliberately tolerates a value with no version prefix so pre-encryption rows can be
 * migrated (#90), which means a plaintext row is served back to callers <em>as though it
 * were fine</em>. No exception, no warning: just patient names readable in a table that is
 * supposed to hold only ciphertext, and a blind index that cannot be computed for them, so
 * those patients are also unfindable by name.
 *
 * <p>That is why this is a test and not a convention. A seed script is exactly the kind of
 * file nobody re-reads, and the failure mode is silence.
 */
class SeededDataIsEncryptedTest {

    private static final Path SEED_SQL = Path.of("..", "tools", "seed-dev.sql");
    private static final Path SEED_SH = Path.of("..", "tools", "seed-dev.sh");

    /** Matches an INSERT into patients listing the encrypted PII columns. */
    private static final Pattern PATIENTS_INSERT = Pattern.compile(
            "insert\\s+into\\s+patients\\b[^;]*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Test
    @DisplayName("no script inserts rows into patients directly")
    void patientsAreNotInsertedBySql() throws Exception {
        String sql = Files.readString(SEED_SQL).toLowerCase(Locale.ROOT);

        assertThat(PATIENTS_INSERT.matcher(sql).find())
                .as("""
                        tools/seed-dev.sql inserts into patients. Those columns hold ciphertext,
                        so the rows land unencrypted and are then served back as if valid.
                        Demo patients must be created through POST /api/v1/patients, which is
                        the only path that encrypts and computes the name index.
                        """)
                .isFalse();
    }

    @Test
    @DisplayName("the seed creates demo patients through the API, not through SQL")
    void seedCreatesPatientsThroughTheApi() throws Exception {
        String script = Files.readString(SEED_SH).toLowerCase(Locale.ROOT);

        assertThat(script)
                .as("""
                        tools/seed-dev.sh must provision demo patients over HTTP so the
                        application's encrypting path is what writes them.
                        """)
                .contains("/api/v1/patients");
    }

    @Test
    @DisplayName("the seed no longer claims to seed patients via SQL in its own output")
    void seedDoesNotMisreportWhatItSeeds() throws Exception {
        String script = Files.readString(SEED_SH).toLowerCase(Locale.ROOT);

        assertThat(script)
                .as("the script's own output must not tell an operator it seeded patients "
                        + "when it did not")
                .doesNotContain("seeds demo users/patients");
    }

    @Test
    @DisplayName("migrations do not insert literal PII into patients")
    void migrationsDoNotInsertLiteralPii() throws Exception {
        List<Path> migrations;
        try (var stream = Files.list(Path.of("..", "database", "migrations"))) {
            migrations = stream.filter(p -> p.toString().endsWith(".sql")).toList();
        }
        assertThat(migrations).isNotEmpty();

        for (Path migration : migrations) {
            String sql = Files.readString(migration).toLowerCase(Locale.ROOT);
            var matcher = PATIENTS_INSERT.matcher(sql);
            assertThat(matcher.find())
                    .as("%s inserts into patients; PII must be written by the application, "
                            + "never by a migration".formatted(migration.getFileName()))
                    .isFalse();
        }
    }
}