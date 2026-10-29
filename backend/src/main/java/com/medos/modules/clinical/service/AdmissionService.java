package com.medos.modules.clinical.service;

import com.medos.dto.AdmissionRequest;
import com.medos.dto.DischargeRequest;
import com.medos.dto.DischargeResultDTO;
import com.medos.dto.DischargeSummaryAddendumRequest;
import com.medos.dto.DischargeSummaryAddendumDTO;
import com.medos.entity.Admission;
import com.medos.entity.Charge;
import com.medos.entity.Patient;
import com.medos.entity.Room;
import com.medos.entity.DischargeSummary;
import com.medos.entity.DischargeSummaryAddendum;
import com.medos.entity.Encounter;
import com.medos.entity.Prescription;
import com.medos.entity.LabOrder;
import com.medos.entity.MedicineCatalog;
import com.medos.entity.User;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.mapper.EntityDtoMapper;
import com.medos.repository.*;
import com.medos.security.CurrentUserProvider;
import com.medos.util.AuditLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import com.medos.modules.billing.event.PatientBalanceEvent;

@Service
@RequiredArgsConstructor
public class AdmissionService {

    private final AdmissionRepository admissionRepository;
    private final RoomRepository roomRepository;
    private final PatientRepository patientRepository;
    private final ChargeRepository chargeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogger auditLogger;
    private final DischargeSummaryRepository dischargeSummaryRepository;
    private final DischargeSummaryAddendumRepository dischargeSummaryAddendumRepository;
    private final EncounterRepository encounterRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicineCatalogRepository medicineCatalogRepository;
    private final LabOrderRepository labOrderRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DischargeSummaryAssembler dischargeSummaryAssembler;

