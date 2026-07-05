package com.medos.dto;

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
}
