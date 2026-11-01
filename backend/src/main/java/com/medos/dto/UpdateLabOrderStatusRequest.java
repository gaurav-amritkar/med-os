package com.medos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Moving a lab order through its lifecycle: ordered → collected → in_progress → completed,
 * or ordered → cancelled. A result may only be recorded once collected or in_progress.
 */
public record UpdateLabOrderStatusRequest(
        @NotBlank @Pattern(regexp = "ordered|collected|in_progress|completed|cancelled") String status
) {
}
