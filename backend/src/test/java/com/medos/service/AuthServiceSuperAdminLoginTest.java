package com.medos.service;

import com.medos.dto.LoginRequest;
import com.medos.dto.LoginResponse;
import com.medos.entity.User;
import com.medos.repository.TenantUserRepository;
import com.medos.repository.UserRepository;
import com.medos.security.JwtTokenProvider;
import com.medos.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The platform super-admin must come back from login carrying the super_admin role and no
 * tenant. Without that, there is nobody who can provision a new tenant after startup:
 * regular admins are tenant-scoped (their JWT carries one tenantId) and the platform
 * account holds no membership row.
 *
 * <p>Until this existed, tenant provisioning rested on the platform-secret token gate
 * (#51) — the secret is the only privilege boundary, and no account has ever been able to
 * say {@code super_admin}. A membership-less user today gets role {@code admin} with a
 * null tenant, which is neither one nor the other.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceSuperAdminLoginTest {

    @Mock private UserRepository userRepository;
    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private LoginRateLimiter rateLimiter;
    @InjectMocks private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "expirationMs", 3_600_000L);
        lenient().when(rateLimiter.isBlocked(anyString())).thenReturn(false);
    }

    @Test
    void superAdminSignInCarriesPlatformRoleAndNoTenant() {
        User platform = User.builder()
                .id(UUID.randomUUID())
                .username("platform")
                .passwordHash("$2a$10$hashed")
                .fullName("Platform Super Admin")
                .active(true)
                .isSuperAdmin(true)
                .build();
        when(userRepository.findByUsername("platform")).thenReturn(Optional.of(platform));
        when(passwordEncoder.matches("pw", "$2a$10$hashed")).thenReturn(true);
        // Active account with no membership: the bootstrap-supersession path.
        when(tenantUserRepository.findByUserIdWithTenant(platform.getId())).thenReturn(List.of());
        when(tokenProvider.generateToken(any(), anyString(), anyString(), any()))
                .thenReturn("jwt-super");

        LoginResponse response = authService.login(request("platform", "pw"));

        assertEquals("super_admin", response.getRole(),
                "a platform account must sign in with the super_admin role, not the "
                        + "membership-less fallback's admin");
        assertNull(response.getTenantId());
        assertEquals("jwt-super", response.getToken());
    }

    @Test
    void tenantAdminSignInStillUsesTheTenantRole() {
        User hospitalAdmin = User.builder()
                .id(UUID.randomUUID())
                .username("hospital-admin")
                .passwordHash("$2a$10$hashed")
                .fullName("Hospital Admin")
                .active(true)
                .isSuperAdmin(false)
                .build();
        when(userRepository.findByUsername("hospital-admin")).thenReturn(Optional.of(hospitalAdmin));
        when(passwordEncoder.matches("pw", "$2a$10$hashed")).thenReturn(true);
        when(tenantUserRepository.findByUserIdWithTenant(hospitalAdmin.getId())).thenReturn(List.of());
        when(tokenProvider.generateToken(hospitalAdmin.getId(), "hospital-admin", "admin", null))
                .thenReturn("jwt-admin");

        LoginResponse response = authService.login(request("hospital-admin", "pw"));

        assertEquals("admin", response.getRole(),
                "a regular tenant-admin-less account still falls back to admin, so the new "
                        + "branch must not swallow its neighbours");
    }

    private static LoginRequest request(String username, String password) {
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword(password);
        return r;
    }
}
