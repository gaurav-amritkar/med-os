package com.medos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.medos.dto.LoginRequest;
import com.medos.entity.Tenant;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.exception.BusinessException;
import com.medos.repository.TenantUserRepository;
import com.medos.repository.UserRepository;
import com.medos.security.LoginRateLimiter;
import com.medos.security.JwtTokenProvider;
import com.medos.security.TenantKeyHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * An account with no membership must not be handed a cross-tenant admin token.
 *
 * <p>The chain this closes: login gave a membership-less user {@code role=admin}
 * with a null {@code tenantId}; {@code JwtAuthenticationFilter} only sets
 * {@code TenantContext} when the claim is present; and
 * {@code TenantStatementInspector} leaves SQL untouched when no tenant is
 * bound. Three individually reasonable decisions composed into a token that
 * reads every hospital's patients.
 *
 * <p>That fallback existed for the first-boot admin, which genuinely has no
 * tenant. {@code users.is_super_admin} is the explicit flag that now expresses
 * it, so the implicit path can close without stranding a fresh install.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceMembershiplessLoginTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantUserRepository tenantUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private LoginRateLimiter rateLimiter;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        TenantKeyHolder.reset();
        ReflectionTestUtils.setField(authService, "expirationMs", 3_600_000L);
    }

    @AfterEach
    void tearDown() {
        TenantKeyHolder.reset();
    }

    private User user(boolean superAdmin) {
        return User.builder()
                .id(UUID.randomUUID())
                .username("stray")
                .fullName("Unaffiliated Account")
                .passwordHash("HASH")
                .active(true)
                .isSuperAdmin(superAdmin)
                .build();
    }

    private LoginRequest loginAs(String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername("stray");
        request.setPassword(password);
        return request;
    }

    private void givenPasswordMatchesFor(User user) {
        when(userRepository.findByUsername("stray")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(tokenProvider.generateToken(any(), anyString(), anyString(), any()))
                .thenReturn("token");
    }

    @Test
    @DisplayName("an active account with no hospital membership cannot sign in")
    void membershiplessUserCannotSignIn() {
        givenPasswordMatchesFor(user(false));
        when(tenantUserRepository.findByUserIdWithTenant(any())).thenReturn(List.of());

        assertThatThrownBy(() -> authService.login(loginAs("Temp@12345")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("the refusal happens before a token is ever minted")
    void noTokenIsMintedForAMembershiplessUser() {
        givenPasswordMatchesFor(user(false));
        when(tenantUserRepository.findByUserIdWithTenant(any())).thenReturn(List.of());

        assertThatThrownBy(() -> authService.login(loginAs("Temp@12345")))
                .isInstanceOf(BusinessException.class);

        org.mockito.Mockito.verify(tokenProvider, org.mockito.Mockito.never())
                .generateToken(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("the platform super-admin still signs in without a tenant")
    void superAdminWithoutMembershipStillSignsIn() {
        givenPasswordMatchesFor(user(true));
        when(tenantUserRepository.findByUserIdWithTenant(any())).thenReturn(List.of());

        var response = authService.login(loginAs("Temp@12345"));

        assertThat(response.getRole())
                .as("a fresh install must stay administrable")
                .isEqualTo("super_admin");
        assertThat(response.getTenantId()).isNull();
    }

    @Test
    @DisplayName("a member of an active hospital is unaffected")
    void ordinaryMemberStillSignsIn() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Clinic A");
        tenant.setActive(true);

        TenantUser membership = new TenantUser();
        membership.setUser(user(false));
        membership.setTenant(tenant);
        membership.setRole(TenantUser.UserRole.doctor);
        membership.setActive(true);

        givenPasswordMatchesFor(user(false));
        when(tenantUserRepository.findByUserIdWithTenant(any())).thenReturn(List.of(membership));

        var response = authService.login(loginAs("Temp@12345"));

        assertThat(response.getTenantId()).isEqualTo(tenant.getId());
        assertThat(response.getRole()).isEqualTo("doctor");
    }
}
