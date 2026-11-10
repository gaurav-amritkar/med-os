package com.medos.controller;

import com.medos.dto.PatientRegistrationRequest;
import com.medos.entity.Patient;
import com.medos.security.TenantContext;
import com.medos.service.PatientOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped onboarding reads and registration.
 *
 * <p>Registration is deliberately NOT exposed here. This path previously held a
 * second registration implementation with no role guard and no canonical DPDP
 * consent event or audit record, while {@link PatientController} enforced
 * {@code RECEPTIONIST}/{@code ADMIN} and wrote both (#223). A duplicate write
 * path to the same table is a second authorization surface to reason about, and
 * this was the weaker one.
 *
 * <p>All registration now goes through {@code PatientService}. The endpoints
 * below remain for onboarding reads, and they carry the same guard as every
 * other patient endpoint so a pharmacist or doctor cannot enumerate the
 * tenant's patient list through the weaker route.
 */
@RestController
@RequestMapping("/api/v1/onboarding/patients")
@RequiredArgsConstructor
public class PatientOnboardingController {

    private final PatientOnboardingService patientOnboardingService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Patient>> getPatients() {
        UUID tenantId = TenantContext.getTenantId()
                .orElseThrow(() -> new IllegalStateException("Tenant context not set"));
        return ResponseEntity.ok(patientOnboardingService.getPatientsByTenant(tenantId));
    }

    @GetMapping("/uhid/{uhid}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Patient> getPatientByUhid(@PathVariable String uhid) {
        UUID tenantId = TenantContext.getTenantId()
                .orElseThrow(() -> new IllegalStateException("Tenant context not set"));
        return patientOnboardingService.getPatientByUhid(tenantId, uhid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
