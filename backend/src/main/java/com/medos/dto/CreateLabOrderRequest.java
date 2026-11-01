package com.medos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Ordering a lab test from an encounter. The encounter must exist, belong to
 * the acting tenant, and not be signed.
 */
public record CreateLabOrderRequest(
        @NotNull UUID encounterId,
        @NotNull UUID patientId,
        @NotBlank @Size(max = 128) String testName,
        @Size(max = 64) String testCode,
        /** normal | urgent | stat — defaults to normal if absent. */
        String priority
) {
}
