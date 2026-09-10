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
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        // Get primary tenant and role for this user
        List<TenantUser> tenantUsers = tenantUserRepository.findByUserId(user.getId());
        TenantUser.UserRole role = tenantUsers.isEmpty() ?
                TenantUser.UserRole.admin : tenantUsers.get(0).getRole();
        UUID tenantId = tenantUsers.isEmpty() ? null : tenantUsers.get(0).getTenant().getId();

        String token = tokenProvider.generateToken(user.getId(), user.getUsername(), role.name(), tenantId);

        return new LoginResponse(
                token,
                expirationMs / 1000L,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                role.name(),
                user.getSpecialization()
        );
    }
}
