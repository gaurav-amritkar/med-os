package com.medos.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.medos.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A caller's mistake is a 400, not a 500.
 *
 * <p>{@code MissingServletRequestParameterException} had no handler, so it fell
 * through to the catch-all and came back as
 * {@code INTERNAL_ERROR "An unexpected error occurred"}. That is actively
 * misleading: the server is fine, the caller omitted a field, and the response
 * names neither the field nor the fact that it was missing.
 *
 * <p>It cost real debugging time here. A stock-in shaped as a JSON body rather
 * than query parameters returned a bare 500, and the actual cause — "Required
 * request parameter 'batchNo' is not present" — appeared only in the server log.
 *
 * <p>The shape the frontend actually sends is {@code ?batchNo=…&expiryDate=…&quantity=…};
 * that path is exercised end to end against the running stack and is not
 * re-derived here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PharmacyRequestParamContractTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(UUID.fromString("00000000-0000-4000-8000-000000000001"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private UserDetails asPharmacist() {
        return User.withUsername("pharmacy")
                .password("unused")
                .authorities(new SimpleGrantedAuthority("ROLE_PHARMACIST"))
                .build();
    }

    @Test
    @DisplayName("a stock-in missing its required parameters is a 400 naming the parameter")
    void missingParameterIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/pharmacy/medicines/{id}/stock-in", UUID.randomUUID())
                        .with(user(asPharmacist())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("batchNo")));
    }

    @Test
    @DisplayName("a stock-in missing only the quantity still names the right parameter")
    void aSingleMissingParameterIsNamed() throws Exception {
        mockMvc.perform(post("/api/v1/pharmacy/medicines/{id}/stock-in", UUID.randomUUID())
                        .with(user(asPharmacist()))
                        .param("batchNo", "B-1")
                        .param("expiryDate", "2027-01-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("quantity")));
    }
}