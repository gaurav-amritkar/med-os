package com.medos.config;

import com.medos.entity.Tenant;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.repository.TenantRepository;
import com.medos.repository.TenantUserRepository;
import com.medos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Bootstraps the first admin account and the hospital it administers.
 *
 * Flyway no longer ships any users (V3 removes the demo accounts), so a fresh
 * production database has no way to log in. Deployments set
 * {@code BOOTSTRAP_ADMIN_PASSWORD} (one-time) and this runner creates the
 * {@code admin} user on first startup only.
 *
 * <p>A tenant is created alongside the user, and the user is made an admin of
 * it. This is not optional: {@code patients.tenant_id} is NOT NULL, and
 * {@code TenantEntityListener} stamps that column from the tenant claim in the
 * JWT. An admin with no tenant membership produces a token with no tenant, so
 * every tenant-scoped insert fails with a not-null violation. An administrator
 * who does not belong to a hospital is also not a meaningful record in a
 * hospital system.
 *
 * <p>Tenant identity is configurable so a hospital can be named correctly on
 * first boot rather than being renamed later.
 *
 * <p>If no admin exists and the password env var is unset, startup logs a clear
 * error — the operator must set a bootstrap password before the system is used.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private static final String DEFAULT_TENANT_SLUG = "primary";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${medos.bootstrap.admin-password:}")
    private String bootstrapPassword;

    @Value("${medos.bootstrap.tenant-name:MedOS Hospital}")
    private String bootstrapTenantName;

    @Value("${medos.bootstrap.superadmin-username:platform}")
    private String bootstrapSuperAdminUsername;

    @Value("${medos.bootstrap.superadmin-password:}")
    private String bootstrapSuperAdminPassword;

    @Value("${medos.bootstrap.tenant-slug:primary}")
    private String bootstrapTenantSlug;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Platform super-admin (#127). Created only when the env password is set, so a
        // production boot never mints one silently. It holds no tenant membership on
        // purpose: LoginResponse returns tenantId=null, and that marks it apart from a
        // tenant-less regular admin, which is a fallback for fresh installs.
        if (bootstrapSuperAdminPassword != null && !bootstrapSuperAdminPassword.isBlank()) {
            userRepository.findByUsername(bootstrapSuperAdminUsername)
                    .or(() -> {
                        log.info("Creating platform super-admin '{}'", bootstrapSuperAdminUsername);
                        return Optional.of(userRepository.save(User.builder()
                                .username(bootstrapSuperAdminUsername)
                                .passwordHash(passwordEncoder.encode(bootstrapSuperAdminPassword))
                                .fullName("Platform Super Admin")
                                .email(bootstrapSuperAdminUsername + "@medos.local")
                                .active(true)
                                .isSuperAdmin(true)
                                .mustChangePassword(true)
                                .build()));
                    });
        } else {
            log.info("No MEDOS bootstrap super-admin password set; skipping super-admin creation. "
                    + "Tenant provisioning will remain gated on registration-token.");
        }

        boolean adminExists = userRepository.findByUsername("admin")
                .map(u -> u.getActive())
                .orElse(false);

        if (adminExists) {
            return;
        }

        if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
            log.error("""
                    ======================================================================
                    No active admin user exists and BOOTSTRAP_ADMIN_PASSWORD is not set.
                    Set BOOTSTRAP_ADMIN_PASSWORD (e.g. `openssl rand -base64 24`) and restart
                    to create the initial admin account. Login is currently impossible.
                    ======================================================================""");
            return;
        }

        if (bootstrapPassword.length() < 12) {
            log.warn("BOOTSTRAP_ADMIN_PASSWORD is shorter than 12 characters — consider a stronger value.");
        }

        // A legacy/inactive 'admin' may exist (e.g. deactivated demo account).
        // Reactivate it with the bootstrap password instead of violating the unique username.
        User admin = userRepository.findByUsername("admin")
                .map(existing -> {
                    existing.setActive(true);
                    existing.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .username("admin")
                        .passwordHash(passwordEncoder.encode(bootstrapPassword))
                        .fullName("System Administrator")
                        .email("admin@medos.local")
                        .active(true)
                        .build()));

        // The hospital itself. Reuse the existing tenant on a re-run so a
        // partially-completed bootstrap does not create a second hospital.
        String slug = bootstrapTenantSlug == null || bootstrapTenantSlug.isBlank()
                ? DEFAULT_TENANT_SLUG
                : bootstrapTenantSlug.trim();

        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .name(bootstrapTenantName)
                        .slug(slug)
                        .type(Tenant.TenantType.HOSPITAL)
                        .active(true)
                        .build()));

        if (tenantUserRepository.findByUserIdAndTenantId(admin.getId(), tenant.getId()).isEmpty()) {
            tenantUserRepository.save(TenantUser.builder()
                    .user(admin)
                    .tenant(tenant)
                    .role(TenantUser.UserRole.admin)
                    .build());
        }

        log.warn("Created/activated initial admin user 'admin' as administrator of tenant '{}' (slug '{}'). "
                + "The bootstrap password should now be rotated/removed.", tenant.getName(), tenant.getSlug());
    }
}
