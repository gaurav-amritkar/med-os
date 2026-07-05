package com.medos.service;

import com.medos.dto.LoginRequest;
import com.medos.dto.LoginResponse;
import com.medos.entity.User;
import com.medos.exception.BusinessException;
import com.medos.repository.UserRepository;
import com.medos.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Value("${medos.security.jwt.expiration-ms}")
    private long expirationMs;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .or(() -> userRepository.findByEmail(request.getUsername()))
                .orElseThrow(() -> new BusinessException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!user.getActive()) {
            throw new BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, "Account is inactive");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String token = tokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole().name());

        return new LoginResponse(
                token,
                expirationMs / 1000L,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().name(),
                user.getSpecialization()
        );
    }
}
