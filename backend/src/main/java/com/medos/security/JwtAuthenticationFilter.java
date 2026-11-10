package com.medos.security;

import com.medos.entity.TenantUser;
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
                    UUID tenantId = null;
                    TenantUser.UserRole currentRole = null;
                    if (claims.get("tenantId") != null) {
                        tenantId = UUID.fromString(claims.get("tenantId").toString());
                        if (isTenantActive(tenantId)) {
                            TenantUser membership = findMembership(userId, tenantId);
                            if (membership != null && membership.isActive()) {
                                TenantContext.setTenantId(tenantId);
                                // The authority comes from the membership as it is
                                // NOW, not from the role stamped into the token.
                                // Trusting the claim let a demoted admin keep ADMIN
                                // until the token expired (#236).
                                currentRole = membership.getRole();
                            } else {
                                admitted = false;
                                log.warn("Rejected token for deactivated membership on {} {}",
                                        request.getMethod(), request.getRequestURI());
                            }
                        } else {
                            admitted = false;
                            log.warn("Rejected token for deactivated tenant {} on {} {}",
                                    tenantId, request.getMethod(), request.getRequestURI());
                        }
                    }

                    if (admitted) {
                        // Platform sessions have no membership and no tenant-bound
                        // role to resolve; the token's role claim is the only
                        // authority there.
                        String role = currentRole != null ? currentRole.name() : claims.get("role").toString();
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
     * The person's current membership at this hospital, or {@code null} if there
     * is none or it has been deactivated.
     *
     * <p>Deactivating someone on the staff page ends one person's access to one
     * hospital. Enforced at login only, the removal would hold until their token
     * expired — up to ten hours of continued access to patient records after
     * they were off the roster.
     *
     * <p>The role is read from this row rather than from the token, so a demotion
     * takes effect on the next request instead of after the token expires.
     *
     * <p>A missing row counts as not a member, for the same reason a missing
     * tenant counts as inactive: for a value that was never written, the safe
     * reading is the closed one.
     */
    private TenantUser findMembership(UUID userId, UUID tenantId) {
        if (tenantUserRepository == null) {
            return null; // not wired (some slice/test profile)
        }
        try {
            return tenantUserRepository.findByUserIdAndTenantId(userId, tenantId)
                    .filter(m -> Boolean.TRUE.equals(m.getActive()))
                    .orElse(null);
        } catch (Exception e) {
            // Fail closed: an unavailable membership store must not become an
            // open door. The request proceeds unauthenticated and gets a 401.
            log.warn("Membership lookup failed for user {} at tenant {}; refusing",
                    userId, tenantId);
            return null;
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