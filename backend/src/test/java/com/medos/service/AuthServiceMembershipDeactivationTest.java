package com.medos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
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
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Signing in has to respect a per-hospital deactivation.
 *
 * <p>{@code tenant_users.active} ends one person's access to one hospital. If login kept
 * picking that membership, "deactivate" would be a cosmetic flag on a staff page — the
 * person walks out of the door and straight back in, which is the exact failure the tenant
 * deactivation check above it already had to fix once.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceMembershipDeactivationTest {

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

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
        // expirationMs arrives through @Value, not the constructor.
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "expirationMs", 3_600_000L);
    }

    @AfterEach
    void tearDown() {
        TenantKeyHolder.reset();
    }

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .username("dr.sharma")
                .fullName("Anita Sharma")
                .passwordHash("HASH")
                .active(true)
                .build();
    }

    private Tenant tenant(UUID id, String name) {
        Tenant t = new Tenant();
        t.setId(id);
        t.setName(name);
        t.setActive(true);
        return t;
    }

    private TenantUser membership(UUID tenantId, String name, TenantUser.UserRole role,
                                  boolean active) {
        TenantUser m = new TenantUser();
        m.setUser(user());
        m.setTenant(tenant(tenantId, name));
        m.setRole(role);
        m.setActive(active);
        return m;
    }

    private LoginRequest loginAs(String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername("dr.sharma");
        request.setPassword(password);
        return request;
    }

    private void givenPasswordMatches() {
        when(userRepository.findByUsername("dr.sharma")).thenReturn(java.util.Optional.of(user()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(tokenProvider.generateToken(any(), anyString(), anyString(), any()))
                .thenReturn("token");
    }

    @Test
    @DisplayName("a member deactivated at this hospital cannot sign in")
    void deactivatedMembershipCannotSignIn() {
        givenPasswordMatches();
        when(tenantUserRepository.findByUserIdWithTenant(any()))
                .thenReturn(List.of(membership(TENANT_A, "Clinic A",
                        TenantUser.UserRole.doctor, false)));

        assertThatThrownBy(() -> authService.login(loginAs("Temp@12345")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("deactivation at one hospital leaves their other clinic working")
    void otherClinicStillWorks() {
        givenPasswordMatches();
        when(tenantUserRepository.findByUserIdWithTenant(any()))
                .thenReturn(List.of(
                        membership(TENANT_A, "Clinic A", TenantUser.UserRole.doctor, false),
                        membership(TENANT_B, "Clinic B", TenantUser.UserRole.nurse, true)));

        var response = authService.login(loginAs("Temp@12345"));

        assertThat(response.getTenantId()).isEqualTo(TENANT_B);
    }

    @Test
    @DisplayName("sign-in reports that the password must be changed")
    void mustChangePasswordIsReported() {
        givenPasswordMatches();
        User mustChange = user();
        mustChange.setMustChangePassword(true);
        when(userRepository.findByUsername("dr.sharma")).thenReturn(java.util.Optional.of(mustChange));
        when(tenantUserRepository.findByUserIdWithTenant(any()))
                .thenReturn(List.of(membership(TENANT_A, "Clinic A",
                        TenantUser.UserRole.doctor, true)));

        var response = authService.login(loginAs("Temp@12345"));

        assertThat(response.getMustChangePassword())
                .as("the client needs to know to send this user to change it")
                .isTrue();
    }
}
