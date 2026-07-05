package com.medos.controller;

import com.medos.dto.AiSuggestRequest;
import com.medos.dto.EncounterRequest;
import com.medos.dto.MedicineSuggestion;
import com.medos.dto.PrescriptionRequest;
import com.medos.entity.Encounter;
import com.medos.entity.Prescription;
import com.medos.service.AiMedicineService;
import com.medos.service.EncouterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/encounters")
@RequiredArgsConstructor
public class EncounterController {

    private final EncouterService encouterService;
    private final AiMedicineService aiMedicineService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','ADMIN')")
    public ResponseEntity<Encounter> createEncounter(@Valid @RequestBody EncounterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(encouterService.createEncounter(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Encounter> getEncounter(@PathVariable UUID id) {
        return ResponseEntity.ok(encouterService.getEncounter(id));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Encounter>> listByPatient(@PathVariable UUID patientId) {
        return ResponseEntity.ok(encouterService.listByPatient(patientId));
    }

    @PostMapping("/{id}/sign")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<Encounter> signEncounter(@PathVariable UUID id) {
        return ResponseEntity.ok(encouterService.signEncounter(id));
    }

    @PostMapping("/{id}/prescriptions")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<Prescription> addPrescription(@Valid @RequestBody PrescriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(encouterService.addPrescription(request));
    }

    @GetMapping("/{id}/prescriptions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Prescription>> listPrescriptions(@PathVariable UUID id) {
        return ResponseEntity.ok(encouterService.listPrescriptions(id));
    }

    @GetMapping("/prescriptions/pending")
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public ResponseEntity<List<Prescription>> pendingPrescriptions() {
        return ResponseEntity.ok(encouterService.pendingPrescriptions());
    }

    @PostMapping("/suggest-medicines")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<List<MedicineSuggestion>> suggestMedicines(@RequestBody AiSuggestRequest request) {
        return ResponseEntity.ok(aiMedicineService.suggestMedicines(request));
    }
}
