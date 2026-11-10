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

    // Registration is not offered here. This service previously had a second
    // registration implementation, reachable with no role guard, that wrote the
    // patient row without the canonical DPDP consent event or the registration
    // audit entry. It is removed (#223); registration goes through
    // PatientService, which enforces RECEPTIONIST/ADMIN and writes both.

    public List<Patient> getPatientsByTenant(UUID tenantId) {
        return patientRepository.findByTenantId(tenantId);
    }

    public Optional<Patient> getPatientByUhid(UUID tenantId, String uhid) {
        return patientRepository.findByTenantIdAndUhid(tenantId, uhid);
    }
}