package com.medos.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave-0 observability closure: the prod compose file is the deployment's runtime
 * contract, and the JSON log format is chosen by *profile*, not by code. This guard
 * pins the two-file pairing: if the compose wiring and logback-spring.xml ever drift
 * apart (profile renamed on one side only), prod logs silently revert to text — a
 * shipping pipeline expecting JSON lines starts failing without a code change.
 *
 * <p>A test reading the compose file is deliberate: the contract this guards lives in
 * the compose file itself, so no amount of @Value plumbing would exercise it. Dev
 * stacks are deliberately NOT covered — their console output stays human-readable.
 */
class ObservabilityProdComposeGuardTest {

    @Test
    @DisplayName("prod backend boots both the prod profile and json-logs")
    void prodBackendRunsJsonLogs() throws Exception {
        String compose = Files.readString(Path.of("..", "docker-compose.prod.yml"));

        // The backend service is the only process that emits logback lines; its
        // profile list must name both. Exact pairing, not substring: adding
        // json-logs to a stack that is not prod-pinned would be a different (and
        // still wrong) deployment.
        assertThat(compose)
                .containsPattern("(?s)backend:.{0,900}SPRING_PROFILES_ACTIVE: prod,json-logs");

        // And nowhere else in prod do we enable JSON: there is exactly one
        // SPRING_PROFILES_ACTIVE line in the file and it is the backend's.
        assertThat(compose.lines().filter(line -> line.contains("SPRING_PROFILES_ACTIVE")))
                .hasSize(1);
    }

    @Test
    @DisplayName("the prod profile actually exposes the Prometheus scrape")
    void prodProfileExposesPrometheus() throws Exception {
        // Profile-specific application-prod.yml REPLACES the base yml's exposure
        // list rather than extending it, so prometheus must be named here — the
        // endpoint otherwise vanishes precisely where operators need it. Pinned
        // because this contract lives in a yml line, which nothing else in the
        // suite exercises outside a real prod boot.
        String prod = Files.readString(
                Path.of("..", "backend", "src", "main", "resources", "application-prod.yml"));
        assertThat(prod).containsPattern(
                "(?s)management:.{0,800}include: health,info,prometheus");
    }

    @Test
    @DisplayName("prod operator settings reach their consumers without exposing the Redis password")
    void prodOperatorSettingsAreWired() throws Exception {
        String compose = Files.readString(Path.of("..", "docker-compose.prod.yml"));
        String prod = Files.readString(
                Path.of("..", "backend", "src", "main", "resources", "application-prod.yml"));
        String redisEntrypoint = Files.readString(
                Path.of("..", "cache", "entrypoint.sh"));

        assertThat(compose)
                .contains("ACME_EMAIL: ${ACME_EMAIL:?ACME_EMAIL must be set}")
                .contains("DB_POOL_SIZE: ${DB_POOL_SIZE:-10}")
                .contains("REDISCLI_AUTH=")
                .doesNotContain("redis-cli -a ")
                .doesNotContain("cors-origins");
        assertThat(prod).contains("maximum-pool-size: ${DB_POOL_SIZE:10}");
        assertThat(redisEntrypoint)
                .contains("redis-runtime.conf")
                .doesNotContain("--requirepass");
    }
}
