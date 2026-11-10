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
 * A token carries the role its bearer had when they signed in. Trusting that
 * claim means a demotion holds until the token expires — up to ten hours of
 * ADMIN authority for someone who is no longer an admin (#236).
 *
 * <p>The authority must come from the membership as it is now. This asserts the
 * filter resolves it per request rather than replaying the claim.
 */
class JwtAuthenticationFilterRoleRevocationTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID USER = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

    private JwtTokenProvider tokenProvider;
    private TenantRepository tenantRepository;
    private TenantUserRepository tenantUserRepository;
    private JwtAuthenticationFilter filter;

    private final java.util.concurrent.atomic.AtomicReference<Authentication> captured =
            new java.util.concurrent.atomic.AtomicReference<>();

    @BeforeEach
    void setUp() {
        tokenProvider = mock(JwtTokenProvider.class);
        tenantRepository = mock(TenantRepository.class);
        tenantUserRepository = mock(TenantUserRepository.class);
        filter = new JwtAuthenticationFilter(tokenProvider, tenantRepository, tenantUserRepository);
        SecurityContextHolder.clearContext();
        captured.set(null);
    }

    private void givenValidTokenWithRoleClaim(String roleClaim) {
        Claims claims = mock(Claims.class);
        when(claims.get("uid")).thenReturn(USER.toString());
        when(claims.get("role")).thenReturn(roleClaim);
        when(claims.getSubject()).thenReturn(USER.toString());
        when(claims.get("tenantId")).thenReturn(TENANT.toString());

        when(tokenProvider.validateToken("valid-token"))
                .thenReturn(JwtTokenProvider.TokenValidationResult.VALID);
        when(tokenProvider.parseToken("valid-token")).thenReturn(claims);

        Tenant tenant = new Tenant();
        tenant.setId(TENANT);
        tenant.setName("Clinic A");
        tenant.setActive(true);
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
    }

    private TenantUser membership(TenantUser.UserRole role) {
        TenantUser m = new TenantUser();
        m.setRole(role);
        m.setActive(true);
        return m;
    }

    private Authentication run() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "Bearer valid-token");
        filter.doFilter(request, new MockHttpServletResponse(),
                (FilterChain) (req, res) -> captured.set(SecurityContextHolder.getContext().getAuthentication()));
        return captured.get();
    }

    @Test
    @DisplayName("a demoted member loses ADMIN on the next request, not at token expiry")
    void demotionTakesEffectImmediately() throws Exception {
        // The token still says admin: it was issued before the demotion.
        givenValidTokenWithRoleClaim("admin");
        when(tenantUserRepository.findByUserIdAndTenantId(USER, TENANT))
                .thenReturn(Optional.of(membership(TenantUser.UserRole.doctor)));

        Authentication auth = run();

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
                .as("the token claim must not outlive the membership it describes")
                .extracting("authority")
                .containsExactly("ROLE_DOCTOR");
    }

    @Test
    @DisplayName("a promotion takes effect on the next request too")
    void promotionTakesEffectImmediately() throws Exception {
        givenValidTokenWithRoleClaim("doctor");
        when(tenantUserRepository.findByUserIdAndTenantId(USER, TENANT))
                .thenReturn(Optional.of(membership(TenantUser.UserRole.admin)));

        Authentication auth = run();

        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("a platform session keeps the role from its token")
    void platformSessionKeepsTokenRole() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get("uid")).thenReturn(USER.toString());
        when(claims.get("role")).thenReturn("super_admin");
        when(claims.getSubject()).thenReturn(USER.toString());
        when(claims.get("tenantId")).thenReturn(null);

        when(tokenProvider.validateToken("valid-token"))
                .thenReturn(JwtTokenProvider.TokenValidationResult.VALID);
        when(tokenProvider.parseToken("valid-token")).thenReturn(claims);
        // No membership exists for a platform session, and none may be required.
        when(tenantUserRepository.findByUserIdAndTenantId(any(), any())).thenReturn(Optional.empty());

        Authentication auth = run();

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_SUPER_ADMIN");
    }
}
