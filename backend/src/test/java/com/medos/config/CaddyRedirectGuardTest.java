package com.medos.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Caddyfile is the edge configuration, and a mistake in it takes the whole
 * site down while every application test stays green. That is exactly what
 * happened: an unconditional {@code redir https://{host}{uri}} inside the HTTPS
 * site block made Caddy answer its own URL with a redirect to itself, so the
 * site — API and health check included — was unreachable (#228).
 *
 * <p>These assertions pin the property rather than the wording: there must be
 * no redirect inside the hostname block, and the HTTP-to-HTTPS hop must be
 * confined to a listener that is served on plain HTTP.
 */
class CaddyRedirectGuardTest {

    private String caddyfile() throws Exception {
        return Files.readString(Path.of("..", "caddy", "Caddyfile"));
    }

    @Test
    @DisplayName("the HTTPS site block contains no redirect to itself")
    void httpsSiteBlockDoesNotRedirect() throws Exception {
        String caddyfile = caddyfile();

        // Extract the hostname site block: from the {$MEDOS_DOMAIN} opener to
        // the next top-level closing brace at column 0.
        int start = caddyfile.indexOf("{$MEDOS_DOMAIN} {");
        assertThat(start).as("the hostname site block must exist").isGreaterThanOrEqualTo(0);

        int end = caddyfile.indexOf("\n}\n", start);
        assertThat(end).as("the hostname site block must be closed").isGreaterThan(start);

        String httpsBlock = caddyfile.substring(start, end);

        assertThat(httpsBlock)
                .as("a redirect inside the HTTPS block points at the site's own URL and loops")
                .doesNotContainPattern("(?m)^\\s*redir\\s");
        assertThat(httpsBlock)
                .doesNotContainPattern("redir\\s+https://\\{host\\}");
    }

    @Test
    @DisplayName("the plain-HTTP listener is the only place that redirects")
    void redirectIsConfinedToHttpListener() throws Exception {
        String caddyfile = caddyfile();

        long redirects = caddyfile.lines()
                .filter(line -> line.trim().startsWith("redir "))
                .count();
        assertThat(redirects)
                .as("exactly one redirect belongs in the file, on the HTTP listener")
                .isEqualTo(1);

        // And it must be inside a block that listens on plain HTTP — a Caddy
        // site address of ":80" is served over HTTP only.
        assertThat(caddyfile)
                .containsPattern("(?s):80\\s*\\{.{0,400}?redir\\s+https://\\{host\\}\\{uri\\}");
    }

    @Test
    @DisplayName("HSTS is still applied, and not only on the HTTPS responses")
    void hstsIsApplied() throws Exception {
        String caddyfile = caddyfile();

        assertThat(caddyfile)
                .contains("Strict-Transport-Security \"max-age=31536000; includeSubDomains\"");

        // The redirect response must also carry HSTS, or a first visit over HTTP
        // is not pinned and is exposed to a downgrade before the first HTTPS
        // response arrives.
        assertThat(caddyfile)
                .containsPattern("(?s):80\\s*\\{.{0,600}?Strict-Transport-Security");
    }

    @Test
    @DisplayName("the catch-all still rejects unknown Host headers")
    void catchAllRejectsUnknownHosts() throws Exception {
        String caddyfile = caddyfile();

        // A catch-all that aborts is what stops the site answering on whatever
        // name it was reached by.
        assertThat(caddyfile)
                .containsPattern("(?m)^:8080\\s*\\{")
                .containsPattern("(?s):8080\\s*\\{\\s*\\n\\s*abort\\s*\\n\\}");
    }
}
