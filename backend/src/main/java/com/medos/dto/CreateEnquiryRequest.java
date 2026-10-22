package com.medos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Logging a walk-in/phone enquiry from the front desk. Contact fields are
 * business-card data; {@code type} and {@code department} feed the desk's
 * filter grammar (All Statuses / All Types).
 */
public record CreateEnquiryRequest(
        @NotBlank @Size(max = 120) String contactName,
        @Size(max = 32) String contactPhone,
        @Size(max = 120) String contactEmail,
        @NotBlank @Pattern(regexp = "general|appointment|admission|emergency|pharmacy|other") String type,
        @Size(max = 64) String department,
        @NotBlank @Size(max = 2000) String reason,
        @Size(max = 2000) String notes
) {
}
