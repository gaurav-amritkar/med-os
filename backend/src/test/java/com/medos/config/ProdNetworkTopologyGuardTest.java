package com.medos.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The production network topology is a security boundary, and nothing else in the
 * suite exercises it: it lives in the compose file, so a change there is
 * invisible to every test that boots the application.
 *
 * <p>Two properties matter and both were wrong once (#229):
 *
 * <ul>
 *   <li>The backend and the one-shot migrate job must be able to reach the
 *       managed database, so {@code medos-private} must <b>not</b> be
 *       {@code internal: true}. An internal bridge has no default route; those
 *       containers could not resolve or connect to anything outside the host,
 *       which made the production deployment undeployable as written.</li>
 *   <li>The edge network <b>must</b> be internal, and only Caddy may be attached
 *       to it, so the TLS terminator cannot initiate outbound connections and a
 *       compromised edge container cannot be used as a pivot.</li>
 * </ul>
 *
 * <p>Published ports are asserted by the CI compose-model check, not here: that
 * check has the resolved model and can see the effective bindings.
 *
 * <p>The compose file is parsed here rather than matched by regex. A regex
 * against a YAML file passes just as happily for a reordered or commented-out
 * block, which is precisely the drift this guard exists to catch.
 */
class ProdNetworkTopologyGuardTest {

    /** Minimal indentation-aware reader for the subset of YAML Compose uses. */
    private static Map<String, Object> parseCompose(String yaml) {
        Map<String, Object> root = new LinkedHashMap<>();
        // stack of (indent, map)
        List<Map<String, Object>> stack = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();
        stack.add(root);
        indents.add(-1);

        String[] lines = yaml.split("\n");
        for (String raw : lines) {
            String line = raw.replaceAll("\t", "  ");
            if (line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }
            int indent = line.indexOf(line.trim());
            String content = line.trim();

            while (indents.size() > 1 && indent <= indents.get(indents.size() - 1)) {
                indents.remove(indents.size() - 1);
                stack.remove(stack.size() - 1);
            }
            Map<String, Object> current = stack.get(stack.size() - 1);

            int colon = content.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = content.substring(0, colon).trim();
            String rest = content.substring(colon + 1).trim();

            if (rest.isEmpty()) {
                Map<String, Object> child = new LinkedHashMap<>();
                current.put(key, child);
                stack.add(child);
                indents.add(indent);
            } else {
                current.put(key, scalar(rest));
            }
        }
        return root;
    }

    private static Object scalar(String value) {
        if (value.equals("true") || value.equals("false")) {
            return Boolean.parseBoolean(value);
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            String inner = value.substring(1, value.length() - 1).trim();
            if (inner.isEmpty()) {
                return List.of();
            }
            List<Object> items = new ArrayList<>();
            for (String part : inner.split(",")) {
                items.add(scalar(part.trim()));
            }
            return items;
        }
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return value.substring(1, value.length() - 1);
        }
        if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> compose() throws Exception {
        String yaml = Files.readString(Path.of("..", "docker-compose.prod.yml"));
        return parseCompose(yaml);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> services() throws Exception {
        return (Map<String, Object>) compose().get("services");
    }

    @SuppressWarnings("unchecked")
    private List<String> networksOf(String service) throws Exception {
        Object networks = ((Map<String, Object>) services().get(service)).get("networks");
        if (networks == null) {
            return List.of();
        }
        if (networks instanceof List<?> list) {
            List<String> names = new ArrayList<>();
            for (Object o : list) {
                names.add(o.toString());
            }
            return names;
        }
        return List.of(networks.toString());
    }

    @Test
    @DisplayName("medos-private carries the application tier and is not internal")
    @SuppressWarnings("unchecked")
    void appNetworkIsNotInternal() throws Exception {
        // The migration job and the backend are the two services that need the
        // database. Both must sit on the non-internal network.
        assertThat(networksOf("migrate")).contains("medos-private");
        assertThat(networksOf("backend")).contains("medos-private");
        assertThat(networksOf("frontend")).contains("medos-private");

        // The network itself must not be internal: an internal bridge cannot
        // reach the managed pooler or resolve external DNS.
        Map<String, Object> networks = (Map<String, Object>) compose().get("networks");
        Object internal = ((Map<String, Object>) networks.getOrDefault("medos-private", Map.of()))
                .get("internal");
        assertThat(internal)
                .as("medos-private must have a default route to the managed database")
                .isEqualTo(false);
    }

    @Test
    @DisplayName("medos-edge is internal and only Caddy is attached to it")
    @SuppressWarnings("unchecked")
    void edgeNetworkIsInternalAndCaddyOnly() throws Exception {
        Map<String, Object> networks = (Map<String, Object>) compose().get("networks");
        Object internal = ((Map<String, Object>) networks.getOrDefault("medos-edge", Map.of()))
                .get("internal");
        assertThat(internal)
                .as("the edge network must not allow outbound connections")
                .isEqualTo(true);

        List<String> onEdge = new ArrayList<>();
        for (String service : services().keySet()) {
            if (networksOf(service).contains("medos-edge")) {
                onEdge.add(service);
            }
        }
        assertThat(onEdge)
                .as("only the TLS terminator belongs on the edge network")
                .containsExactly("caddy");
    }

    @Test
    @DisplayName("Redis is not reachable from the edge network")
    void redisIsNotOnTheEdge() throws Exception {
        // Redis holds cache and lockout counters, never patient data (ADR-0007),
        // but an unauthenticated Redis is still a write primitive for anything
        // that reaches it. It stays on the application network only.
        assertThat(networksOf("redis")).contains("medos-private").doesNotContain("medos-edge");
    }
}
