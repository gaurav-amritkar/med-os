package com.medos.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medos.service.TenantService;

/**
 * A deployment that has not configured a registration secret must have tenant provisioning
 * <em>off</em>, not silently open.
 *
 * <p>This is the default posture of the shipped configuration, and it is the one that matters:
 * the secret arrives from a gitignored file, so the common first-run state is "not set yet".
 * If a missing secret meant "allow", then forgetting one line of configuration would reopen
 * the hole this gate exists to close — the failure would be invisible until someone noticed.
 *
 * <p>Separate context from {@link OnboardingRegistrationAccessTest} because it pins a
 * different configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "medos.onboarding.registration-token=")
class OnboardingProvisioningDisabledByDefaultTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantService tenantService;

    private static final String BODY = """
            {"name":"Some Hospital","type":"HOSPITAL","slug":"some-hospital",
             "contactEmail":"contact@example.test","contactPhone":"+919000000000",
             "adminUsername":"owner","adminPassword":"Temp@12345",
             "adminEmail":"owner@example.test","registrationToken":"anything"}
            """;

    /** No registrationToken field at all: the presented value is blank, like the configured one. */
    private static final String BODY_NO_TOKEN = """
            {"name":"Some Hospital","type":"HOSPITAL","slug":"some-hospital",
             "contactEmail":"contact@example.test","contactPhone":"+919000000000",
             "adminUsername":"owner","adminPassword":"Temp@12345",
             "adminEmail":"owner@example.test"}
            """;

    @Test
    @DisplayName("with no secret configured, a blank token does not match a blank secret")
    @WithMockUser(roles = "ADMIN")
    void blankTokenDoesNotMatchBlankSecret() throws Exception {
        // The comparison alone would say these are equal, so this is the case that proves the
        // explicit blank guard exists rather than the byte comparison doing the work.
        mockMvc.perform(post("/api/v1/onboarding/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_NO_TOKEN))
                .andExpect(status().isForbidden());

        verify(tenantService, never()).createTenant(anyString(), any(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("with no secret configured, provisioning is refused even for a signed-in admin")
    @WithMockUser(roles = "ADMIN")
    void disabledWithoutConfiguredSecret() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());

        verify(tenantService, never()).createTenant(anyString(), any(), anyString(),
                anyString(), anyString(), any());
    }
}