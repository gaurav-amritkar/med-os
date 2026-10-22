package com.medos.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;

import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level phase of observability (Wave 0 gate): the correlation id reaches
 * clients and proxies, and the Prometheus scrape endpoint exists so monitors
 * inside the private network can collect metrics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// ObservabilityHttpContractTest is the exception to Boot's test default: by
// default (Boot 3.4+) the test autoconfigure flips every metrics export off so
// sliced tests stay cheap. This class asserts the real production observability
// contract — the Prometheus scrape is part of the Wave-0 gate — so metrics
// must be on here, exactly as prod runs them.
@AutoConfigureObservability
class ObservabilityHttpContractTest {

    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("every response carries an X-Request-Id")
    void everyResponseCarriesRequestId() throws Exception {
        // Any endpoint does: the filter sits above security and the dispatcher.
        // Health answers 200 when every indicator is up and 503 when the test
        // profile lacks (e.g.) Redis — either answer must carry the id.
        mockMvc.perform(get("/manage/health"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getStatus()).isIn(200, 503))
                .andExpect(header().string(RequestCorrelationFilter.HEADER,
                        matchesPattern(REQUEST_ID)));
    }

    @Test
    @DisplayName("the inbound X-Request-Id is echoed, even on rejected requests")
    void inboundEchoedOnRejectedRequests() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header(RequestCorrelationFilter.HEADER, "trace-pr-9-0002"))
                .andExpect(status().is4xxClientError())
                .andExpect(header().string(RequestCorrelationFilter.HEADER, "trace-pr-9-0002"));
    }

    @Test
    @DisplayName("the Prometheus endpoint answers unauthenticated (private network only)")
    void prometheusScrapeAvailable() throws Exception {
        mockMvc.perform(get("/manage/prometheus"))
                .andExpect(status().isOk())
                // Micrometer text format: metric families like jvm_memory_used_bytes.
                .andExpect(content().string(containsString("jvm_memory")));
    }
}
