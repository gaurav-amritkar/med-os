package com.medos.config;

import com.medos.service.IdempotencyService;
import com.medos.util.RequestFingerprint;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Filter to handle idempotency keys for mutating endpoints.
 *
 * <p>Order in the security chain matters and is load-bearing. This filter is
 * registered <b>after</b> {@link com.medos.security.JwtAuthenticationFilter} and
 * after authorization, so a replay is only ever served to a caller who has
 * already been authenticated and authorized for the endpoint. It used to run
 * before the JWT filter and read the tenant/principal from an absent context,
 * which meant a caller who guessed a key could receive a stored financial
 * response without a token at all (#221).
 *
 * <p>Header: {@code Idempotency-Key} (required for configured endpoints).
 * <br>Response header: {@code Idempotency-Key-Replayed: true} when returning a
 * cached response.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    private final IdempotencyService idempotencyService;
    private final RequestFingerprint requestFingerprint;

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

        // Fail closed. This filter runs after the JWT filter, so by the time we
        // get here an authenticated caller has a principal. Anything else — no
        // token, expired token, refused tenant/membership, or an anonymous
        // principal — must never reach the cache. Returning the cached body of a
        // request this caller was not authorized to make is exactly the leak
        // this filter used to permit.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID principal = actingPrincipal(auth);
        if (principal == null) {
            log.warn("Refusing idempotency handling on {}: no authenticated principal", path);
            rejectUnauthorized(response);
            return;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);

        // If no idempotency key provided, reject for configured endpoints
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("Missing Idempotency-Key header for {}", path);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                {"status":400,"error":"Bad Request","code":"IDEMPOTENCY_KEY_REQUIRED",\
                "message":"Missing required header: Idempotency-Key","path":"%s"}
                """.formatted(path));
            return;
        }

        // The key is bound to the acting tenant, the acting user, the endpoint
        // and a fingerprint of the request body. A retry that changes the payload
        // is not a retry: replaying the original response would tell the client a
        // different operation succeeded.
        UUID tenantId = com.medos.security.TenantContext.getTenantId().orElse(null);
        String endpointId = getEndpointId(path);
        String fingerprint = requestFingerprint.fingerprint(request);

        IdempotencyService.Claim claim = idempotencyService.claim(
                endpointId, idempotencyKey, tenantId, principal, fingerprint);

        if (claim instanceof IdempotencyService.Claim.Replay replay) {
            log.info("Replaying cached response for idempotency key on {}", path);
            // The original status, not a blanket 200: a 201 replayed as 200 tells
            // the client the resource already existed rather than being created.
            response.setStatus(replay.response().status());
            if (!replay.response().body().isEmpty()) {
                response.setContentType(replay.response().contentType());
                response.setHeader(REPLAYED_HEADER, "true");
                // The body was captured as the original bytes and is replayed
                // verbatim. Re-serializing it through an ObjectMapper would
                // double-encode it and change the response shape.
                response.getWriter().write(replay.response().body());
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
            response.getWriter().write("""
                {"status":409,"error":"Conflict","code":"IDEMPOTENCY_KEY_IN_FLIGHT",\
                "message":"A request with this Idempotency-Key is still in progress. Retry shortly.",\
                "path":"%s"}
                """.formatted(path));
            return;
        }

        if (claim instanceof IdempotencyService.Claim.FingerprintMismatch mismatch) {
            // Same key, same principal, different payload. This is a client bug
            // or an attempt to reuse a key; neither may replay the stored
            // response, and neither may silently execute the second operation.
            log.warn("Idempotency-Key reused with a different payload on {}", path);
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                {"status":409,"error":"Conflict","code":"IDEMPOTENCY_KEY_REUSED",\
                "message":"This Idempotency-Key was already used with a different request body.",\
                "path":"%s"}
                """.formatted(path));
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
                idempotencyService.complete(endpointId, idempotencyKey, tenantId, principal, fingerprint,
                        new IdempotencyService.CachedResponse(
                                status,
                                wrappedResponse.getContentType(),
                                body == null || body.isBlank() ? EMPTY_BODY : body,
                                fingerprint));
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
     * <p>Returns {@code null} rather than an "anonymous" marker when there is no
     * authenticated principal. The previous behaviour recorded {@code null} as
     * {@code platform:anonymous}, which put unauthenticated callers into a shared
     * cache scope; the key builder keeps that fallback for platform-level sessions
     * that genuinely have no tenant, but this filter refuses to reach the cache
     * at all without a principal.
     */
    private UUID actingPrincipal(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void rejectUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
            {"status":401,"error":"Unauthorized","code":"UNAUTHENTICATED",\
            "message":"Authentication required"}
            """);
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
}
