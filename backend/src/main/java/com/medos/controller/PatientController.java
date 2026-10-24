package com.medos.controller;

import com.medos.dto.PageResponse;
import com.medos.dto.PatientConsentRequest;
import com.medos.dto.PatientDTO;
import com.medos.dto.PatientRegistrationRequest;
import com.medos.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    public ResponseEntity<PatientDTO> registerPatient(@Valid @RequestBody PatientRegistrationRequest request) {
        PatientDTO patient = patientService.registerPatient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(patient);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<PatientDTO>> listPatients(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String bloodGroup,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(patientService.listPatients(search, type, bloodGroup, page, size));
    }

    /** The Hub counter rail: Total / New Today / Active OPD / IPD / Discharged. */
    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PatientService.HubSummary> hubSummary() {
        return ResponseEntity.ok(patientService.hubSummary());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PatientDTO> getPatient(@PathVariable UUID id) {
        return ResponseEntity.ok(patientService.getPatient(id));
    }

    @GetMapping("/uhid/{uhid}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PatientDTO> getByUhid(@PathVariable String uhid) {
        return ResponseEntity.ok(patientService.getByUhid(uhid));
    }

    /**
     * Captures a DPDP consent grant (or, with {@code granted=false}, a withdrawal)
     * after registration.
     *
     * <p>Front-desk roles only, matching registration: consent is captured in
     * conversation with the patient, and a clinician in the middle of an
     * encounter should not be the one recording it.
     */
    @PostMapping("/{id}/consent")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    public ResponseEntity<PatientDTO> recordConsent(
            @PathVariable UUID id,
            @Valid @RequestBody PatientConsentRequest request) {
        return ResponseEntity.ok(patientService.recordConsent(id, request));
    }

    /**
     * Withdraws consent, appending a {@code granted=false} event.
     *
     * <p>Kept as its own verb because withdrawal is a distinct act with its own
     * intent: a caller reaching for DELETE should not be able to accidentally
     * grant by omitting a field.
     */
    @DeleteMapping("/{id}/consent")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    public ResponseEntity<PatientDTO> withdrawConsent(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        PatientConsentRequest request = new PatientConsentRequest();
        request.setGranted(false);
        request.setPurpose(reason);
        return ResponseEntity.ok(patientService.recordConsent(id, request));
    }
}
