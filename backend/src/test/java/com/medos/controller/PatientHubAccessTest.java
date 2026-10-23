package com.medos.controller;

import com.medos.entity.Tenant;
import com.medos.repository.TenantRepository;
import com.medos.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Hub's read side is open to every signed-in clinical role (the board is
 * the whole front page for reception), while its one write — registration —
 * stays front-desk authority. The new slice adds /summary and the cohort +
 * blood-group filters; both are reads, asserted here alongside the existing
 * register gate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientHubAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor CALLER =
            SecurityMockMvcRequestPostProcessors.user("11111111-2222-3333-4444-000000000001");

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(String role) {
        return CALLER.roles(role);
    }

    private UUID tenantContext() {
        Tenant t = new Tenant();
        t.setName("Patient Hub Access " + UUID.randomUUID());
        t.setType(Tenant.TenantType.HOSPITAL);
        t.setSlug("patient-hub-" + UUID.randomUUID().toString().substring(0, 8));
        t.setActive(true);
        return tenantRepository.saveAndFlush(t).getId();
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("the counter rail answers all five counts to any signed-in role")
    void summaryIsReadable() throws Exception {
        try {
            TenantContext.setTenantId(tenantContext());
            mockMvc.perform(get("/api/v1/patients/summary").with(as("NURSE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").exists())
                    .andExpect(jsonPath("$.newToday").exists())
                    .andExpect(jsonPath("$.activeOpd").exists())
                    .andExpect(jsonPath("$.ipd").exists())
                    .andExpect(jsonPath("$.discharged").exists());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("cohort and blood-group filters answer an empty page, not an error")
    void filteredListIsReadable() throws Exception {
        try {
            TenantContext.setTenantId(tenantContext());
            mockMvc.perform(get("/api/v1/patients")
                            .with(as("BILLING"))
                            .param("type", "ipd")
                            .param("bloodGroup", "A+"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.empty").value(true));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("an unknown cohort filter is a stated 400, never a silent empty page")
    void unknownTypeIsRejected() throws Exception {
        try {
            TenantContext.setTenantId(tenantContext());
            mockMvc.perform(get("/api/v1/patients")
                            .with(as("RECEPTIONIST"))
                            .param("type", "emergency"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Unknown patient type filter: emergency"));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("registering stays front-desk authority: no doctor write, no anonymous read")
    void writeGateAndAnonymousReadHold() throws Exception {
        try {
            TenantContext.setTenantId(tenantContext());
            mockMvc.perform(post("/api/v1/patients")
                            .with(as("DOCTOR"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Dr Write","age":30,"gender":"male",
                                     "phone":"9876543210","dpdpConsent":false}
                                    """))
                    .andExpect(status().isForbidden());
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(get("/api/v1/patients/summary")).andExpect(status().isUnauthorized());
    }
}
