package com.medos.service;

import com.medos.dto.PageResponse;
import com.medos.dto.PatientDTO;
import com.medos.dto.PatientRegistrationRequest;
import com.medos.entity.Patient;
import com.medos.exception.BusinessException;
import com.medos.repository.ConsentRepository;
import com.medos.repository.PatientRepository;
import com.medos.util.AuditLogger;
import com.medos.util.BlindIndexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
    @Mock
    private BlindIndexUtil blindIndexUtil;

    @InjectMocks
    private PatientService patientService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /** A real index, not a mock, so search routing is exercised for real. */
    private BlindIndexUtil realBlindIndex() {
        BlindIndexUtil util = new BlindIndexUtil();
        util.setPiiKeyForTesting("jIMpvDp7I0XTfZLdyYKeryj/7t7yKPydMu+4tOJMBus=");
        return util;
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

        when(patientRepository.getNextUhidSeq()).thenReturn(1L);
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

        PatientDTO result = patientService.registerPatient(req);
        assertNotNull(result);
        assertEquals("John Doe", result.getName());
        assertEquals("UHID000001", result.getUhid());
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
    void listPatients_searchByName_usesBlindIndexNotCiphertext() {
        // The regression this locks down: searching the encrypted column with
        // LIKE returns nothing for every query, because AES-GCM ciphertext is
        // randomised per value. Search must go through the blind index.
        ReflectionTestUtils.setField(patientService, "blindIndexUtil", realBlindIndex());
        BlindIndexUtil index = realBlindIndex();
        when(patientRepository.findByNameIndex(eq(index.indexPatientName("John Doe")), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        patientService.listPatients("John Doe", 0, 20);

        verify(patientRepository, times(1))
                .findByNameIndex(eq(index.indexPatientName("John Doe")), any(PageRequest.class));
        verify(patientRepository, never()).findByNameContainingIgnoreCase(anyString(), any(PageRequest.class));
    }

    @Test
    void listPatients_searchByUhid_usesPlaintextColumn() {
        ReflectionTestUtils.setField(patientService, "blindIndexUtil", realBlindIndex());
        when(patientRepository.findByUhidContainingIgnoreCase(eq("UHID000"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        patientService.listPatients("UHID000", 0, 20);

        verify(patientRepository, times(1))
                .findByUhidContainingIgnoreCase(eq("UHID000"), any(PageRequest.class));
        verify(patientRepository, never()).findByNameIndex(anyString(), any(PageRequest.class));
    }

    @Test
    void listPatients_searchIsCaseInsensitiveViaNormalisedIndex() {
        ReflectionTestUtils.setField(patientService, "blindIndexUtil", realBlindIndex());
        BlindIndexUtil index = realBlindIndex();
        String expected = index.indexPatientName("John Doe");
        assertNotNull(expected);
        when(patientRepository.findByNameIndex(eq(expected), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        patientService.listPatients("  john   doe  ", 0, 20);

        verify(patientRepository, times(1)).findByNameIndex(eq(expected), any(PageRequest.class));
    }

    @Test
    void listPatients_withoutSearch_returnsAll() {
        Page<Patient> page = new PageImpl<>(Collections.emptyList());
        when(patientRepository.findAll(any(PageRequest.class))).thenReturn(page);
        PageResponse<PatientDTO> result = patientService.listPatients(null, 0, 20);
        assertTrue(result.getContent().isEmpty());
        verify(patientRepository, times(1)).findAll(any(PageRequest.class));
    }
}
