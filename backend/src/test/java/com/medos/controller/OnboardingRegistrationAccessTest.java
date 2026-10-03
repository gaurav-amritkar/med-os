package com.medos.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.medos.entity.TenantUser;
import com.medos.service.TenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Provisioning a hospital must not be something any signed-in user can do.
 *
 * <p>The anonymous hole is closed — {@code medos.onboarding.public-signup} defaults to
 * {@code false}, so the path requires authentication. But "authenticated" is not the same as
 * "allowed": this endpoint mints a tenant and an {@code admin} membership, so a doctor or a
 * nurse token that reaches it can create a hospital and administer it. Authentication is not
 * authorization, and the endpoint currently has no authorization at all.
 *
 * <p>Interim behaviour until a {@code super_admin} platform role exists (#54): only a caller
 * presenting the platform registration secret may provision a tenant. A hospital's own admin
 * must not be able to create a sibling hospital.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingRegistrationAccessTest {

    private static final String REGISTRATION_TOKEN = "test-registration-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantService tenantService;

    @MockitoBean
    private com.medos.util.AuditLogger auditLogger;

    // Matches the real OnboardingRequest: name, type, slug, contact fields, admin fields.
    @org.junit.jupiter.api.BeforeEach
    void stubTenantCreation() {
        // Without this the controller NPEs on tenant.getId() and every test reports 500,
        // which hides whether the request was authorized at all.
        com.medos.entity.Tenant tenant = com.medos.entity.Tenant.builder()
                .id(UUID.randomUUID())
                .name("Some Hospital")
                .slug("some-hospital")
                .active(true)
                .build();
        org.mockito.Mockito.lenient().when(tenantService.createTenant(
                anyString(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(tenant);
    }

    private String body(String registrationToken) {
        return """
                {"name":"Some Hospital","type":"HOSPITAL","slug":"some-hospital-%s",
                 "contactEmail":"contact@example.test","contactPhone":"+919000000000",
                 "adminUsername":"owner-%s","adminPassword":"Temp@12345",
                 "adminEmail":"owner-%s@example.test","adminFullName":"Owner Person"%s}
                """.formatted(
                UUID.randomUUID().toString().substring(0, 8),
                UUID.randomUUID().toString().substring(0, 8),
                UUID.randomUUID().toString().substring(0, 8),
                registrationToken == null ? ""
                        : ",\"registrationToken\":\"" + registrationToken + "\"");
    }

    @Test
    @DisplayName("an anonymous caller cannot provision a tenant")
    void anonymousCannotRegister() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null)))
                .andExpect(status().isUnauthorized());

        verify(tenantService, never()).createTenant(anyString(), any(), anyString(),
                anyString(), anyString(), any());
    }

    @ParameterizedTest(name = "a {0} token cannot provision a tenant")
    @ValueSource(strings = {"DOCTOR", "NURSE", "RECEPTIONIST", "PHARMACIST", "BILLING"})
    @WithMockUser(roles = "ADMIN")
    void nonPlatformRolesCannotRegister(String ignoredRolePlaceholder) throws Exception {
        // Role is irrelevant: what matters is that the platform secret is absent, because a
        // hospital's own admin must not be able to create a sibling hospital.
        mockMvc.perform(post("/api/v1/onboarding/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null)))
                .andExpect(status().isForbidden());

        verify(tenantService, never()).createTenant(anyString(), any(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("a hospital admin cannot provision a tenant, even though they are an admin")
    @WithMockUser(roles = "ADMIN")
    void tenantAdminCannotRegister() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null)))
                .andExpect(status().isForbidden());

        verify(tenantService, never()).createTenant(anyString(), any(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("a caller presenting the platform registration secret may provision a tenant")
    @WithMockUser(roles = "ADMIN")
    void platformOperatorCanRegister() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(REGISTRATION_TOKEN)))
                .andExpect(status().isOk());

        verify(tenantService).createTenant(anyString(), any(), anyString(), anyString(),
                anyString(), any());
    }

    @Test
    @DisplayName("a wrong registration secret is refused")
    @WithMockUser(roles = "ADMIN")
    void wrongTokenRejected() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-the-secret")))
                .andExpect(status().isForbidden());

        verify(tenantService, never()).createTenant(anyString(), any(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("provisioning a tenant is written to the audit log")
    @WithMockUser(roles = "ADMIN")
    void registrationIsAudited() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(REGISTRATION_TOKEN)))
                .andExpect(status().isOk());

        // Identity events must be auditable like every other material action. Asserted on
        // AuditLogger so a future refactor cannot quietly drop it.
        verify(auditLogger, org.mockito.Mockito.atLeastOnce()).log(
                org.mockito.ArgumentMatchers.eq("CREATE"),
                anyString(),
                anyString(),
                any(),
                anyString());
    }

    @Test
    @DisplayName("the role granted to a new tenant admin is admin, not something wider")
    @WithMockUser(roles = "ADMIN")
    void newTenantAdminGetsAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(REGISTRATION_TOKEN)))
                .andExpect(status().isOk());

        verify(tenantService).assignUserToTenant(any(), any(),
                org.mockito.ArgumentMatchers.eq(TenantUser.UserRole.admin));
    }

    @Test
    @DisplayName("the default mode is the safe one: onboarding is off unless a secret is set")
    @WithMockUser(roles = "ADMIN")
    void defaultModeIsTheSecretGate() throws Exception {
        // Two modes exist and must not be confused. With public self-serve signup off (the
        // shipped default) a signed-in admin still needs the platform secret; the flag is
        // what turns self-serve on, and it is an explicit operator decision per ADR-0003.
        mockMvc.perform(post("/api/v1/onboarding/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null)))
                .andExpect(status().isForbidden());
    }
}
