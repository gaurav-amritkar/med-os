package com.medos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class DispenseRequest {
    @NotNull
    private UUID patientId;

    @NotNull
    private UUID prescriptionId;

    @NotNull
    @Min(1)
    private Integer quantity;

    private String notes;
}
