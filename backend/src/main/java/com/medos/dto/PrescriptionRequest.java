package com.medos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class PrescriptionRequest {
    @NotNull
    private UUID encounterId;

    @NotNull
    private UUID patientId;

    @NotNull
    private UUID medicineId;

    @NotBlank
    private String dosage;

    private String frequency;
    private String duration;
    private String instructions;

    // Total units prescribed; enables partial dispensing. Optional so legacy
    // prescriptions keep the previous behaviour.
    @Min(1)
    @Max(10000)
    private Integer quantity;
}
