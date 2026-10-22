package com.medos.dto;

import com.medos.entity.Enquiry;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The Enquiry Desk board row: Enquiry #, Contact, Reason/Department, Status —
 * the MedWorks grammar, plus the audit trail's shape (createdAt, notes).
 */
public record EnquiryDTO(
        UUID id,
        Integer enquiryNo,
        String enquiryRef,
        String contactName,
        String contactPhone,
        String contactEmail,
        String type,
        String department,
        String reason,
        String status,
        String notes,
        UUID convertedPatientId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static EnquiryDTO from(Enquiry e) {
        return new EnquiryDTO(
                e.getId(),
                e.getEnquiryNo(),
                "ENQ-" + e.getEnquiryNo(),
                e.getContactName(),
                e.getContactPhone(),
                e.getContactEmail(),
                e.getEnquiryType() == null ? null : e.getEnquiryType().name(),
                e.getDepartment(),
                e.getReason(),
                e.getStatus() == null ? null : e.getStatus().name(),
                e.getNotes(),
                e.getConvertedPatientId(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
