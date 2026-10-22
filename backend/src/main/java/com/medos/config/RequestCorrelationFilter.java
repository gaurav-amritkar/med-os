package com.medos.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlation IDs (Wave 0 observability gate): every request is traceable
 * across log lines and back to its client or proxy.
 *
 * <p>An inbound {@code X-Request-Id} from the edge is honoured when safe
 * ([A-Za-z0-9._-]{1,64}) and replaced otherwise — a caller cannot forge policy
 * into log lines via the header. The surviving id is bound to the MDC so the
 * logback pattern prints it on every line written while the request is in
 * flight, echoed on the response, and removed in a {@code finally} so it never
 * leaks onto the next request served by the same worker thread.
 */
@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String inbound = request.getHeader(HEADER);
        String requestId = (inbound != null && SAFE_REQUEST_ID.matcher(inbound).matches())
                ? inbound
                : UUID.randomUUID().toString();
        try {
            MDC.put(MDC_KEY, requestId);
            response.setHeader(HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            // The request id belongs to one request, not to this worker thread.
            MDC.remove(MDC_KEY);
        }
    }
}
