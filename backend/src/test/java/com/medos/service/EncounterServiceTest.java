package com.medos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medos.dto.EncounterDTO;
import com.medos.dto.PageResponse;
import com.medos.entity.Encounter;
import com.medos.entity.Patient;
import com.medos.entity.User;
import com.medos.repository.AuditLogRepository;
import com.medos.repository.EncounterRepository;
import com.medos.repository.MedicineCatalogRepository;
import com.medos.repository.PatientRepository;
import com.medos.repository.PrescriptionRepository;
import com.medos.repository.UserRepository;
import com.medos.security.CurrentUserProvider;
import com.medos.util.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers how encounter reads are enriched for display.
 *
 * <p>An encounter stores only ids. The patient profile's encounter history
 * rendered the doctor's raw UUID in its Doctor column, so these tests pin the
 * name resolution that replaced it, and pin that it stays batched per page.
 */
@ExtendWith(MockitoExtension.class)
class EncounterServiceTest {

    @Mock private EncounterRepository encounterRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private MedicineCatalogRepository medicineCatalogRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ObjectMapper objectMapper;
    private AuditLogger auditLogger; // real — Mockito cannot mock AuditLogger on JDK 26

    @InjectMocks private EncounterService encounterService;

    @BeforeEach
    void wireAuditLogger() {
        auditLogger = new AuditLogger(auditLogRepository, userRepository, currentUserProvider);
        ReflectionTestUtils.setField(encounterService, "auditLogger", auditLogger);
    }

    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID DOCTOR_A = UUID.randomUUID();
    private static final UUID DOCTOR_B = UUID.randomUUID();
    private static final Pageable PAGE = PageRequest.of(0, 20);

    private Encounter encounter(UUID id, UUID doctorId) {
        return Encounter.builder()
                .id(id)
                .patientId(PATIENT_ID)
                .doctorId(doctorId)
                .status(Encounter.Status.signed)
                .chiefComplaint("fever")
                .build();
    }

    private User doctor(UUID id, String fullName) {
        return User.builder().id(id).username("dr" + id.toString().substring(0, 4)).fullName(fullName).build();
    }

    private Patient patient() {
        return Patient.builder().id(PATIENT_ID).name("Asha Rao").uhid("UH-0001").build();
    }

    @Test
    void listByPatient_resolvesDoctorNameAndPatientIdentity() {
        when(encounterRepository.findByPatientId(PATIENT_ID, PAGE))
                .thenReturn(new PageImpl<>(List.of(
                        encounter(UUID.randomUUID(), DOCTOR_A),
                        encounter(UUID.randomUUID(), DOCTOR_B)), PAGE, 2));
        when(userRepository.findAllById(any()))
                .thenReturn(List.of(doctor(DOCTOR_A, "Dr Anita Rao"), doctor(DOCTOR_B, "Dr Bilal Khan")));
        when(patientRepository.findAllById(any())).thenReturn(List.of(patient()));

        PageResponse<EncounterDTO> result = encounterService.listByPatient(PATIENT_ID, 0, 20);

        assertEquals(2, result.getContent().size());
        assertEquals("Dr Anita Rao", result.getContent().get(0).getDoctorName());
        assertEquals("Dr Bilal Khan", result.getContent().get(1).getDoctorName());
        // Patient identity stays resolved on the same read path.
        assertEquals("Asha Rao", result.getContent().get(0).getPatientName());
        assertEquals("UH-0001", result.getContent().get(0).getPatientUhid());
        // The raw id is retained for callers that need to link or filter.
        assertEquals(DOCTOR_A, result.getContent().get(0).getDoctorId());
        // Pagination metadata must survive the enrichment.
        assertEquals(2, result.getTotalElements());
    }

    @Test
    void listByPatient_looksUpDoctorsOncePerPage_notPerRow() {
        when(encounterRepository.findByPatientId(PATIENT_ID, PAGE))
                .thenReturn(new PageImpl<>(List.of(
                        encounter(UUID.randomUUID(), DOCTOR_A),
                        encounter(UUID.randomUUID(), DOCTOR_A),
                        encounter(UUID.randomUUID(), DOCTOR_B)), PAGE, 3));
        when(userRepository.findAllById(any()))
                .thenReturn(List.of(doctor(DOCTOR_A, "Dr Anita Rao"), doctor(DOCTOR_B, "Dr Bilal Khan")));
        when(patientRepository.findAllById(any())).thenReturn(List.of(patient()));

        encounterService.listByPatient(PATIENT_ID, 0, 20);

        verify(userRepository, times(1)).findAllById(any());
        verify(patientRepository, times(1)).findAllById(any());
    }

    @Test
    void listByPatient_deletedDoctorRow_stillMapsWithNullName() {
        when(encounterRepository.findByPatientId(PATIENT_ID, PAGE))
                .thenReturn(new PageImpl<>(List.of(encounter(UUID.randomUUID(), DOCTOR_A)), PAGE, 1));
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(patientRepository.findAllById(any())).thenReturn(List.of(patient()));

        EncounterDTO dto = encounterService.listByPatient(PATIENT_ID, 0, 20).getContent().get(0);

        // The UI falls back to "Unknown clinician" rather than showing a UUID.
        assertNull(dto.getDoctorName());
        assertEquals(DOCTOR_A, dto.getDoctorId());
    }

    @Test
    void listByPatient_emptyPage_doesNotQueryUsers() {
        when(encounterRepository.findByPatientId(PATIENT_ID, PAGE))
                .thenReturn(new PageImpl<>(List.of(), PAGE, 0));

        PageResponse<EncounterDTO> result = encounterService.listByPatient(PATIENT_ID, 0, 20);

        assertEquals(0, result.getContent().size());
        verifyNoInteractions(userRepository, patientRepository);
    }

    @Test
    void getEncounter_resolvesDoctorName() {
        when(encounterRepository.findById(any())).thenReturn(Optional.of(encounter(UUID.randomUUID(), DOCTOR_A)));
        when(userRepository.findAllById(any())).thenReturn(List.of(doctor(DOCTOR_A, "Dr Anita Rao")));
        when(patientRepository.findAllById(any())).thenReturn(List.of(patient()));

        EncounterDTO dto = encounterService.getEncounter(UUID.randomUUID());

        assertEquals("Dr Anita Rao", dto.getDoctorName());
    }

    @Test
    void legacyListByPatient_resolvesDoctorName() {
        when(encounterRepository.findByPatientIdOrderByCreatedAtDesc(PATIENT_ID))
                .thenReturn(List.of(encounter(UUID.randomUUID(), DOCTOR_A)));
        when(userRepository.findAllById(any())).thenReturn(List.of(doctor(DOCTOR_A, "Dr Anita Rao")));
        when(patientRepository.findAllById(any())).thenReturn(List.of(patient()));

        List<EncounterDTO> result = encounterService.listByPatient(PATIENT_ID);

        assertEquals(1, result.size());
        assertEquals("Dr Anita Rao", result.get(0).getDoctorName());
    }
}
