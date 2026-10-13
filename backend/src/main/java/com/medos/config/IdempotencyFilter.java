package com.medos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medos.security.TenantContext;
import com.medos.service.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Filter to handle idempotency keys for mutating endpoints.
 * 
 * Checks for Idempotency-Key header on configured endpoints.
 * If key exists, returns cached response (200 OK with cached body).
 * If key doesn't exist, allows request through and caches response.
 * 
 * Header: Idempotency-Key (required for configured endpoints)
 * Response Header: Idempotency-Key-Replayed: true (when returning cached response)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    // Endpoints that require idempotency keys (must match controller request mappings)
    private static final Set<String> IDEMPOTENT_ENDPOINTS = Set.of(
            "/api/v1/billing/payments",
            "/api/v1/billing/invoices",
            "/api/v1/pharmacy/dispense"
    );

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String REPLAYED_HEADER = "Idempotency-Key-Replayed";
    /** Marker stored for 2xx responses with an empty body (void endpoints). */
    private static final String EMPTY_BODY = "";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Only apply to POST requests on configured endpoints
        if (!"POST".equalsIgnoreCase(method) || !isIdempotentEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);

        // If no idempotency key provided, reject for configured endpoints
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("Missing Idempotency-Key header for {}", path);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(),
                new IdempotencyErrorResponse("Missing required header: Idempotency-Key"));
            return;
        }

        // Scoped to the acting tenant and user, so one hospital's key cannot
        // collide with another's — the endpoints below return financial records.
        UUID tenantId = TenantContext.getTenantId().orElse(null);
        UUID principal = principalName();
        String endpointId = getEndpointId(path);

        IdempotencyService.Claim claim = idempotencyService.claim(endpointId, idempotencyKey, tenantId, principal);

        if (claim instanceof IdempotencyService.Claim.Replay replay) {
            log.info("Replaying cached response for idempotency key on {}", path);
            // The original status, not a blanket 200: a 201 replayed as 200 tells
            // the client the resource already existed rather than being created.
            response.setStatus(replay.response().status());
            if (!replay.response().body().isEmpty()) {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader(REPLAYED_HEADER, "true");
                objectMapper.writeValue(response.getWriter(), replay.response().body());
            } else {
                response.setHeader(REPLAYED_HEADER, "true");
            }
            return;
        }

        if (claim instanceof IdempotencyService.Claim.InFlight) {
            // Another request with this key is running. Executing as well is the
            // double-charge this feature exists to prevent, so refuse and let the
            // client retry rather than doing it twice.
            log.info("Concurrent idempotent request in flight on {}", path);
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(),
                new IdempotencyErrorResponse(
                    "A request with this Idempotency-Key is still in progress. Retry shortly."));
            return;
        }

        // Wrap response to capture it for caching
        IdempotencyResponseWrapper wrappedResponse = new IdempotencyResponseWrapper(response);

        try {
            filterChain.doFilter(request, wrappedResponse);

            // Cache successful responses (2xx), including empty bodies so that retrying
            // a void operation (e.g. dispense) replays the success instead of re-executing it
            int status = wrappedResponse.getStatus();
            if (status >= 200 && status < 300) {
                String body = wrappedResponse.getBodyAsString();
                idempotencyService.complete(endpointId, idempotencyKey, tenantId, principal,
                        new IdempotencyService.CachedResponse(
                                status, body == null || body.isBlank() ? EMPTY_BODY : body));
            } else {
                // A failure must not hold the key: the client is entitled to retry
                // the payment it was legitimately attempting.
                idempotencyService.release(endpointId, idempotencyKey, tenantId, principal);
            }
        } finally {
            // Copy wrapped response to actual response
            wrappedResponse.copyTo(response);
        }
    }

    /**
     * The acting user, as the JWT principal.
     *
     * <p>Used as the second scoping component so two users in one hospital cannot
     * read each other's cached payment responses either.
     */
    private UUID principalName() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isIdempotentEndpoint(String path) {
        return IDEMPOTENT_ENDPOINTS.stream().anyMatch(path::equals);
    }

    private String getEndpointId(String path) {
        // Convert path to endpoint identifier (e.g., "/api/v1/billing/payments" -> "billing/payments")
        if (path.startsWith("/api/v1/")) {
            return path.substring("/api/v1/".length());
        }
        if (path.startsWith("/api/")) {
            return path.substring(5); // Remove "/api/"
        }
        return path;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) 
            || !isIdempotentEndpoint(request.getRequestURI());
    }

    // Simple error response record
    private record IdempotencyErrorResponse(String message) {}
}