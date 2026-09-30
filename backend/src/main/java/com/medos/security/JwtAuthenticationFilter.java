package com.medos.security;

import com.medos.repository.TenantRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;
    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractToken(request);
        try {
            if (StringUtils.hasText(token)) {
                if (tokenProvider.validateToken(token) == JwtTokenProvider.TokenValidationResult.VALID) {
                    Claims claims = tokenProvider.parseToken(token);
                    String uid = claims.get("uid") != null ? claims.get("uid").toString() : claims.getSubject();
                    String role = claims.get("role").toString();

                    // A token lives 10 hours, so enforcing tenant state only at
                    // login would leave a deboarded facility working for the
                    // rest of that window. The claim carries the tenant, so the
                    // state is checked per request — one primary-key lookup.
                    //
                    // No tenantId claim means a bootstrap/superadmin session.
                    // Those have no tenant to be deactivated and must keep
                    // working, or a fresh install cannot be administered.
                    boolean admitted = true;
                    if (claims.get("tenantId") != null) {
                        UUID tenantId = UUID.fromString(claims.get("tenantId").toString());
                        if (isTenantActive(tenantId)) {
                            TenantContext.setTenantId(tenantId);
                        } else {
                            admitted = false;
                            log.warn("Rejected token for deactivated tenant {} on {} {}",
                                    tenantId, request.getMethod(), request.getRequestURI());
                        }
                    }

                    if (admitted) {
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                uid, null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        );
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } else {
                    log.warn("Rejected invalid/expired JWT on {} {}", request.getMethod(), request.getRequestURI());
                }
            }
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            // Never leak token internals — log a sanitized message only.
            log.warn("JWT parsing failed on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName());
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Whether a tenant may still be used.
     *
     * <p>A missing row is treated as not active: a tenant that has been deleted
     * must not leave its former staff permanently authenticated. {@code
     * active} is a nullable {@code BOOLEAN}, and a null counts as inactive —
     * for a value that was never written, the safe reading is the closed one.
     */
    private boolean isTenantActive(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(tenant -> Boolean.TRUE.equals(tenant.getActive()))
                .orElse(false);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}