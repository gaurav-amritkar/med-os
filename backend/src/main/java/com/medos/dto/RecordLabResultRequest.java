package com.medos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Recording a lab result. Only allowed once the order is collected or in_progress,
 * and only while the parent encounter is unsigned.
 */
public record RecordLabResultRequest(
        @NotBlank @Size(max = 4000) String result
) {
}
