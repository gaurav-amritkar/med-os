package com.medos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.medos.entity.Tenant;
import com.medos.entity.TenantUser;
import com.medos.repository.TenantRepository;
import com.medos.repository.TenantUserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Removing someone from a hospital must take effect while they are signed in.
 *
 * <p>{@code JwtAuthenticationFilter} re-checked <em>tenant</em> state on every
 * request — the #53 fix — but not <em>membership</em> state. A token lives ten
 * hours, so deactivating a member on the staff page left them reading and
 * writing patient records until it expired.
 *
 * <p>It is the same defect one level up from the one that was fixed, and it
 * gets the same treatment.
 */
class JwtAuthenticationFilterMembershipRevocationTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID USER = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

    private JwtTokenProvider tokenProvider;
    private TenantRepository tenantRepository;
    private TenantUserRepository tenantUserRepository;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenProvider = mock(JwtTokenProvider.class);
        tenantRepository = mock(TenantRepository.class);
        tenantUserRepository = mock(TenantUserRepository.class);
        filter = new JwtAuthenticationFilter(tokenProvider, tenantRepository, tenantUserRepository);
        SecurityContextHolder.clearContext();
    }

    private Tenant activeTenant() {
        Tenant t = new Tenant();
        t.setId(TENANT);
        t.setName("Clinic A");
        t.setActive(true);
        return t;
    }

    private TenantUser membership(boolean active) {
        TenantUser m = new TenantUser();
        m.setActive(active);
        m.setTenant(activeTenant());
        return m;
    }

    private Claims claimsWithTenant() {
        Claims claims = mock(Claims.class);
        when(claims.get("uid")).thenReturn(USER.toString());
        when(claims.get("role")).thenReturn("doctor");
        when(claims.getSubject()).thenReturn(USER.toString());
        when(claims.get("tenantId")).thenReturn(TENANT.toString());
        return claims;
    }

    private MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients");
        request.addHeader("Authorization", "Bearer valid-token");
        return request;
    }

    /**
     * Captures the authentication the filter installed.
     *
     * <p>Read inside the chain, not after: the filter clears the SecurityContext
     * in its {@code finally} block, so anything observed afterwards is always
     * null regardless of the filter's decision.
     */
    private final java.util.concurrent.atomic.AtomicReference<java.util.UUID> boundTenant =
            new java.util.concurrent.atomic.AtomicReference<>();

    private Authentication authenticationAfter(MockHttpServletRequest request) throws Exception {
        var captured = new Authentication[1];
        boundTenant.set(null);
        filter.doFilter(request, new MockHttpServletResponse(),
                (FilterChain) (req, res) -> {
                    captured[0] = SecurityContextHolder.getContext().getAuthentication();
                    TenantContext.getTenantId().ifPresent(boundTenant::set);
                });
        return captured[0];
    }

    private void givenValidToken() {
        // Build the claims before starting another stubbing block — nesting
        // when() inside when() leaves Mockito with an unfinished stubbing.
        Claims claims = claimsWithTenant();
        when(tokenProvider.validateToken("valid-token"))
                .thenReturn(JwtTokenProvider.TokenValidationResult.VALID);
        when(tokenProvider.parseToken("valid-token")).thenReturn(claims);
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(activeTenant()));
    }

    @Test
    @DisplayName("an active membership is admitted and the tenant is bound")
    void activeMembershipIsAdmitted() throws Exception {
        givenValidToken();
        when(tenantUserRepository.findByUserIdAndTenantId(any(), any()))
                .thenReturn(Optional.of(membership(true)));

        var auth = authenticationAfter(requestWithToken());

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_DOCTOR");
        assertThat(boundTenant.get()).as("the tenant must be bound for the request").isEqualTo(TENANT);
    }

    @Test
    @DisplayName("a membership deactivated mid-session loses access immediately")
    void deactivatedMembershipIsRefusedMidSession() throws Exception {
        givenValidToken();
        // The staff page flipped this while the session was live.
        when(tenantUserRepository.findByUserIdAndTenantId(any(), any()))
                .thenReturn(Optional.of(membership(false)));

        var auth = authenticationAfter(requestWithToken());

        assertThat(auth)
                .as("a person removed from the roster must not keep PHI access for 10 hours")
                .isNull();
    }

    @Test
    @DisplayName("a deleted membership is refused rather than trusted")
    void missingMembershipIsRefused() throws Exception {
        givenValidToken();
        when(tenantUserRepository.findByUserIdAndTenantId(any(), any())).thenReturn(Optional.empty());

        assertThat(authenticationAfter(requestWithToken()))
                .as("a membership row that no longer exists must not fall open")
                .isNull();
    }

    @Test
    @DisplayName("a token with no tenant claim is not membership-checked")
    void platformSessionIsUnaffected() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get("uid")).thenReturn(USER.toString());
        when(claims.get("role")).thenReturn("super_admin");
        when(claims.getSubject()).thenReturn(USER.toString());
        when(claims.get("tenantId")).thenReturn(null);

        when(tokenProvider.validateToken("valid-token"))
                .thenReturn(JwtTokenProvider.TokenValidationResult.VALID);
        when(tokenProvider.parseToken("valid-token")).thenReturn(claims);
        // A super-admin holds no membership on purpose, so no lookup must be
        // attempted and a fresh install stays administrable.
        when(tenantUserRepository.findByUserIdAndTenantId(any(), any()))
                .thenReturn(Optional.empty());

        var auth = authenticationAfter(requestWithToken());

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_SUPER_ADMIN");
    }
}
