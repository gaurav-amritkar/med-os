package com.medos.controller;

import com.medos.dto.AppointmentDTO;
import com.medos.dto.BookAppointmentRequest;
import com.medos.dto.PageResponse;
import com.medos.dto.UpdateAppointmentStatusRequest;
import com.medos.modules.appointment.service.AppointmentService;
import com.medos.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * The Appointments board. All endpoints are scoped to the acting tenant via
 * the write-scoping inspector (G16/PR #182). Reads require any medical role;
 * writes (book / status / note / delete) require the doctor or admin role —
 * the desk staff don't move appointments, only the doctor starts/finishes.
 */
@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN','RECEPTIONIST')")
    public ResponseEntity<AppointmentDTO> book(@Valid @RequestBody BookAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointmentService.book(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN','RECEPTIONIST','NURSE')")
    public ResponseEntity<PageResponse<AppointmentDTO>> listToday(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String doctorId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(appointmentService.listToday(status, doctorId, type, page, size));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN','RECEPTIONIST','NURSE')")
    public ResponseEntity<AppointmentService.BoardSummary> summaryToday() {
        return ResponseEntity.ok(appointmentService.summaryToday());
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN','RECEPTIONIST')")
    public ResponseEntity<AppointmentDTO> checkIn(@PathVariable UUID id) {
        return ResponseEntity.ok(appointmentService.checkIn(id));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<AppointmentDTO> updateStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return ResponseEntity.ok(appointmentService.updateStatus(id, request));
    }

    @PostMapping("/{id}/note")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<AppointmentDTO> writeNote(@PathVariable UUID id,
                                                    @Valid @RequestBody String note) {
        return ResponseEntity.ok(appointmentService.writeNote(id, new com.medos.dto.AppointmentNoteRequest(note)));
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN','RECEPTIONIST','NURSE')")
    public ResponseEntity<PageResponse<AppointmentDTO>> queueToday(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(appointmentService.queueToday(status, page, size));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<Void> deleteTerminal(@PathVariable UUID id) {
        appointmentService.deleteTerminal(id);
        return ResponseEntity.noContent().build();
    }
}
