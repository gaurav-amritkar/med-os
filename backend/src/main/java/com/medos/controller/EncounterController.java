package com.medos.controller;

import com.medos.dto.EncounterDTO;
import com.medos.dto.PageResponse;
import com.medos.dto.AiSuggestRequest;
import com.medos.dto.EncounterRequest;
import com.medos.dto.MedicineSuggestion;
import com.medos.dto.PrescriptionDTO;
import com.medos.dto.PrescriptionRequest;
import com.medos.service.AiMedicineService;
import com.medos.service.EncounterService;
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

    private final EncounterService encounterService;
    private final AiMedicineService aiMedicineService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','ADMIN')")
    public ResponseEntity<EncounterDTO> createEncounter(@Valid @RequestBody EncounterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(encounterService.createEncounter(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EncounterDTO> getEncounter(@PathVariable UUID id) {
        return ResponseEntity.ok(encounterService.getEncounter(id));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<EncounterDTO>> listByPatient(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(encounterService.listByPatient(patientId, page, size));
    }

    @PostMapping("/{id}/sign")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<EncounterDTO> signEncounter(@PathVariable UUID id) {
        return ResponseEntity.ok(encounterService.signEncounter(id));
    }

    @PostMapping("/{id}/prescriptions")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<PrescriptionDTO> addPrescription(@Valid @RequestBody PrescriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(encounterService.addPrescription(request));
    }

    @GetMapping("/{id}/prescriptions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PrescriptionDTO>> listPrescriptions(@PathVariable UUID id) {
        return ResponseEntity.ok(encounterService.listPrescriptions(id));
    }

    @GetMapping("/prescriptions/pending")
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public ResponseEntity<List<PrescriptionDTO>> pendingPrescriptions() {
        return ResponseEntity.ok(encounterService.pendingPrescriptions());
    }

    @PostMapping("/suggest-medicines")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<List<MedicineSuggestion>> suggestMedicines(@RequestBody AiSuggestRequest request) {
        return ResponseEntity.ok(aiMedicineService.suggestMedicines(request));
    }
}
