package com.medos.controller;

import com.medos.dto.PatientRegistrationRequest;
import com.medos.entity.Patient;
import com.medos.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    public ResponseEntity<Patient> registerPatient(@Valid @RequestBody PatientRegistrationRequest request) {
        Patient patient = patientService.registerPatient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(patient);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Patient>> listPatients(
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(patientService.listPatients(search));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Patient> getPatient(@PathVariable UUID id) {
        return ResponseEntity.ok(patientService.getPatient(id));
    }

    @GetMapping("/uhid/{uhid}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Patient> getByUhid(@PathVariable String uhid) {
        return ResponseEntity.ok(patientService.getByUhid(uhid));
    }
}
