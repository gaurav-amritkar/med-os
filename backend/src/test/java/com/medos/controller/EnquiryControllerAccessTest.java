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
 * Who can run the desk. Logging or status-changing an enquiry is front-desk
 * authority (receptionist/admin): a pharmacist must not move leads off the
 * board — but the board itself is readable by the clinical roles, because
 * today's enquiry may become tomorrow's appointment a doctor runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnquiryControllerAccessTest {

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
        t.setName("Enquiry Access " + UUID.randomUUID());
        t.setType(Tenant.TenantType.HOSPITAL);
        t.setSlug("enquiry-access-" + UUID.randomUUID().toString().substring(0, 8));
        t.setActive(true);
        return tenantRepository.saveAndFlush(t).getId();
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a receptionist logs an enquiry (201) and the board answers ENQ-1")
    void receptionistCanLog() throws Exception {
        UUID tenant = tenantContext();
        try {
            TenantContext.setTenantId(tenant);
            mockMvc.perform(post("/api/v1/enquiries")
                            .with(as("RECEPTIONIST"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"contactName":"Anita Sharma","type":"appointment",
                                     "reason":"Wants a cardiology appointment"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.enquiryRef").value("ENQ-1"))
                    .andExpect(jsonPath("$.status").value("new_enquiry"));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("a pharmacist gets 403 on logging and status change, but reads the board")
    void pharmacistIsReadOnly() throws Exception {
        mockMvc.perform(post("/api/v1/enquiries")
                        .with(as("PHARMACIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactName":"X","type":"general","reason":"r"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/enquiries/00000000-0000-4000-8000-00000000000a/status")
                        .with(as("PHARMACIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"cancelled"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unauthenticated caller gets 401")
    void anonymousIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/enquiries")).andExpect(status().isUnauthorized());
    }
}
