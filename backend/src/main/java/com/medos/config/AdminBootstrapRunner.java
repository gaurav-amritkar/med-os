package com.medos.config;

import com.medos.entity.User;
import com.medos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bootstraps the first admin account for production deployments.
 *
 * Flyway no longer ships any users (V3 removes the demo accounts), so a fresh
 * production database has no way to log in. Deployments set
 * {@code BOOTSTRAP_ADMIN_PASSWORD} (one-time) and this runner creates the
 * {@code admin} user on first startup only.
 *
 * If no admin exists and the env var is unset, startup logs a clear error —
 * the operator must rotate in a bootstrap password before the system can be used.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${medos.bootstrap.admin-password:}")
    private String bootstrapPassword;

    @Override
    public void run(ApplicationArguments args) {
        boolean adminExists = userRepository.findByRole(User.Role.admin)
                .stream().anyMatch(User::getActive);

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
        userRepository.findByUsername("admin").ifPresentOrElse(
                existing -> {
                    existing.setActive(true);
                    existing.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
                    userRepository.save(existing);
                },
                () -> {
                    User admin = User.builder()
                            .username("admin")
                            .passwordHash(passwordEncoder.encode(bootstrapPassword))
                            .fullName("System Administrator")
                            .email("admin@medos.local")
                            .role(User.Role.admin)
                            .active(true)
                            .build();
                    userRepository.save(admin);
                }
        );

        log.warn("Created/activated initial admin user 'admin'. The bootstrap password should now be rotated/removed.");
    }
}
