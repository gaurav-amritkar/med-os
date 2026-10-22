package com.medos.controller;

import com.medos.dto.CreateEnquiryRequest;
import com.medos.dto.EnquiryDTO;
import com.medos.dto.PageResponse;
import com.medos.dto.UpdateEnquiryStatusRequest;
import com.medos.entity.EnquiryStatusHistory;
import com.medos.modules.enquiry.service.EnquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The Enquiry Desk: the receptionist logs contacts before they become patients.
 * Writes are front-desk authority (receptionist/admin); the board is readable by
 * the clinical roles too, since the enquiry may turn into an appointment they run.
 */
@RestController
@RequestMapping("/api/v1/enquiries")
@RequiredArgsConstructor
public class EnquiryController {

    private final EnquiryService enquiryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    public ResponseEntity<EnquiryDTO> logEnquiry(@Valid @RequestBody CreateEnquiryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(enquiryService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<PageResponse<EnquiryDTO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(enquiryService.list(status, type, page, size));
    }

    /** The board's counter rail: Total / New / Follow-up / Converted / Cancelled. */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<EnquiryService.DeskSummary> summary() {
        return ResponseEntity.ok(enquiryService.summary());
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<List<EnquiryStatusHistory>> history(@PathVariable UUID id) {
        return ResponseEntity.ok(enquiryService.history(id));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    public ResponseEntity<EnquiryDTO> updateStatus(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateEnquiryStatusRequest request) {
        return ResponseEntity.ok(enquiryService.updateStatus(id, request));
    }
}
