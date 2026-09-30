package com.medos.service;

import com.medos.dto.LoginRequest;
import com.medos.dto.LoginResponse;
import com.medos.entity.Tenant;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.exception.BusinessException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tenant state must gate login, not just the user's own flag.
 *
 * <p>Mocked unit coverage of the decision logic. The same enforcement is also
 * covered against real Hibernate proxies in
 * {@code AuthServiceTenantStateIntegrationTest}, which is the one that catches
 * a LAZY-initialisation failure — stubs cannot reproduce it, and an earlier
 * version of this test passed while every login in the running app returned
 * 500.
 *
 * <p>{@code AuthService.login} checked {@code user.active} and never the
 * tenant's. Deactivating a tenant therefore changed a database flag and nothing
 * else: its staff could still sign in and work. That makes any "deboard this
 * tenant" control report a tenant as disabled while it keeps operating, so the
 * flag has to be enforced where the session is issued.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTenantStateTest {

    @Mock private UserRepository userRepository;
    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private LoginRateLimiter rateLimiter;
    @InjectMocks private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "expirationMs", 3600000L);
        lenient().when(rateLimiter.isBlocked(anyString())).thenReturn(false);
        lenient().when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        lenient().when(tokenProvider.generateToken(any(), anyString(), anyString(), any()))
                .thenReturn("token-value");
    }

    private User userWithMembership(TenantUser... memberships) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("staff")
                .passwordHash("$2a$10$hashed")
                .fullName("Staff Member")
                .active(true)
                .build();
        lenient().when(userRepository.findByUsername("staff")).thenReturn(Optional.of(user));
        lenient().when(userRepository.findByEmail("staff")).thenReturn(Optional.empty());
        lenient().when(tenantUserRepository.findByUserIdWithTenant(user.getId())).thenReturn(List.of(memberships));
        return user;
    }

    private Tenant tenant(Boolean active) {
        return Tenant.builder()
                .id(UUID.randomUUID())
                .name("Clinic")
                .slug("clinic")
                .active(active)
                .build();
    }

    private TenantUser membership(Tenant tenant, TenantUser.UserRole role) {
        TenantUser membership = new TenantUser();
        membership.setUser(User.builder().id(UUID.randomUUID()).username("m").build());
        membership.setTenant(tenant);
        membership.setRole(role);
        return membership;
    }

    @Test
    void refusesLoginWhenTheOnlyTenantIsDeactivated() {
        userWithMembership(membership(tenant(false), TenantUser.UserRole.admin));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(credentials()));

        assertEquals(403, ex.getStatus().value());
        assertNotNull(ex.getMessage());
    }

    @Test
    void allowsLoginWhenTheTenantIsActive() {
        userWithMembership(membership(tenant(true), TenantUser.UserRole.doctor));

        LoginResponse response = authService.login(credentials());

        assertNotNull(response.getToken());
    }

    @Test
    void aTenantWithNullActiveIsTreatedAsInactive() {
        // tenants.active is a nullable BOOLEAN with no default. Reading a null
        // as "not disabled" would mean a row that never had the flag set is
        // wide open, which is the wrong direction for a missing value.
        userWithMembership(membership(tenant(null), TenantUser.UserRole.admin));

        assertThrows(BusinessException.class, () -> authService.login(credentials()));
    }

    @Test
    void aUserInOneSuspendedAndOneActiveTenantCanStillSignIn() {
        Tenant suspended = tenant(false);
        Tenant active = tenant(true);
        userWithMembership(
                membership(suspended, TenantUser.UserRole.admin),
                membership(active, TenantUser.UserRole.doctor));

        LoginResponse response = authService.login(credentials());

        // The session must be scoped to the tenant that is actually usable,
        // not to whichever membership happened to be first.
        assertEquals(active.getId(), response.getTenantId());
        assertEquals(TenantUser.UserRole.doctor.name(), response.getRole());
    }

    @Test
    void aUserWithNoTenantMembershipIsUnaffected() {
        // Bootstrap/superadmin sessions carry no tenant. Enforcing tenant state
        // must not lock them out, or a fresh install cannot be administered.
        userWithMembership();

        LoginResponse response = authService.login(credentials());

        assertEquals(null, response.getTenantId());
    }

    @Test
    void doesNotUpdateLastLoginWhenLoginIsRefused() {
        User user = userWithMembership(membership(tenant(false), TenantUser.UserRole.admin));

        assertThrows(BusinessException.class, () -> authService.login(credentials()));

        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never())
                .save(eq(user));
    }

    private LoginRequest credentials() {
        LoginRequest request = new LoginRequest();
        request.setUsername("staff");
        request.setPassword("correct-horse-battery");
        return request;
    }
}
