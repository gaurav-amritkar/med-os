package com.medos.security;

import com.medos.entity.Tenant;
import com.medos.repository.TenantRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A token issued before a tenant was suspended must stop working.
 *
 * <p>The token lives 10 hours, so enforcing tenant state only at login leaves a
 * long window in which a deboarded facility's staff keep a working session.
 * Since the token already carries {@code tenantId}, the filter can refuse a
 * suspended tenant's tokens on every request.
 *
 * <p>Tokens with no {@code tenantId} claim are bootstrap/superadmin sessions and
 * must keep working, or a fresh install cannot be administered.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTenantStateTest {

    @Mock private JwtTokenProvider tokenProvider;
    @Mock private TenantRepository tenantRepository;
    @Mock private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() throws Exception {
        filter = new JwtAuthenticationFilter(tokenProvider, tenantRepository);
        when(tokenProvider.validateToken(any())).thenReturn(JwtTokenProvider.TokenValidationResult.VALID);
        capturedAuth = null;
        // The chain is stubbed rather than verified so the security context can
        // be observed at the moment a request would be served.
        doAnswer(inv -> {
            capturedAuth = SecurityContextHolder.getContext().getAuthentication();
            return null;
        }).when(filterChain).doFilter(any(), any());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void tokenFor(UUID tenantId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", UUID.randomUUID().toString());
        claims.put("role", "doctor");
        claims.put("uname", "staff");
        if (tenantId != null) {
            claims.put("tenantId", tenantId.toString());
        }
        Claims parsed = org.mockito.Mockito.mock(Claims.class);
        when(parsed.get("uid")).thenReturn(claims.get("uid"));
        when(parsed.getSubject()).thenReturn((String) claims.get("uid"));
        when(parsed.get("role")).thenReturn("doctor");
        when(parsed.get("tenantId")).thenReturn(tenantId == null ? null : tenantId.toString());
        when(tokenProvider.parseToken(any())).thenReturn(parsed);
        request.addHeader("Authorization", "Bearer token-value");
    }

    private void tenantRow(UUID id, Boolean active) {
        when(tenantRepository.findById(id)).thenReturn(Optional.of(
                Tenant.builder().id(id).name("Clinic").slug("clinic").active(active).build()));
    }

    /**
     * Authentication as the downstream code would see it.
     *
     * <p>Captured at chain-invocation time, not after: the filter clears the
     * security context in its own {@code finally} block, by design, so reading
     * it once {@code doFilter} has returned always yields null and would say
     * nothing about whether authentication happened.
     */
    private Authentication capturedAuth;

    private Authentication authenticated() {
        return capturedAuth;
    }

    @Test
    void authenticatesWhenTheTokensTenantIsActive() throws Exception {
        UUID tenantId = UUID.randomUUID();
        tokenFor(tenantId);
        tenantRow(tenantId, true);

        filter.doFilter(request, response, filterChain);

        assertNotNull(authenticated());
        assertEquals("ROLE_DOCTOR", authenticated().getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void refusesAuthenticationWhenTheTokensTenantIsDeactivated() throws Exception {
        UUID tenantId = UUID.randomUUID();
        tokenFor(tenantId);
        tenantRow(tenantId, false);

        filter.doFilter(request, response, filterChain);

        // No authentication is established. The request is still passed down the
        // chain so Spring Security's exception translation produces the 401 —
        // swallowing the chain here would return an empty 200 instead, which
        // reads to a client as success.
        assertNull(authenticated(), "a suspended tenant's token must not authenticate");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void treatsANullActiveFlagAsDeactivated() throws Exception {
        UUID tenantId = UUID.randomUUID();
        tokenFor(tenantId);
        tenantRow(tenantId, null);

        filter.doFilter(request, response, filterChain);

        assertNull(authenticated());
    }

    @Test
    void refusesWhenTheTokensTenantNoLongerExists() throws Exception {
        // A deleted tenant row must not leave its staff permanently authenticated.
        UUID tenantId = UUID.randomUUID();
        tokenFor(tenantId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        assertNull(authenticated());
    }

    @Test
    void aTokenWithNoTenantClaimIsStillAccepted() throws Exception {
        tokenFor(null);

        filter.doFilter(request, response, filterChain);

        assertNotNull(authenticated(), "bootstrap/superadmin tokens carry no tenant");
    }

    @Test
    void doesNotLookUpATenantForATokenWithoutOne() throws Exception {
        tokenFor(null);

        filter.doFilter(request, response, filterChain);

        verify(tenantRepository, never()).findById(any());
    }
}
