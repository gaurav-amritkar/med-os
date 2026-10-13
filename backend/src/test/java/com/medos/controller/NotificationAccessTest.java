package com.medos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import com.medos.entity.Notification;
import com.medos.entity.User;
import com.medos.repository.NotificationRepository;
import com.medos.repository.TenantRepository;
import com.medos.repository.UserRepository;
import com.medos.security.TenantContext;
import com.medos.service.DashboardService;
import com.medos.service.TenantService;
import com.medos.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A notification belongs to its recipient, and only its recipient.
 *
 * <p>{@code notifications} is absent from {@code TenantStatementInspector.TENANT_TABLES},
 * so no tenant predicate is added to the statement. Between that and an
 * {@code isAuthenticated()}-only guard, {@code findById(id)} on mark-read was
 * reachable by any signed-in account in any tenant — the ownership check simply
 * did not exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationAccessTest {

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    private final UUID callerId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationRepository notificationRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private TenantService tenantService;

    @MockitoBean
    private TenantRepository tenantRepository;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
        // resolveUser() parses the principal name as a UUID, so the caller is
        // authenticated under their own id.
        when(userRepository.findById(any()))
                .thenReturn(Optional.of(User.builder()
                        .id(callerId).username("caller").fullName("Caller")
                        .active(true).build()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("marking someone else's notification read does nothing and says 404")
    void cannotMarkAnotherUsersNotificationRead() throws Exception {
        // Scoped lookup misses, because the row belongs to another recipient.
        when(notificationRepository.findByIdAndRecipientId(notificationId, callerId))
                .thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/notifications/{id}/read", notificationId)
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(callerId.toString()).roles("DOCTOR")))
                .andExpect(status().isNotFound());

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("the unscoped findById is not used to mutate")
    void neverLooksUpByIdAlone() throws Exception {
        when(notificationRepository.findByIdAndRecipientId(notificationId, callerId))
                .thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/notifications/{id}/read", notificationId)
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(callerId.toString()).roles("DOCTOR")))
                .andExpect(status().isNotFound());

        // findById returns the row whatever the recipient, which is the exact call
        // that made this an IDOR.
        verify(notificationRepository, never()).findById(any());
    }

    @Test
    @DisplayName("marking your own notification read works")
    void canMarkOwnNotificationRead() throws Exception {
        Notification mine = Notification.builder()
                .id(notificationId)
                .recipientId(callerId)
                .read(false)
                .build();
        when(notificationRepository.findByIdAndRecipientId(any(), any()))
                .thenReturn(Optional.of(mine));

        mockMvc.perform(put("/api/v1/notifications/{id}/read", notificationId)
                        .with(SecurityMockMvcRequestPostProcessors
                                .user(callerId.toString()).roles("DOCTOR")))
                .andExpect(status().isNoContent());

        verify(notificationRepository).save(mine);
        assertThat(mine.getRead()).isTrue();
    }
}
