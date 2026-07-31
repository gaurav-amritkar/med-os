package com.medos.service;

import com.medos.dto.PatientRegistrationRequest;
import com.medos.entity.Patient;
import com.medos.exception.BusinessException;
import com.medos.repository.ConsentRepository;
import com.medos.repository.PatientRepository;
import com.medos.util.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private ConsentRepository consentRepository;
    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private PatientService patientService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void registerPatient_successful() {
        PatientRegistrationRequest req = new PatientRegistrationRequest();
        req.setDpdpConsent(true);
        req.setName("John Doe");
        req.setAge(30);
        req.setGender("Male");
        req.setPhone("1234567890");
        req.setEmail("john@example.com");
        req.setAddress("123 Street");
        req.setBloodGroup("A+");
        req.setConsentPurpose("Treatment");

        when(patientRepository.findMaxUhidSequence()).thenReturn(Optional.of(0));
        Patient savedPatient = Patient.builder()
                .id(UUID.randomUUID())
                .uhid("UHID000001")
                .name("John Doe")
                .age(30)
                .gender("Male")
                .phone("1234567890")
                .email("john@example.com")
                .address("123 Street")
                .bloodGroup("A+")
                .dpdpConsent(true)
                .dpdpConsentAt(LocalDateTime.now())
                .outstanding(BigDecimal.ZERO)
                .build();
        when(patientRepository.save(any(Patient.class))).thenReturn(savedPatient);

        Patient result = patientService.registerPatient(req);
        assertNotNull(result);
        assertEquals("John Doe", result.getName());
        verify(consentRepository, times(1)).save(any());
        verify(auditLogger, times(1)).log(eq("CREATE"), eq("Patient"), eq(savedPatient.getId().toString()), isNull(), anyString());
    }

    @Test
    void registerPatient_missingConsent_throwsException() {
        PatientRegistrationRequest req = new PatientRegistrationRequest();
        req.setDpdpConsent(false);
        req.setName("John");
        req.setAge(25);
        BusinessException ex = assertThrows(BusinessException.class, () -> patientService.registerPatient(req));
        assertEquals("DPDP consent is required for patient registration", ex.getMessage());
    }

    @Test
    void registerPatient_invalidAge_throwsException() {
        PatientRegistrationRequest req = new PatientRegistrationRequest();
        req.setDpdpConsent(true);
        req.setName("John");
        req.setAge(200);
        BusinessException ex = assertThrows(BusinessException.class, () -> patientService.registerPatient(req));
        assertEquals("Invalid patient age (0-150)", ex.getMessage());
    }

    @Test
    void listPatients_withSearch_callsRepository() {
        when(patientRepository.findByNameContainingIgnoreCase("john")).thenReturn(Collections.emptyList());
        List<Patient> result = patientService.listPatients("john");
        assertTrue(result.isEmpty());
        verify(patientRepository, times(1)).findByNameContainingIgnoreCase("john");
    }

    @Test
    void listPatients_withoutSearch_returnsAll() {
        List<Patient> dummy = Collections.emptyList();
        when(patientRepository.findAll()).thenReturn(dummy);
        List<Patient> result = patientService.listPatients(null);
        assertSame(dummy, result);
        verify(patientRepository, times(1)).findAll();
    }
}
