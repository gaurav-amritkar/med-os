package com.medos.controller;

import com.medos.dto.PatientRegistrationRequest;
import com.medos.entity.Patient;
import com.medos.security.TenantContext;
import com.medos.service.PatientOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/onboarding/patients")
@RequiredArgsConstructor
public class PatientOnboardingController {
    private final PatientOnboardingService patientOnboardingService;

    @PostMapping
    public ResponseEntity<Patient> registerPatient(@Valid @RequestBody PatientRegistrationRequest request) {
        UUID tenantId = TenantContext.getTenantId()
                .orElseThrow(() -> new IllegalStateException("Tenant context not set"));
        Patient patient = patientOnboardingService.onboardPatient(
                tenantId,
                request.getName(),
                request.getAge(),
                request.getGender(),
                request.getPhone(),
                request.getEmail(),
                request.getAddress(),
                request.getBloodGroup(),
                request.getDpdpConsent()
        );
        return ResponseEntity.ok(patient);
    }

    @GetMapping
    public ResponseEntity<List<Patient>> getPatients() {
        UUID tenantId = TenantContext.getTenantId()
                .orElseThrow(() -> new IllegalStateException("Tenant context not set"));
        return ResponseEntity.ok(patientOnboardingService.getPatientsByTenant(tenantId));
    }

    @GetMapping("/uhid/{uhid}")
    public ResponseEntity<Patient> getPatientByUhid(@PathVariable String uhid) {
        UUID tenantId = TenantContext.getTenantId()
                .orElseThrow(() -> new IllegalStateException("Tenant context not set"));
        return patientOnboardingService.getPatientByUhid(tenantId, uhid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}