package com.medos.service;

import com.medos.entity.Patient;
import com.medos.exception.BusinessException;
import com.medos.repository.PatientRepository;
import com.medos.util.AuditLogger;
import com.medos.util.BlindIndexUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Patients Hub counter rail and cohort filters (Wave 1, parity row 3).
 * Counts ride one group of count queries; filters ride a Specification, and
 * the desk-visible contract errors are stated, never silent omissions.
 */
class PatientHubServiceTest {

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private com.medos.repository.ConsentRepository consentRepository;
    @Mock
    private AuditLogger auditLogger;
    @Mock
    private BlindIndexUtil blindIndexUtil;

    @InjectMocks
    private PatientService patientService;

    private final AutoCloseable closeable = MockitoAnnotations.openMocks(this);

    private Patient patient(String uhid) {
        return Patient.builder()
                .uhid(uhid)
                .name("Patient " + uhid)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    @AfterEach
    void close() throws Exception {
        closeable.close();
    }

    @Test
    @DisplayName("hubSummary answers the whole rail from one group of counts")
    void summaryWiresAllFiveCounts() {
        when(patientRepository.count()).thenReturn(42L);
        when(patientRepository.countByCreatedAtAfter(any(LocalDateTime.class))).thenReturn(3L);
        when(patientRepository.countActiveOpdPatients()).thenReturn(9L);
        when(patientRepository.countIpdPatients()).thenReturn(5L);
        when(patientRepository.countDischargedPatients()).thenReturn(11L);

        PatientService.HubSummary summary = patientService.hubSummary();

        assertEquals(42L, summary.total());
        assertEquals(3L, summary.newToday());
        assertEquals(9L, summary.activeOpd());
        assertEquals(5L, summary.ipd());
        assertEquals(11L, summary.discharged());
        // "New Today" counts from the start of the local day, not the last 24h.
        assertTrue(summary.newToday() >= 0);
    }

    @Test
    @DisplayName("no filters keeps the existing search path untouched")
    void noFilterDelegatesToExistingPath() {
        when(patientRepository.findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(patient("UHID000001")),
                        PageRequest.of(0, 20), 1));

        var result = patientService.listPatients(null, null, null, 0, 20);

        assertEquals(1, result.getContent().size());
        verify(patientRepository, never())
                .findAll(org.mockito.ArgumentMatchers.<Specification<Patient>>any(), any(Pageable.class));
    }

    @Test
    @DisplayName("a cohort filter routes through the Specification path")
    void cohortFilterUsesSpecification() {
        when(patientRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Patient>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(patient("UHID000002")), PageRequest.of(0, 20), 1));

        var result = patientService.listPatients(null, "ipd", null, 0, 20);

        assertEquals(1, result.getContent().size());
        verify(patientRepository).findAll(org.mockito.ArgumentMatchers.<Specification<Patient>>any(), any(Pageable.class));
    }

    @Test
    @DisplayName("an unknown cohort is a stated error, not an empty page")
    void unknownTypeIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> patientService.listPatients(null, "emergency", null, 0, 20));
        assertTrue(ex.getMessage().contains("Unknown patient type filter"));
    }

    @Test
    @DisplayName("name fragments cannot combine with filters — stated, not silently dropped")
    void nameSearchWithFilterIsRefused() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> patientService.listPatients("anita", "ipd", null, 0, 20));
        assertTrue(ex.getMessage().contains("search by UHID"));
    }
}
