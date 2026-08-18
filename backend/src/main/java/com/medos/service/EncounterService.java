package com.medos.service;

import com.medos.dto.EncounterDTO;
import com.medos.dto.PageResponse;
import com.medos.dto.PrescriptionDTO;
import com.medos.dto.EncounterRequest;
import com.medos.dto.PrescriptionRequest;
import com.medos.entity.Encounter;
import com.medos.entity.Prescription;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.mapper.EntityDtoMapper;
import com.medos.repository.EncounterRepository;
import com.medos.repository.PrescriptionRepository;
import com.medos.repository.UserRepository;
import com.medos.security.CurrentUserProvider;
import com.medos.util.AuditLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EncounterService {

    private final EncounterRepository encounterRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    @Transactional
    public EncounterDTO createEncounter(EncounterRequest request) {
        UUID doctorId = currentUserProvider.getCurrentUserId();
        if (doctorId == null) {
            throw new BusinessException("Authenticated user required");
        }

        Encounter encounter = Encounter.builder()
                .patientId(request.getPatientId())
                .doctorId(doctorId)
                .status(Encounter.Status.open)
                .chiefComplaint(request.getChiefComplaint())
                .diagnosis(request.getDiagnosis())
                .clinicalNotes(request.getClinicalNotes())
                .vitalsJson(serializeVitals(request.getVitals()))
                .build();
        Encounter saved = encounterRepository.save(encounter);
        auditLogger.log("CREATE", "Encounter", saved.getId().toString());
        return EntityDtoMapper.toDTO(saved);
    }

    public EncounterDTO getEncounter(UUID id) {
        return encounterRepository.findById(id)
                .map(EntityDtoMapper::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter", id.toString()));
    }

    public PageResponse<EncounterDTO> listByPatient(UUID patientId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Encounter> result = encounterRepository.findByPatientId(patientId, pageable);
        return PageResponse.of(result.map(EntityDtoMapper::toDTO));
    }

    // Backward compatibility
    public List<EncounterDTO> listByPatient(UUID patientId) {
        return encounterRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(EntityDtoMapper::toDTO)
                .toList();
    }

    @Transactional
    public EncounterDTO signEncounter(UUID id) {
        Encounter encounter = getEncounterEntity(id);
        if (encounter.getStatus() != Encounter.Status.open) {
            throw new BusinessException("Encounter is not open for signing");
        }
        UUID signer = currentUserProvider.getCurrentUserId();
        encounter.setStatus(Encounter.Status.signed);
        encounter.setSignedAt(LocalDateTime.now());
        encounter.setSignedBy(signer);
        Encounter saved = encounterRepository.save(encounter);
        auditLogger.log("SIGN", "Encounter", saved.getId().toString());
        return EntityDtoMapper.toDTO(saved);
    }

    @Transactional
    public PrescriptionDTO addPrescription(PrescriptionRequest request) {
        Encounter encounter = getEncounterEntity(request.getEncounterId());
        if (encounter.getStatus() == Encounter.Status.signed) {
            throw new BusinessException("Cannot add prescription to a signed encounter");
        }
        UUID prescriber = currentUserProvider.getCurrentUserId();
        if (prescriber == null) {
            throw new BusinessException("Authenticated user required");
        }
        Prescription rx = Prescription.builder()
                .encounterId(request.getEncounterId())
                .patientId(request.getPatientId())
                .medicineId(request.getMedicineId())
                .dosage(request.getDosage())
                .frequency(request.getFrequency())
                .duration(request.getDuration())
                .instructions(request.getInstructions())
                .status(Prescription.Status.pending)
                .prescribedBy(prescriber)
                .build();
        Prescription saved = prescriptionRepository.save(rx);
        auditLogger.log("CREATE", "Prescription", saved.getId().toString());
        return EntityDtoMapper.toDTO(saved);
    }

    public List<PrescriptionDTO> listPrescriptions(UUID encounterId) {
        return prescriptionRepository.findByEncounterId(encounterId)
                .stream()
                .map(EntityDtoMapper::toDTO)
                .toList();
    }

    public List<PrescriptionDTO> pendingPrescriptions() {
        return prescriptionRepository.findByStatus(Prescription.Status.pending)
                .stream()
                .map(EntityDtoMapper::toDTO)
                .toList();
    }

    // Internal method to get entity for operations
    private Encounter getEncounterEntity(UUID id) {
        return encounterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter", id.toString()));
    }

    private String serializeVitals(Object vitals) {
        try {
            return objectMapper.writeValueAsString(vitals);
        } catch (Exception e) {
            return "{}";
        }
    }
}
