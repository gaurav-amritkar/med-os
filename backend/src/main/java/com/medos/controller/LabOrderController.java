package com.medos.controller;

import com.medos.dto.CreateLabOrderRequest;
import com.medos.dto.LabOrderDTO;
import com.medos.dto.RecordLabResultRequest;
import com.medos.dto.UpdateLabOrderStatusRequest;
import com.medos.modules.clinical.service.LabOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Lab orders: a doctor orders a test from an encounter; a lab role updates
 * status through the lifecycle and records the result.
 *
 * <p>Role split:
 *   - DOCTOR / ADMIN: create an order
 *   - LAB / ADMIN:    update status, record a result
 *   - all authenticated: list orders (tenant-scoped)
 */
@RestController
@RequestMapping("/api/v1/lab-orders")
@RequiredArgsConstructor
public class LabOrderController {

    private final LabOrderService labOrderService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<LabOrderDTO> create(@Valid @RequestBody CreateLabOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(labOrderService.createLabOrder(request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LabOrderDTO>> list(
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID encounterId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(labOrderService.listLabOrders(patientId, encounterId, status));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('LAB','ADMIN')")
    public ResponseEntity<LabOrderDTO> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLabOrderStatusRequest request) {
        return ResponseEntity.ok(labOrderService.updateStatus(id, request));
    }

    @PatchMapping("/{id}/result")
    @PreAuthorize("hasAnyRole('LAB','ADMIN')")
    public ResponseEntity<LabOrderDTO> recordResult(
            @PathVariable UUID id,
            @Valid @RequestBody RecordLabResultRequest request) {
        return ResponseEntity.ok(labOrderService.recordResult(id, request));
    }
}
