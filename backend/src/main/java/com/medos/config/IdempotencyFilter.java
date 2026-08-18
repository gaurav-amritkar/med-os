package com.medos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medos.service.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

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

    // Endpoints that require idempotency keys
    private static final Set<String> IDEMPOTENT_ENDPOINTS = Set.of(
            "/api/billing/payments",
            "/api/billing/invoices",
            "/api/pharmacy/dispense"
    );

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String REPLAYED_HEADER = "Idempotency-Key-Replayed";

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

        // Check for existing cached response
        Optional<Object> cached = idempotencyService.getResponse(getEndpointId(path), idempotencyKey);
        if (cached.isPresent()) {
            log.info("Returning cached response for idempotency key on {}", path);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader(REPLAYED_HEADER, "true");
            objectMapper.writeValue(response.getWriter(), cached.get());
            return;
        }

        // Wrap response to capture it for caching
        IdempotencyResponseWrapper wrappedResponse = new IdempotencyResponseWrapper(response);
        
        try {
            filterChain.doFilter(request, wrappedResponse);
            
            // Cache successful responses (2xx)
            if (wrappedResponse.getStatus() >= 200 && wrappedResponse.getStatus() < 300) {
                String body = wrappedResponse.getBodyAsString();
                if (body != null && !body.isBlank()) {
                    Object responseObj = objectMapper.readValue(body, Object.class);
                    idempotencyService.storeResponse(getEndpointId(path), idempotencyKey, responseObj);
                }
            }
        } finally {
            // Copy wrapped response to actual response
            wrappedResponse.copyTo(response);
        }
    }

    private boolean isIdempotentEndpoint(String path) {
        return IDEMPOTENT_ENDPOINTS.stream().anyMatch(path::equals);
    }

    private String getEndpointId(String path) {
        // Convert path to endpoint identifier (e.g., "/api/billing/payments" -> "billing/payments")
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