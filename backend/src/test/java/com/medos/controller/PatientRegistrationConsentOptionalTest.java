package com.medos.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medos.dto.PatientDTO;
import com.medos.service.PatientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Consent is decoupled from registration (Wave 0, medworks-parity design).
 *
 * <p>The old contract rejected the whole request at bean validation when the
 * caller omitted {@code dpdpConsent} — a 400 that a receptionist could not do
 * anything about, for a checkbox the reference system does not need up front.
 * The new contract: registration succeeds without consent; consent itself is an
 * auditable event captured separately (the {@code consents} table already
 * stores it when the caller does send consent).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientRegistrationConsentOptionalTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientService patientService;

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    @DisplayName("POST /patients without dpdpConsent registers the patient instead of 400ing")
    void registrationWithoutConsentFieldIsAccepted() throws Exception {
        when(patientService.registerPatient(any())).thenReturn(PatientDTO.builder()
                .id(java.util.UUID.randomUUID())
                .uhid("UHID000001")
                .name("Walk-in Without Consent")
                .build());

        // The body deliberately omits dpdpConsent entirely — the field that used
        // to carry a @NotNull validation and made this request a 400.
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Walk-in Without Consent", "age": 30, "gender": "male",
                                 "phone": "9812345678"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uhid").value("UHID000001"));

        verify(patientService, times(1)).registerPatient(any());
    }
}
