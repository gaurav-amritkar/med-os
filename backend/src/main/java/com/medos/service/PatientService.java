package com.medos.service;

import com.medos.dto.PatientRegistrationRequest;
import com.medos.entity.Consent;
import com.medos.entity.Patient;
import com.medos.exception.BusinessException;
import com.medos.repository.ConsentRepository;
import com.medos.repository.PatientRepository;
import com.medos.util.AuditLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final ConsentRepository consentRepository;
    private final AuditLogger auditLogger;

    @Transactional
    public Patient registerPatient(PatientRegistrationRequest req) {
        if (!Boolean.TRUE.equals(req.getDpdpConsent())) {
            throw new BusinessException("DPDP consent is required for patient registration");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BusinessException("Patient name is required");
        }
        if (req.getAge() == null || req.getAge() < 0 || req.getAge() > 150) {
            throw new BusinessException("Invalid patient age (0-150)");
        }

        Patient patient = Patient.builder()
                .uhid(generateUhid())
                .name(req.getName().trim())
                .age(req.getAge())
                .gender(req.getGender())
                .phone(req.getPhone())
                .email(req.getEmail())
                .address(req.getAddress())
                .bloodGroup(req.getBloodGroup())
                .dpdpConsent(true)
                .dpdpConsentAt(LocalDateTime.now())
                .outstanding(java.math.BigDecimal.ZERO)
                .build();
        Patient saved = patientRepository.save(patient);

        Consent consent = Consent.builder()
                .patientId(saved.getId())
                .consentType("DPDP_DATA_PROCESSING")
                .granted(true)
                .grantedBy(saved.getName())
                .purpose(req.getConsentPurpose() != null ? req.getConsentPurpose() : "Treatment and billing")
                .build();
        consentRepository.save(consent);

        auditLogger.log("CREATE", "Patient", saved.getId().toString(),
                null, "UHID=" + saved.getUhid());

        return saved;
    }

    public List<Patient> listPatients(String search) {
        if (search != null && !search.isBlank()) {
            return patientRepository.findByNameContainingIgnoreCase(search);
        }
        return patientRepository.findAll();
    }

    public Patient getPatient(UUID id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new com.medos.exception.ResourceNotFoundException("Patient", id.toString()));
    }

    public Patient getByUhid(String uhid) {
        return patientRepository.findByUhid(uhid)
                .orElseThrow(() -> new com.medos.exception.ResourceNotFoundException("Patient UHID: " + uhid));
    }

    private String generateUhid() {
        Integer max = patientRepository.findMaxUhidSequence().orElse(0);
        return String.format("UHID%06d", (max == null ? 0 : max) + 1);
    }
}
