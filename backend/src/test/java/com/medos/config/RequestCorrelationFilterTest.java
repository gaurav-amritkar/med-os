package com.medos.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correlation IDs (Wave 0, observability gate): every request is traceable
 * end to end — an inbound {@code X-Request-Id} from a trusted proxy is kept,
 * one is minted when missing or unsafe, the id reaches the logs (MDC) and the
 * response, and it never survives the request on the worker thread.
 */
class RequestCorrelationFilterTest {

    private RequestCorrelationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestCorrelationFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private MockHttpServletResponse passThrough(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response,
                (req, res) -> { /* chain reached = request processing happens here */ });
        return response;
    }

    @Test
    @DisplayName("mints a UUID request id when the header is absent")
    void mintsWhenAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = passThrough(request);

        String mdcId = MDC.get(RequestCorrelationFilter.MDC_KEY);
        assertThat(mdcId).isNull(); // cleared inside finally at chain end
        assertThat(response.getHeader(RequestCorrelationFilter.HEADER))
                .satisfies(id -> UUID.fromString(id)); // it is a UUID
    }

    @Test
    @DisplayName("honours a safe inbound X-Request-Id and returns it")
    void honoursInbound() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.HEADER, "req-20261022-0007");
        MockHttpServletResponse response = passThrough(request);

        assertThat(response.getHeader(RequestCorrelationFilter.HEADER)).isEqualTo("req-20261022-0007");
    }

    @Test
    @DisplayName("an unsafe inbound id is replaced — no proxy id forges log lines")
    void replacesUnsafeInbound() throws Exception {
        // Header injection / log-forgery: a caller cannot smuggle their policy
        // into our correlation id.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.HEADER, "evil id\ninjected: whatever-you-want");
        MockHttpServletResponse response = passThrough(request);

        String echoed = response.getHeader(RequestCorrelationFilter.HEADER);
        assertThat(echoed).isNotEqualTo(request.getHeader(RequestCorrelationFilter.HEADER));
        assertThat(echoed).containsPattern("^[A-Za-z0-9._-]{1,64}$");
    }

    @Test
    @DisplayName("the id never leaks onto the next request on the same thread")
    void clearsAfterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        passThrough(request);

        assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("MDC carries the id while the rest of the chain runs")
    void presentDuringChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, (req, res) -> {
            assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNotBlank();
        });
    }
}
