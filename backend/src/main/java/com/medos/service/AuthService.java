package com.medos.service;

import com.medos.dto.LoginRequest;
import com.medos.dto.LoginResponse;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.exception.BusinessException;
import com.medos.repository.TenantUserRepository;
import com.medos.repository.UserRepository;
import com.medos.security.JwtTokenProvider;
import com.medos.security.LoginRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final LoginRateLimiter rateLimiter;

    @Value("${medos.security.jwt.expiration-ms}")
    private long expirationMs;

    public LoginResponse login(LoginRequest request) {
        if (rateLimiter.isBlocked(request.getUsername())) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Try again later.");
        }

        User user = userRepository.findByUsername(request.getUsername())
                .or(() -> userRepository.findByEmail(request.getUsername()))
                .orElseThrow(() -> {
                    rateLimiter.recordFailure(request.getUsername());
                    return new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
                });

        if (!user.getActive()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Account is inactive");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            rateLimiter.recordFailure(request.getUsername());
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        rateLimiter.reset(request.getUsername());

        // Get primary tenant and role for this user
        // join fetch: the tenant is read outside a transaction, so a LAZY proxy here
        // would throw rather than resolve.
        List<TenantUser> tenantUsers = tenantUserRepository.findByUserIdWithTenant(user.getId());
        TenantUser activeMembership = firstActiveTenantMembership(tenantUsers);
        if (!tenantUsers.isEmpty() && activeMembership == null) {
            // The account is fine; every facility it belongs to is deactivated.
            // Without this check, deactivating a tenant was a cosmetic flag:
            // its staff signed in and worked exactly as before.
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "Your organisation is not active. Contact your administrator.");
        }
        // No membership at all is a bootstrap/superadmin session, which carries
        // no tenant. Those must keep working or a fresh install cannot be
        // administered.
        TenantUser.UserRole role = activeMembership == null ?
                TenantUser.UserRole.admin : activeMembership.getRole();
        UUID tenantId = activeMembership == null ? null : activeMembership.getTenant().getId();

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String token = tokenProvider.generateToken(user.getId(), user.getUsername(), role.name(), tenantId);

        return new LoginResponse(
                token,
                expirationMs / 1000L,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                role.name(),
                user.getSpecialization(),
                tenantId
        );
    }

    /**
     * The first membership whose tenant is active.
     *
     * <p>Selecting the active membership rather than the first one matters: a
     * clinician who works at two clinics should still sign in when only one of
     * them is suspended, and their session must be scoped to the clinic that
     * is actually usable.
     *
     * <p>{@code tenants.active} is a nullable {@code BOOLEAN} with no default,
     * so a null counts as inactive. A row that never had the flag written must
     * not be read as "not disabled" — for a missing value, the safe reading is
     * the closed one.
     */
    private TenantUser firstActiveTenantMembership(List<TenantUser> memberships) {
        return memberships.stream()
                .filter(m -> m.getTenant() != null)
                .filter(m -> Boolean.TRUE.equals(m.getTenant().getActive()))
                .findFirst()
                .orElse(null);
    }
}
