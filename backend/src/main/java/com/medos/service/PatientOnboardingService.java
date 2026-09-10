package com.medos.service;

import com.medos.entity.Patient;
import com.medos.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PatientOnboardingService {
    private final PatientRepository patientRepository;

    public Patient onboardPatient(UUID tenantId, String name, Integer age, String gender,
                                  String phone, String email, String address, String bloodGroup,
                                  Boolean dpdpConsent) {
        Patient patient = Patient.builder()
                .tenantId(tenantId)
                .name(name)
                .age(age)
                .gender(gender)
                .phone(phone)
                .email(email)
                .address(address)
                .bloodGroup(bloodGroup)
                .dpdpConsent(dpdpConsent)
                .uhid(generateUhid(tenantId))
                .build();
        return patientRepository.save(patient);
    }

    public List<Patient> getPatientsByTenant(UUID tenantId) {
        return patientRepository.findByTenantId(tenantId);
    }

    public Optional<Patient> getPatientByUhid(UUID tenantId, String uhid) {
        return patientRepository.findByTenantIdAndUhid(tenantId, uhid);
    }

    private String generateUhid(UUID tenantId) {
        Long seq = patientRepository.getNextPatientSeq();
        return tenantId.toString().substring(0, 8) + "-" + String.format("%08d", seq);
    }
}