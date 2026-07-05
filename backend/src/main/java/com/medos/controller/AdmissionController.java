package com.medos.controller;

import com.medos.dto.AdmissionRequest;
import com.medos.dto.DischargeRequest;
import com.medos.entity.Admission;
import com.medos.entity.Room;
import com.medos.service.AdmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admissions")
@RequiredArgsConstructor
public class AdmissionController {

    private final AdmissionService admissionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','ADMIN')")
    public ResponseEntity<Admission> admitPatient(@Valid @RequestBody AdmissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admissionService.admitPatient(request));
    }

    @PutMapping("/{id}/discharge")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<Admission> dischargePatient(
            @PathVariable UUID id,
            @Valid @RequestBody DischargeRequest request) {
        return ResponseEntity.ok(admissionService.dischargePatient(id, request));
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Admission>> getActiveAdmissions() {
        return ResponseEntity.ok(admissionService.getActiveAdmissions());
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Admission>> getPatientHistory(@PathVariable UUID patientId) {
        return ResponseEntity.ok(admissionService.getPatientHistory(patientId));
    }

    @GetMapping("/rooms")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Room>> getAllRooms() {
        return ResponseEntity.ok(admissionService.getAllRooms());
    }

    @GetMapping("/rooms/available")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Room>> getAvailableRooms() {
        return ResponseEntity.ok(admissionService.getAvailableRooms());
    }
}
