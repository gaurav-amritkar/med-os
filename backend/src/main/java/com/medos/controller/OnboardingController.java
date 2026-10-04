package com.medos.controller;

import com.medos.dto.OnboardingRequest;
import com.medos.entity.Tenant;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.repository.UserRepository;
import com.medos.exception.BusinessException;
import com.medos.service.TenantService;
import com.medos.util.AuditLogger;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {
    private final TenantService tenantService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger auditLogger;

    /**
     * Platform registration secret, or blank when none is configured.
     *
     * <p>Blank disables the endpoint outright rather than permitting it, so a deployment that
     * forgets to set it is closed by default.
     */
    @Value("${medos.onboarding.registration-token:}")
    private String configuredRegistrationToken;

    /**
     * Explicit operator opt-in to anonymous self-serve signup (ADR-0003).
     *
     * <p>Two distinct modes, deliberately kept distinct:
     * <ul>
     *   <li><b>false (default)</b> — provisioning is an operator action and requires the
     *       platform registration secret. A signed-in user of any role is refused.</li>
     *   <li><b>true</b> — an operator has explicitly accepted anonymous self-serve signup,
     *       which ADR-0003 allows as a later decision.</li>
     * </ul>
     * Collapsing them would either remove the documented opt-in or reopen the default hole.
     */
    @Value("${medos.onboarding.public-signup:false}")
    private boolean publicSignupEnabled;

    /**
     * Provision a hospital tenant.
     *
     * <p>Requires the platform registration secret. This endpoint creates a tenant and an
     * {@code admin} membership, so allowing it to any authenticated caller would mean a
     * doctor token could create a hospital and administer it — authentication is not
     * authorization, and this endpoint previously had no authorization whatsoever.
     *
     * <p>Replaced by a {@code super_admin} role once #54 ships; the secret is the interim
     * that closes the hole now.
     */
    @PostMapping("/register")
    public ResponseEntity<Tenant> onboardTenant(@Valid @RequestBody OnboardingRequest request) {
        boolean platformOperator = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication() != null
                && org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication().getAuthorities().stream()
                        .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));

        if (!platformOperator && !publicSignupEnabled) {
            // Not the self-serve mode, so this is an operator action and must be proven.
            requireRegistrationToken(request.getRegistrationToken());
        }

        java.util.List<String> requestedFeatures = request.getFeatures() == null || request.getFeatures().isEmpty()
                ? java.util.List.of("admissions", "billing", "encounters", "pharmacy")
                : request.getFeatures();
        com.medos.security.FeatureFlags.validateRequested(requestedFeatures);
        String featuresCsv = String.join(",", requestedFeatures);

        Tenant tenant = tenantService.createTenant(
                request.getName(),
                Tenant.TenantType.valueOf(request.getType()),
                request.getSlug(),
                request.getContactEmail(),
                request.getContactPhone(),
                request.getAddress()
        );

        User admin = User.builder()
                .username(request.getAdminUsername())
                .passwordHash(passwordEncoder.encode(request.getAdminPassword()))
                .fullName(request.getAdminFullName() != null ? request.getAdminFullName() : request.getAdminUsername())
                .email(request.getAdminEmail())
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(admin);

        tenantService.assignUserToTenant(admin.getId(), tenant.getId(), TenantUser.UserRole.admin);

        // Tenant creation is an identity event and must be auditable like every other.
        auditLogger.log("CREATE", "Tenant", tenant.getId().toString(), null,
                "Provisioning request created tenant " + tenant.getName()
                        + " with admin " + admin.getUsername());

        if (request.getConfig() != null) {
            request.getConfig().forEach((k, v) -> tenantService.setConfig(tenant.getId(), k, v));
        }

        tenantService.setConfig(tenant.getId(), com.medos.security.FeatureFlags.CONFIG_KEY, featuresCsv);

        return ResponseEntity.ok(tenant);
    }

    /**
     * Reject the request unless it presents the configured platform secret.
     *
     * <p>Compared in constant time: a byte-by-byte {@code equals} on a shared secret leaks its
     * prefix through timing, and this value is the only thing standing between an anonymous
     * caller and a tenant with admin rights.
     */
    private void requireRegistrationToken(String presented) {
        if (configuredRegistrationToken == null || configuredRegistrationToken.isBlank()) {
            // Fail closed. No configured secret means self-serve provisioning is switched off,
            // which is the correct state for a deployment that has not opted in.
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "Tenant provisioning is not enabled. A platform operator must provision it.");
        }
        byte[] expected = configuredRegistrationToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = presented == null
                ? new byte[0]
                : presented.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "A valid platform registration token is required to provision a tenant.");
        }
    }
}
