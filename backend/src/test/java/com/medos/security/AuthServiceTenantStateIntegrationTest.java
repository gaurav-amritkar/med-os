package com.medos.security;

import com.medos.dto.LoginRequest;
import com.medos.dto.LoginResponse;
import com.medos.entity.Tenant;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.exception.BusinessException;
import com.medos.repository.TenantRepository;
import com.medos.repository.TenantUserRepository;
import com.medos.repository.UserRepository;
import com.medos.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Login tenant-state enforcement against the real database.
 *
 * <p>{@code TenantUser.tenant} is a {@code LAZY} {@code @ManyToOne} and
 * {@code AuthService.login} is not transactional. A mocked unit test navigates
 * {@code membership.getTenant().getActive()} happily and passes, while the real
 * application throws {@code LazyInitializationException} and returns 500 for
 * <em>every</em> login. The mocked test in {@code AuthServiceTenantStateTest}
 * did exactly that: green in isolation, broken in the app.
 *
 * <p>So the enforcement is exercised here through real Hibernate proxies, with
 * real password hashing, and asserted by what a caller actually observes — a
 * token, a 403, or the tenant the session was scoped to.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.cache.type=simple",
        "spring.data.redis.repositories.enabled=false",
})
class AuthServiceTenantStateIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantUserRepository tenantUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "Str0ng-Passw0rd!";

    @BeforeEach
    void clean() {
        tenantUserRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User staffWithMemberships(String username, Tenant... tenants) {
        User user = userRepository.saveAndFlush(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Staff " + username)
                .active(true)
                .build());
        TenantUser.UserRole[] roles = {TenantUser.UserRole.doctor, TenantUser.UserRole.admin};
        for (int i = 0; i < tenants.length; i++) {
            tenantUserRepository.saveAndFlush(TenantUser.builder()
                    .user(user)
                    .tenant(tenants[i])
                    .role(roles[Math.min(i, roles.length - 1)])
                    .build());
        }
        return user;
    }

    private Tenant tenant(String slug, Boolean active) {
        return tenantRepository.saveAndFlush(Tenant.builder()
                .name("Tenant " + slug)
                .slug(slug)
                .active(active)
                .build());
    }

    private LoginRequest credentials(String username) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(PASSWORD);
        return request;
    }

    @Test
    void anActiveTenantSignsInSuccessfully() {
        // The direct regression guard. Reading the tenant's active flag must not
        // throw a lazy-initialisation error, or every login in the running
        // application returns 500 regardless of tenancy.
        staffWithMemberships("active.user", tenant("active-tenant", true));

        LoginResponse response = authService.login(credentials("active.user"));

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getTenantId()).isNotNull();
    }

    @Test
    void aDeactivatedTenantIsRefusedWithForbidden() {
        staffWithMemberships("blocked.user", tenant("suspended-tenant", false));

        assertThatThrownBy(() -> authService.login(credentials("blocked.user")))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getStatus().value())
                        .isEqualTo(403));
    }

    @Test
    void aUserWithOneSuspendedAndOneActiveTenantSignsIntoTheActiveOne() {
        Tenant suspended = tenant("suspended-first", false);
        Tenant active = tenant("active-second", true);
        staffWithMemberships("split.user", suspended, active);

        LoginResponse response = authService.login(credentials("split.user"));

        // Scoped to the usable tenant, not to whichever membership came first.
        assertThat(response.getTenantId()).isEqualTo(active.getId());
    }

    @Test
    void aUserWithNoTenantSignsInAsABootstrapSession() {
        staffWithMemberships("bootstrap.user");

        LoginResponse response = authService.login(credentials("bootstrap.user"));

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getTenantId()).isNull();
    }
}
