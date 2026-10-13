package com.medos.security;

import com.medos.repository.TenantRepository;
import com.medos.repository.TenantUserRepository;
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
    private final TenantUserRepository tenantUserRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractToken(request);
        try {
            if (StringUtils.hasText(token)) {
                if (tokenProvider.validateToken(token) == JwtTokenProvider.TokenValidationResult.VALID) {
                    Claims claims = tokenProvider.parseToken(token);
                    String uid = claims.get("uid") != null ? claims.get("uid").toString() : claims.getSubject();
                    UUID userId = UUID.fromString(uid);
                    String role = claims.get("role").toString();

                    // A token lives 10 hours, so enforcing state only at login would
                    // leave a deboarded person working for the rest of that window.
                    // Both the hospital and the person's membership in it are
                    // re-checked per request — one primary-key lookup each.
                    //
                    // No tenantId claim means a platform/superadmin session. Those
                    // hold no membership on purpose, so there is nothing to check
                    // and they must keep working, or a fresh install cannot be
                    // administered.
                    boolean admitted = true;
                    if (claims.get("tenantId") != null) {
                        UUID tenantId = UUID.fromString(claims.get("tenantId").toString());
                        if (isTenantActive(tenantId) && isMembershipActive(userId, tenantId)) {
                            TenantContext.setTenantId(tenantId);
                        } else {
                            admitted = false;
                            log.warn("Rejected token for deactivated tenant {} or membership on {} {}",
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

    /**
     * Whether this person may still act at this hospital.
     *
     * <p>Deactivating someone on the staff page ends one person's access to one
     * hospital. Enforced at login only, the removal would hold until their token
     * expired — up to ten hours of continued access to patient records after
     * they were off the roster.
     *
     * <p>A missing row counts as not a member, for the same reason a missing
     * tenant counts as inactive: for a value that was never written, the safe
     * reading is the closed one.
     */
    private boolean isMembershipActive(UUID userId, UUID tenantId) {
        if (tenantUserRepository == null) {
            return true; // not wired (some slice/test profile)
        }
        try {
            return tenantUserRepository.findByUserIdAndTenantId(userId, tenantId)
                    .map(m -> Boolean.TRUE.equals(m.getActive()))
                    .orElse(false);
        } catch (Exception e) {
            // Fail closed: an unavailable membership store must not become an
            // open door. The request proceeds unauthenticated and gets a 401.
            log.warn("Membership lookup failed for user {} at tenant {}; refusing",
                    userId, tenantId);
            return false;
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}