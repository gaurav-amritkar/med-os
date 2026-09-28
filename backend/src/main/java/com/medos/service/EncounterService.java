package com.medos.service;

import com.medos.dto.EncounterDTO;
import com.medos.dto.PageResponse;
import com.medos.dto.PrescriptionDTO;
import com.medos.dto.EncounterRequest;
import com.medos.dto.PrescriptionRequest;
import com.medos.entity.Encounter;
import com.medos.entity.MedicineCatalog;
import com.medos.entity.Patient;
import com.medos.entity.Prescription;
import com.medos.entity.User;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.mapper.EntityDtoMapper;
import com.medos.repository.EncounterRepository;
import com.medos.repository.MedicineCatalogRepository;
import com.medos.repository.PatientRepository;
import com.medos.repository.PrescriptionRepository;
import com.medos.repository.UserRepository;
import com.medos.security.CurrentUserProvider;
import com.medos.util.AuditLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EncounterService {

    private final EncounterRepository encounterRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PatientRepository patientRepository;
    private final MedicineCatalogRepository medicineCatalogRepository;
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
        return toEncounterDTO(saved);
    }

    public EncounterDTO getEncounter(UUID id) {
        return encounterRepository.findById(id)
                .map(this::toEncounterDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter", id.toString()));
    }

    public PageResponse<EncounterDTO> listByPatient(UUID patientId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Encounter> result = encounterRepository.findByPatientId(patientId, pageable);
        return toPageResponse(result, pageable);
    }

    /**
     * Encounter worklist, newest first.
     *
     * <p>This exists so a clinician can return to an encounter they started. The
     * only ways to reach an encounter were by id or by patient, so an open
     * encounter could not be found again unless the patient was already known.
     *
     * @param status  filter, or null for every encounter
     * @param mine    true to restrict to encounters this clinician started
     */
    public PageResponse<EncounterDTO> list(Encounter.Status status, boolean mine, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Encounter> result;
        if (status != null && mine) {
            result = encounterRepository.findByStatusAndDoctorIdOrderByCreatedAtDesc(
                    status, currentUserProvider.getCurrentUserId(), pageable);
        } else if (status != null) {
            result = encounterRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else if (mine) {
            result = encounterRepository.findByDoctorId(
                    currentUserProvider.getCurrentUserId(), pageable);
        } else {
            result = encounterRepository.findAll(pageable);
        }
        return toPageResponse(result, pageable);
    }

    private PageResponse<EncounterDTO> toPageResponse(Page<Encounter> page, Pageable pageable) {
        return PageResponse.of(
                new PageImpl<>(toEncounterDTOs(page.getContent()), pageable, page.getTotalElements()));
    }

    /**
     * Attaches the patient identity and the doctor's name to each encounter.
     *
     * <p>An encounter stores only ids, so every read path — the worklist, and the
     * patient profile's encounter history — rendered a raw UUID where a name
     * belongs, leaving no way to tell who had seen a patient. Names are resolved
     * in one query per page rather than per row, and are never persisted on the
     * encounter. A row whose patient or doctor has since been deleted still
     * maps, with a null name for the reader to fall back on.
     *
     * <p>Decryption of patient PII happens here, in the service, so the
     * controller stays free of PII concerns.
     */
    private List<EncounterDTO> toEncounterDTOs(List<Encounter> encounters) {
        Map<UUID, Patient> patients = new HashMap<>();
        List<UUID> patientIds = encounters.stream()
                .map(Encounter::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!patientIds.isEmpty()) {
            patientRepository.findAllById(patientIds)
                    .forEach(p -> patients.put(p.getId(), p));
        }

        Map<UUID, User> doctors = new HashMap<>();
        List<UUID> doctorIds = encounters.stream()
                .map(Encounter::getDoctorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!doctorIds.isEmpty()) {
            userRepository.findAllById(doctorIds)
                    .forEach(u -> doctors.put(u.getId(), u));
        }

        return encounters.stream().map(encounter -> {
            EncounterDTO dto = EntityDtoMapper.toDTO(encounter);
            Patient patient = patients.get(encounter.getPatientId());
            if (patient != null) {
                dto.setPatientName(patient.getName());
                dto.setPatientUhid(patient.getUhid());
            }
            User doctor = doctors.get(encounter.getDoctorId());
            if (doctor != null) {
                dto.setDoctorName(doctor.getFullName());
            }
            return dto;
        }).toList();
    }

    private EncounterDTO toEncounterDTO(Encounter encounter) {
        return toEncounterDTOs(List.of(encounter)).get(0);
    }

    // Backward compatibility
    public List<EncounterDTO> listByPatient(UUID patientId) {
        return toEncounterDTOs(encounterRepository.findByPatientIdOrderByCreatedAtDesc(patientId));
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
        return toEncounterDTO(saved);
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
        return toPrescriptionDTOs(List.of(saved)).get(0);
    }

    public List<PrescriptionDTO> listPrescriptions(UUID encounterId) {
        return toPrescriptionDTOs(prescriptionRepository.findByEncounterId(encounterId));
    }

    public List<PrescriptionDTO> pendingPrescriptions() {
        return toPrescriptionDTOs(prescriptionRepository.findByStatus(Prescription.Status.pending));
    }

    /**
     * Attaches the medicine name to each prescription.
     *
     * <p>A prescription stores only the medicine id, so without this the OPD
     * prescription list and the pharmacy pending queue both render a raw UUID
     * where the drug name belongs — a pharmacist cannot dispense from a UUID.
     * The medicines are fetched in one query per page rather than per row, and
     * a prescription whose medicine was deleted still maps, with a null name.
     */
    private List<PrescriptionDTO> toPrescriptionDTOs(List<Prescription> prescriptions) {
        Map<UUID, MedicineCatalog> medicines = new HashMap<>();
        List<UUID> ids = prescriptions.stream()
                .map(Prescription::getMedicineId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!ids.isEmpty()) {
            medicineCatalogRepository.findAllById(ids)
                    .forEach(m -> medicines.put(m.getId(), m));
        }

        return prescriptions.stream().map(rx -> {
            PrescriptionDTO dto = EntityDtoMapper.toDTO(rx);
            MedicineCatalog medicine = medicines.get(rx.getMedicineId());
            if (medicine != null) {
                dto.setMedicineName(medicine.getName());
                dto.setMedicineGenericName(medicine.getGenericName());
                dto.setMedicineUnit(medicine.getUnit());
            }
            return dto;
        }).toList();
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
