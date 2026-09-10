package com.medos.controller;

import com.medos.dto.OnboardingRequest;
import com.medos.entity.Tenant;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.repository.UserRepository;
import com.medos.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {
    private final TenantService tenantService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<Tenant> onboardTenant(@Valid @RequestBody OnboardingRequest request) {
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

        if (request.getConfig() != null) {
            request.getConfig().forEach((k, v) -> tenantService.setConfig(tenant.getId(), k, v));
        }

        return ResponseEntity.ok(tenant);
    }
}