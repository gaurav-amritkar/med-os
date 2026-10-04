package com.medos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medos.entity.User;
import com.medos.repository.TenantRepository;
import com.medos.repository.TenantUserRepository;
import com.medos.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The platform super-admin is the account that provisions hospitals, so two things
 * matter: it is never minted silently on a production boot with no password, and
 * when it does exist it belongs to no hospital — a tenant membership would hand it
 * access to one hospital's patients.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminBootstrapRunnerSuperAdminTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantUserRepository tenantUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminBootstrapRunner runner;

    private AdminBootstrapRunner runnerWith(String username, String password) {
        ReflectionTestUtils.setField(runner, "bootstrapSuperAdminUsername", username);
        ReflectionTestUtils.setField(runner, "bootstrapSuperAdminPassword", password);
        ReflectionTestUtils.setField(runner, "bootstrapPassword", "");
        ReflectionTestUtils.setField(runner, "bootstrapTenantName", "MedOS Hospital");
        ReflectionTestUtils.setField(runner, "bootstrapTenantSlug", "primary");
        return runner;
    }

    @Test
    @DisplayName("a configured platform super-admin is created without any tenant membership")
    void createsSuperAdminWithoutMembership() {
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.findByUsername("platform")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return User.builder().id(UUID.randomUUID()).username(u.getUsername())
                    .passwordHash(u.getPasswordHash()).isSuperAdmin(u.getIsSuperAdmin()).build();
        });

        runnerWith("platform", "Str0ng!pass").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getIsSuperAdmin()).isTrue();
        assertThat(saved.getValue().getUsername()).isEqualTo("platform");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("encoded");
        verify(tenantUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("no password means no super-admin is minted on boot")
    void skipsWhenNoPasswordConfigured() {
        runnerWith("platform", "").run(null);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("an existing super-admin is not recreated or re-passworded")
    void idempotentAcrossRestarts() {
        User existing = User.builder().id(UUID.randomUUID()).username("platform")
                .isSuperAdmin(true).build();
        when(userRepository.findByUsername("platform")).thenReturn(Optional.of(existing));

        runnerWith("platform", "Str0ng!pass").run(null);

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }
}