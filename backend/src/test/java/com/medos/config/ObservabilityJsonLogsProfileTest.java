package com.medos.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.ConsoleAppender;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave-0 observability closure: pins the logback-spring.xml profile switch directly.
 *
 * <p>The naive way to test this — boot a Spring context with the {@code json-logs}
 * profile and parse stdout — is order-dependent: the JVM logging system is a shared
 * singleton, and a context that boots into an already-initialised logback keeps
 * whatever format a previous profile installed (the same class of surefire class-order
 * flakiness that hit the TenantKeyHolder statics; reproduced: standalone green, after
 * {@code ObservabilityHttpContractTest} red). So this test runs Joran against the real
 * XML with a mock environment per branch: no JVM coupling, and both branches pinned —
 * with {@code json-logs} the root appender is a LogstashEncoder whose output parses as
 * JSON with the app framed; without it, text stays text.
 */
class ObservabilityJsonLogsProfileTest {

    private static final File CONFIG = new File("src/main/resources/logback-spring.xml");

    @Test
    @DisplayName("with json-logs, logback-spring.xml yields a JSON appender that parses with the app pinned")
    void jsonProfileProducesParseableJson() throws Exception {
        LoggerContext context = configuredContext("json-logs");

        ConsoleAppender<?> appender = consoleAppenderOf(context, "JSON");
        assertThat(appender).isNotNull();
        var encoder = appender.getEncoder();
        assertThat(encoder).isInstanceOf(net.logstash.logback.encoder.LogstashEncoder.class);

        // Capture one real event through the configured context (a hand-built
        // LoggingEvent has no timestamp instant, which the encoder needs).
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> capture =
                new ch.qos.logback.core.read.ListAppender<>();
        capture.setContext(context);
        capture.start();
        var logger = context.getLogger("medos.probe");
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        logger.addAppender(capture);
        logger.info("json-logs probe message");

        var event = capture.list.get(0);
        byte[] encoded = ((net.logstash.logback.encoder.LogstashEncoder) encoder).encode(event);
        String line = new String(encoded, StandardCharsets.UTF_8).trim();
        assertThat(line).startsWith("{");

        com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(line);
        assertThat(json.get("message").asText()).isEqualTo("json-logs probe message");
        assertThat(json.get("app").asText()).isEqualTo("medos-backend");
        assertThat(json.get("@timestamp")).isNotNull();
    }

    @Test
    @DisplayName("without json-logs, logback-spring.xml stays human-readable text")
    void defaultProfileStaysText() throws Exception {
        LoggerContext context = configuredContext("dev");

        ConsoleAppender<?> appender = consoleAppenderOf(context, "TEXT");
        assertThat(appender).isNotNull();
        assertThat(appender.getEncoder()).isInstanceOf(PatternLayoutEncoder.class);
        // And no JSON appender is installed at all: the two branches are exclusive.
        assertThat(consoleAppenderOf(context, "JSON")).isNull();
    }

    private LoggerContext configuredContext(String... profiles) throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        LoggerContext context = new LoggerContext();
        // A fresh LoggerContext has no MDC adapter; the real one is bound by
        // logback's global binding. The JSON encoder writes the MDC map, so the
        // standalone configuration needs the standard adapter to encode events.
        context.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
        // Boot keeps its Joran configurator package-private; reflect the one public
        // and stable contract — the LoggingInitializationContext constructor — and
        // then configure the XML through logback's own public doConfigure(URL). If
        // Boot ever removes that constructor, this fails loudly here, not silently.
        Class<?> configuratorClass = Class.forName(
                "org.springframework.boot.logging.logback.SpringBootJoranConfigurator");
        java.lang.reflect.Constructor<?> constructor = configuratorClass
                .getDeclaredConstructor(org.springframework.boot.logging.LoggingInitializationContext.class);
        constructor.setAccessible(true);
        Object configurator = constructor
                .newInstance(new org.springframework.boot.logging.LoggingInitializationContext(environment));
        java.lang.reflect.Method setContext = configuratorClass.getMethod("setContext",
                ch.qos.logback.core.Context.class);
        setContext.invoke(configurator, context);
        java.lang.reflect.Method doConfigure = configuratorClass.getMethod("doConfigure",
                java.net.URL.class);
        doConfigure.invoke(configurator, CONFIG.toURI().toURL());
        return context;
    }

    private ConsoleAppender<?> consoleAppenderOf(LoggerContext context, String name) {
        Iterator<ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent>> appenders =
                context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME).iteratorForAppenders();
        while (appenders.hasNext()) {
            var appender = appenders.next();
            if (appender instanceof ConsoleAppender<?> console && appender.getName().equals(name)) {
                return console;
            }
        }
        return null;
    }

    /** Minimal environment holder (kept out of the main flow for clarity). */
    private static final class MockEnvironment extends org.springframework.mock.env.MockEnvironment {
    }
}