    @Transactional
    public Admission admitPatient(AdmissionRequest request) {
        // Lock the room for update to prevent concurrent admission
        Room room = roomRepository.findByIdForUpdate(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", request.getRoomId().toString()));
        if (room.getOccupied()) {
            throw new BusinessException("Room " + room.getRoomNumber() + " is already occupied");
        }

        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", request.getPatientId().toString()));

        // Check patient is not already admitted
        admissionRepository.findByPatientIdAndStatus(request.getPatientId(), Admission.Status.admitted)
                .ifPresent(a -> {
                    throw new BusinessException("Patient is already admitted in room");
                });

        Admission admission = Admission.builder()
                .patientId(request.getPatientId())
                .roomId(request.getRoomId())
                .doctorId(request.getDoctorId())
                .admissionDate(LocalDateTime.now())
                .status(Admission.Status.admitted)
                .roomCharges(BigDecimal.ZERO)
                .daysAdmitted(0)
                .build();
        Admission saved = admissionRepository.save(admission);

        room.setOccupied(true);
        roomRepository.save(room);

        auditLogger.log("ADMIT", "Admission", saved.getId().toString(),
                null, "room=" + room.getRoomNumber() + " patient=" + patient.getUhid());
        return saved;
    }

    @Transactional
    public DischargeResultDTO dischargePatient(UUID admissionId, DischargeRequest request) {
        Admission admission = admissionRepository.findByIdForUpdate(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", admissionId.toString()));
        if (admission.getStatus() != Admission.Status.admitted) {
            throw new BusinessException("Patient is not currently admitted");
        }
        if (dischargeSummaryRepository.existsByAdmissionId(admissionId)) {
            throw new BusinessException("A signed discharge summary already exists for this admission");
        }

        // Lock the room for update to prevent concurrent operations
        Room room = roomRepository.findByIdForUpdate(admission.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", admission.getRoomId().toString()));

        LocalDateTime now = LocalDateTime.now();
        long daysAdmitted = admission.getAdmissionDate() != null
                ? ChronoUnit.DAYS.between(admission.getAdmissionDate(), now) + 1
                : 1;

        BigDecimal roomCharges = room.getDailyRate().multiply(BigDecimal.valueOf(daysAdmitted));

        UUID clinicianId = currentUserProvider.getCurrentUserId();
        if (clinicianId == null) {
            throw new BusinessException("A signed-in clinician is required to discharge a patient");
        }
        String clinicianName = userRepository.findById(clinicianId)
                .map(User::getFullName)
                .orElse("Unknown clinician");
        var prescriptions = prescriptionRepository.findByPatientId(admission.getPatientId());
        Map<UUID, MedicineCatalog> medicines = medicineCatalogRepository
                .findAllById(prescriptions.stream().map(Prescription::getMedicineId).toList())
                .stream()
                .collect(Collectors.toMap(MedicineCatalog::getId,
                        Function.identity()));
        List<Encounter> encounters = encounterRepository.findByPatientId(admission.getPatientId());
        List<Prescription> admissionPrescriptions = prescriptions.stream()
                .filter(p -> p.getPrescribedAt().isAfter(admission.getAdmissionDate()) &&
                        p.getPrescribedAt().isBefore(now))
                .toList();
        List<LabOrder> labOrders = labOrderRepository.findByPatientId(admission.getPatientId());

        DischargeSummary summary = dischargeSummaryAssembler.assemble(
                admission,
                request,
                now,
                clinicianId,
                clinicianName,
                encounters,
                admissionPrescriptions,
                medicines,
                labOrders);

        dischargeSummaryRepository.save(summary);

        auditLogger.log("DISCHARGE", "DischargeSummary", admission.getId().toString(), null, request.getClinicalCourse());

        room.setOccupied(false);
        roomRepository.save(room);

        admission.setStatus(Admission.Status.discharged);
        admission.setDischargeDate(now);
        admission.setDischargeDiagnosis(request.getDischargeDiagnosis());
        admission.setRoomCharges(roomCharges);
        admission.setDaysAdmitted((int) daysAdmitted);
        admissionRepository.save(admission);

        chargeRepository.save(Charge.builder()
                .tenantId(admission.getTenantId())
                .patientId(admission.getPatientId())
                .admissionId(admission.getId())
                .chargeType(Charge.ChargeType.room)
                .description("Room " + room.getRoomNumber() + " (" + room.getRoomType().name() + ") - " + daysAdmitted + " days")
                .unitPrice(room.getDailyRate())
                .amount(roomCharges)
                .totalAmount(roomCharges)
                .status(Charge.Status.unbilled)
                .createdAt(now)
                .build());

        eventPublisher.publishEvent(new PatientBalanceEvent(admission.getPatientId()));

        return DischargeResultDTO.builder()
                .admission(EntityDtoMapper.toDTO(admission))
                .summary(EntityDtoMapper.toDTO(summary))
                .build();
    }

    @Transactional
    public DischargeSummaryAddendumDTO addDischargeSummaryAddendum(UUID admissionId, DischargeSummaryAddendumRequest request) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new BusinessException("Admission not found"));
        if (admission.getStatus() != Admission.Status.admitted) {
            throw new BusinessException("Admission must be admitted to add discharge summary addendum");
        }

        DischargeSummary summary = dischargeSummaryRepository.findByAdmissionId(admissionId)
                .orElseThrow(() -> new BusinessException("Discharge summary not found for admission"));

        UUID clinicianId = currentUserProvider.getCurrentUserId();
        String clinicianName = userRepository.findById(clinicianId)
                .map(User::getFullName)
                .orElse("Unknown clinician");

        DischargeSummaryAddendum addendum = DischargeSummaryAddendum.builder()
                .tenantId(admission.getTenantId())
                .dischargeSummaryId(summary.getId())
                .text(request.getText())
                .authoredBy(clinicianId)
                .authorName(clinicianName)
                .createdAt(LocalDateTime.now())
                .build();

        dischargeSummaryAddendumRepository.save(addendum);

        return DischargeSummaryAddendumDTO.builder()
                .id(addendum.getId())
                .text(addendum.getText())
                .authoredBy(addendum.getAuthoredBy())
                .authorName(addendum.getAuthorName())
                .createdAt(addendum.getCreatedAt())
                .build();
    }

    public List<Admission> getActiveAdmissions() {
        return admissionRepository.findByStatus(Admission.Status.admitted);
    }

    public List<Admission> getPatientHistory(UUID patientId) {
        return admissionRepository.findByPatientId(patientId);
    }

    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    public List<Room> getAvailableRooms() {
        return roomRepository.findByOccupied(false);
    }
}