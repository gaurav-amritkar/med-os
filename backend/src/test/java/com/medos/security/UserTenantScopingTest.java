package com.medos.security;

import com.medos.dto.UserDTO;
import com.medos.entity.Tenant;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.repository.TenantRepository;
import com.medos.repository.TenantUserRepository;
import com.medos.repository.UserRepository;
import com.medos.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tenant scoping for the staff listing.
 *
 * <p>{@code User} is not a {@code TenantOwned} entity — it carries no
 * {@code tenant_id} — so {@code TenantStatementInspector} adds no predicate to
 * a query on it. A bare {@code findByActiveTrue()} therefore returned every
 * tenant's users: an admin of one clinic could read another clinic's staff
 * usernames and emails. Membership lives in {@code tenant_users}, and that
 * table is not inspector-scoped either, so the scoping has to be explicit here.
 *
 * <p>Runs against the real database with the full context so the repositories,
 * the tenant context and the service wiring are all exercised together.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.cache.type=simple",
        "spring.data.redis.repositories.enabled=false",
})
class UserTenantScopingTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantUserRepository tenantUserRepository;
    @Autowired private TenantRepository tenantRepository;

    private Tenant tenantA;
    private Tenant tenantB;
    private User adminA;
    private User doctorA;
    private User adminB;

    @BeforeEach
    void seed() {
        TenantContext.clear();
        // Membership rows are what the listing is built from, so start clean.
        tenantUserRepository.deleteAll();
        userRepository.deleteAll();

        tenantA = tenantRepository.save(tenant("Clinic A"));
        tenantB = tenantRepository.save(tenant("Clinic B"));

        adminA = saveUser("a.admin", "Admin A", "a.admin@clinic-a.test");
        doctorA = saveUser("a.doctor", "Doctor A", "a.doctor@clinic-a.test");
        adminB = saveUser("b.admin", "Admin B", "b.admin@clinic-b.test");

        addMembership(adminA, tenantA, TenantUser.UserRole.admin);
        addMembership(doctorA, tenantA, TenantUser.UserRole.doctor);
        addMembership(adminB, tenantB, TenantUser.UserRole.admin);
    }

    @Test
    void listsOnlyUsersOfTheCallersTenant() {
        TenantContext.setTenantId(tenantA.getId());

        List<UserDTO> users = userService.listTenantUsers();

        assertThat(users).extracting(UserDTO::getUsername)
                .containsExactlyInAnyOrder("a.admin", "a.doctor");
    }

    @Test
    void doesNotLeakAnotherTenantsEmail() {
        TenantContext.setTenantId(tenantA.getId());

        List<UserDTO> users = userService.listTenantUsers();

        assertThat(users).extracting(UserDTO::getEmail)
                .doesNotContain("b.admin@clinic-b.test");
    }

    @Test
    void carriesTheRoleHeldInTheCallersTenantNotAnother() {
        TenantContext.setTenantId(tenantA.getId());

        List<UserDTO> users = userService.listTenantUsers();

        UserDTO doctor = users.stream()
                .filter(u -> "a.doctor".equals(u.getUsername()))
                .findFirst()
                .orElseThrow();
        assertThat(doctor.getRole()).isEqualTo(TenantUser.UserRole.doctor.name());
    }

    @Test
    void withNoTenantContext_returnsNothingRatherThanEverything() {
        // The dangerous default is "no tenant means no restriction", which is
        // how the leak happened. A session with no tenant has no tenant roster
        // to see. A future super-admin role must branch on the role explicitly.
        TenantContext.clear();

        assertThat(userService.listTenantUsers()).isEmpty();
    }

    @Test
    void excludesDeactivatedUsers() {
        doctorA.setActive(false);
        userRepository.saveAndFlush(doctorA);
        TenantContext.setTenantId(tenantA.getId());

        List<UserDTO> users = userService.listTenantUsers();

        assertThat(users).extracting(UserDTO::getUsername).containsExactly("a.admin");
    }

    private Tenant tenant(String name) {
        return Tenant.builder().name(name).slug(name.toLowerCase().replace(' ', '-')).build();
    }

    private User saveUser(String username, String fullName, String email) {
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .fullName(fullName)
                .email(email)
                .passwordHash("not-a-real-hash")
                .active(true)
                .build());
    }

    private void addMembership(User user, Tenant tenant, TenantUser.UserRole role) {
        tenantUserRepository.saveAndFlush(TenantUser.builder()
                .user(user)
                .tenant(tenant)
                .role(role)
                .build());
    }
}
