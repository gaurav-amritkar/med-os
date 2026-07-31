package com.medos.service;

import com.medos.dto.LoginRequest;
import com.medos.dto.LoginResponse;
import com.medos.entity.User;
import com.medos.exception.BusinessException;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private LoginRateLimiter rateLimiter;
    @InjectMocks private AuthService authService;

    private static final long EXPIRATION_MS = 3600000L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "expirationMs", EXPIRATION_MS);
        when(rateLimiter.isBlocked(anyString())).thenReturn(false);
    }

    private User activeUser(String username, User.Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .passwordHash("$2a$10$hashed")
                .fullName("Dr " + username)
                .role(role)
                .active(true)
                .specialization("General")
                .build();
    }

    @Test
    void login_success_returnsResponseWithToken() {
        User user = activeUser("admin", User.Role.admin);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "$2a$10$hashed")).thenReturn(true);
        when(tokenProvider.generateToken(user.getId(), "admin", "admin"))
                .thenReturn("jwt-token");

        LoginResponse response = authService.login(new LoginRequest() {{
            setUsername("admin"); setPassword("password");
        }});

        assertEquals("jwt-token", response.getToken());
        assertEquals(EXPIRATION_MS / 1000L, response.getExpiresIn());
        assertEquals(user.getId(), response.getUserId());
        assertEquals("admin", response.getUsername());
        assertEquals(user.getRole().name(), response.getRole());
        assertNotNull(user.getLastLogin());
        verify(userRepository).save(user);
    }

    @Test
    void login_unknownUser_throwsUnauthorized() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(login("ghost", "x")));
        assertEquals(401, ex.getStatus().value());
        verifyNoInteractions(passwordEncoder);
        verify(tokenProvider, never()).generateToken(any(), any(), any());
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        User user = activeUser("doctor", User.Role.doctor);
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(login("doctor", "wrong")));
        assertEquals(401, ex.getStatus().value());
        verify(tokenProvider, never()).generateToken(any(), any(), any());
    }

    @Test
    void login_inactiveUser_throwsForbidden() {
        User user = activeUser("nurse", User.Role.nurse);
        user.setActive(false);
        when(userRepository.findByUsername("nurse")).thenReturn(Optional.of(user));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(login("nurse", "password")));
        assertEquals(403, ex.getStatus().value());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_fallsBackToEmailLookup() {
        User user = activeUser("reception", User.Role.receptionist);
        user.setEmail("reception@medos.test");
        when(userRepository.findByUsername("reception@medos.test")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("reception@medos.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(tokenProvider.generateToken(any(), any(), any())).thenReturn("tok");

        LoginResponse response = authService.login(login("reception@medos.test", "password"));

        assertEquals("tok", response.getToken());
    }

    @Test
    void login_whenLockedOut_throwsTooManyRequests() {
        when(rateLimiter.isBlocked("admin")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(login("admin", "whatever")));
        assertEquals(429, ex.getStatus().value());
        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    void login_wrongPassword_recordsFailure() {
        User user = activeUser("doctor", User.Role.doctor);
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

        assertThrows(BusinessException.class, () -> authService.login(login("doctor", "wrong")));
        verify(rateLimiter).recordFailure("doctor");
    }

    @Test
    void login_success_resetsFailureCounter() {
        User user = activeUser("admin", User.Role.admin);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "$2a$10$hashed")).thenReturn(true);
        when(tokenProvider.generateToken(any(), any(), any())).thenReturn("tok");

        authService.login(login("admin", "password"));
        verify(rateLimiter).reset("admin");
    }

    private LoginRequest login(String username, String password) {
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword(password);
        return r;
    }
}
