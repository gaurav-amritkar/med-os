package com.medos.modules.clinical.service;

import com.medos.dto.DischargeRequest;
import com.medos.dto.DischargeResultDTO;
import com.medos.dto.DischargeSummaryAddendumRequest;
import com.medos.dto.DischargeSummaryAddendumDTO;
import com.medos.entity.Admission;
import com.medos.entity.DischargeSummary;
import com.medos.entity.DischargeSummaryAddendum;
import com.medos.entity.Room;
import com.medos.entity.User;
import com.medos.exception.BusinessException;
import com.medos.repository.AdmissionRepository;
import com.medos.repository.ChargeRepository;
import com.medos.repository.DischargeSummaryRepository;
import com.medos.repository.DischargeSummaryAddendumRepository;
import com.medos.repository.EncounterRepository;
import com.medos.repository.LabOrderRepository;
import com.medos.repository.MedicineCatalogRepository;
import com.medos.repository.PrescriptionRepository;
import com.medos.repository.RoomRepository;
import com.medos.repository.UserRepository;
import com.medos.security.CurrentUserProvider;
import com.medos.util.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AdmissionServiceDischargeSummaryTest {

    @Mock private AdmissionRepository admissionRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private ChargeRepository chargeRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private DischargeSummaryRepository dischargeSummaryRepository;
    @Mock private DischargeSummaryAddendumRepository dischargeSummaryAddendumRepository;
    @Mock private EncounterRepository encounterRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private MedicineCatalogRepository medicineCatalogRepository;
    @Mock private LabOrderRepository labOrderRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private AuditLogger auditLogger;
    @Spy private DischargeSummaryAssembler assembler = new DischargeSummaryAssembler();

    @InjectMocks private AdmissionService admissionService;

    private UUID clinicianId;
    private Admission admission;

    @BeforeEach
    void setUp() {
        clinicianId = UUID.randomUUID();
        admission = Admission.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .patientId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .admissionDate(LocalDateTime.now().minusDays(2))
                .status(Admission.Status.admitted)
                .build();
        Room room = Room.builder()
                .id(admission.getRoomId())
                .roomNumber("A-101")
                .roomType(Room.RoomType.general)
                .dailyRate(new BigDecimal("800.00"))
                .occupied(true)
                .build();

        when(admissionRepository.findByIdForUpdate(admission.getId())).thenReturn(Optional.of(admission));
        when(roomRepository.findByIdForUpdate(admission.getRoomId())).thenReturn(Optional.of(room));
        when(currentUserProvider.getCurrentUserId()).thenReturn(clinicianId);
        when(userRepository.findById(clinicianId)).thenReturn(Optional.of(User.builder()
                .id(clinicianId).username("doctor").fullName("Dr Anita Rao").build()));
        when(encounterRepository.findByPatientId(admission.getPatientId())).thenReturn(List.of());
        when(prescriptionRepository.findByPatientId(admission.getPatientId())).thenReturn(List.of());
        when(labOrderRepository.findByPatientId(admission.getPatientId())).thenReturn(List.of());
        when(dischargeSummaryRepository.save(any(DischargeSummary.class))).thenAnswer(invocation -> {
            DischargeSummary summary = invocation.getArgument(0);
            summary.setId(UUID.randomUUID());
            return summary;
        });
        lenient().when(userRepository.existsById(clinicianId)).thenReturn(true);
    }

    @Test
    void discharge_signsOneSummaryAndRepeatedCallCannotCreateAnotherCharge() {
        DischargeRequest request = new DischargeRequest();
        request.setAdmissionId(admission.getId());
        request.setPatientId(admission.getPatientId());
        request.setDischargingClinicianId(clinicianId);
        request.setDischargingClinicianName("Dr Anita Rao");
        request.setAdmissionDiagnosis("Viral infection");
        request.setDischargeDiagnosis("Recovered pneumonia");
        request.setClinicalCourse("Mild fever, improved");
        request.setProceduresPerformed("Blood tests");
        request.setInvestigationsSummary("Normal lab results");
        request.setVitalsSummary("BP 120/80, HR 80");
        request.setConditionAtDischarge("Stable");
        request.setFollowUpAdvice("Review in one week");

        DischargeResultDTO result = admissionService.dischargePatient(admission.getId(), request);

        assertThat(result.getAdmission().getStatus()).isEqualTo("discharged");
        assertThat(result.getSummary().getSignedAt()).isNotNull();
        assertThat(result.getSummary().getAdmissionDiagnosis()).isEqualTo("Viral infection");
        assertThat(result.getSummary().getDischargingClinicianName()).isEqualTo("Dr Anita Rao");
    }

    @Test
    void addAddendum_appendsCorrectionWithoutChangingSignedSummary() {
        when(admissionRepository.findById(admission.getId())).thenReturn(Optional.of(admission));
        LocalDateTime originalSignedAt = LocalDateTime.now().minusHours(2);
        DischargeSummary summary = DischargeSummary.builder()
                .id(UUID.randomUUID())
                .tenantId(admission.getTenantId())
                .admissionId(admission.getId())
                .patientId(admission.getPatientId())
                .dischargeDiagnosis("Original diagnosis")
                .signedAt(originalSignedAt)
                .build();
        when(dischargeSummaryRepository.findByAdmissionId(admission.getId())).thenReturn(Optional.of(summary));
        when(dischargeSummaryAddendumRepository.save(any(DischargeSummaryAddendum.class)))
                .thenAnswer(invocation -> {
                    DischargeSummaryAddendum addendum = invocation.getArgument(0);
                    addendum.setId(UUID.randomUUID());
                    addendum.setCreatedAt(LocalDateTime.now());
                    return addendum;
                });

        DischargeSummaryAddendumRequest request = new DischargeSummaryAddendumRequest();
        request.setText("Correction: review after three days.");

        var result = admissionService.addDischargeSummaryAddendum(admission.getId(), request);

        assertThat(result.getText()).isEqualTo("Correction: review after three days.");
        assertThat(result.getAuthoredBy()).isEqualTo(clinicianId);
        assertThat(result.getAuthorName()).isEqualTo("Dr Anita Rao");
        assertThat(summary.getDischargeDiagnosis()).isEqualTo("Original diagnosis");
        assertThat(summary.getSignedAt()).isEqualTo(originalSignedAt);
        verify(dischargeSummaryRepository, times(0)).save(summary);
    }
}