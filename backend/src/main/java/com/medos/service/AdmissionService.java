package com.medos.service;

import com.medos.dto.AdmissionRequest;
import com.medos.dto.DischargeRequest;
import com.medos.entity.Admission;
import com.medos.entity.Charge;
import com.medos.entity.Patient;
import com.medos.entity.Room;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.repository.*;
import com.medos.util.AuditLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdmissionService {

    private final AdmissionRepository admissionRepository;
    private final RoomRepository roomRepository;
    private final PatientRepository patientRepository;
    private final ChargeRepository chargeRepository;
    private final AuditLogger auditLogger;

    @Transactional
    public Admission admitPatient(AdmissionRequest request) {
        Room room = roomRepository.findById(request.getRoomId())
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
    public Admission dischargePatient(UUID admissionId, DischargeRequest request) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission", admissionId.toString()));
        if (admission.getStatus() != Admission.Status.admitted) {
            throw new BusinessException("Patient is not currently admitted");
        }

        Room room = roomRepository.findById(admission.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", admission.getRoomId().toString()));

        LocalDateTime now = LocalDateTime.now();
        long daysAdmitted = admission.getAdmissionDate() != null
                ? ChronoUnit.DAYS.between(admission.getAdmissionDate(), now) + 1
                : 1;

        BigDecimal roomCharges = room.getDailyRate().multiply(BigDecimal.valueOf(daysAdmitted));

        admission.setDischargeDate(now);
        admission.setStatus(Admission.Status.discharged);
        admission.setDischargeDiagnosis(request.getDischargeDiagnosis());
        admission.setRoomCharges(roomCharges);
        admission.setDaysAdmitted((int) daysAdmitted);
        admissionRepository.save(admission);

        // Free the room
        room.setOccupied(false);
        roomRepository.save(room);

        // Auto-generate room charge
        Charge charge = Charge.builder()
                .patientId(admission.getPatientId())
                .admissionId(admission.getId())
                .chargeType(Charge.ChargeType.room)
                .description("Room " + room.getRoomNumber() + " (" + room.getRoomType().name() + ") - " + daysAdmitted + " days")
                .quantity((int) daysAdmitted)
                .unitPrice(room.getDailyRate())
                .amount(roomCharges)
                .gstPercent(BigDecimal.ZERO)
                .gstAmount(BigDecimal.ZERO)
                .totalAmount(roomCharges)
                .status(Charge.Status.unbilled)
                .build();
        chargeRepository.save(charge);

        auditLogger.log("DISCHARGE", "Admission", admission.getId().toString(),
                "admitted", "discharged days=" + daysAdmitted + " charges=" + roomCharges);
        return admission;
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
