package com.medos.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medos.dto.PatientConsentRequest;
import com.medos.dto.PatientDTO;
import com.medos.service.PatientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consent is captured after registration, so the route is a front-desk action
 * with a role gate — not an authenticated free-for-all.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientConsentEndpointTest {

    private static final UUID PATIENT_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000042");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientService patientService;

    private PatientDTO consented(boolean granted) {
        return PatientDTO.builder()
                .id(PATIENT_ID)
                .uhid("UHID000042")
                .name("Asha Rao")
                .dpdpConsent(granted)
                .build();
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    @DisplayName("POST /patients/{id}/consent records a grant")
    void receptionistCanGrantConsent() throws Exception {
        when(patientService.recordConsent(eq(PATIENT_ID), any())).thenReturn(consented(true));

        mockMvc.perform(post("/api/v1/patients/{id}/consent", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"granted": true, "purpose": "Treatment"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dpdpConsent").value(true));

        ArgumentCaptor<PatientConsentRequest> captor = ArgumentCaptor.forClass(PatientConsentRequest.class);
        verify(patientService).recordConsent(eq(PATIENT_ID), captor.capture());
        assertThat(captor.getValue().getGranted()).isTrue();
        assertThat(captor.getValue().getPurpose()).isEqualTo("Treatment");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /patients/{id}/consent appends a withdrawal")
    void adminCanWithdrawConsent() throws Exception {
        when(patientService.recordConsent(eq(PATIENT_ID), any())).thenReturn(consented(false));

        mockMvc.perform(delete("/api/v1/patients/{id}/consent", PATIENT_ID)
                        .param("reason", "Patient asked at the desk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dpdpConsent").value(false));

        ArgumentCaptor<PatientConsentRequest> captor = ArgumentCaptor.forClass(PatientConsentRequest.class);
        verify(patientService).recordConsent(eq(PATIENT_ID), captor.capture());
        assertThat(captor.getValue().getGranted()).isFalse();
        assertThat(captor.getValue().getPurpose()).isEqualTo("Patient asked at the desk");
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    @DisplayName("a clinician cannot capture or withdraw consent")
    void doctorIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/patients/{id}/consent", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\": true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/patients/{id}/consent", PATIENT_ID))
                .andExpect(status().isForbidden());

        verify(patientService, never()).recordConsent(any(), any());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    @DisplayName("granted is required; a body that omits it is a 400, not a silent withdrawal")
    void grantedIsRequired() throws Exception {
        mockMvc.perform(post("/api/v1/patients/{id}/consent", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\": \"Treatment\"}"))
                .andExpect(status().isBadRequest());

        verify(patientService, never()).recordConsent(any(), any());
    }
}
