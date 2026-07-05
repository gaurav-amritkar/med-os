package com.medos.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AdmissionRequest {
    @NotNull
    private UUID patientId;

    @NotNull
    private UUID roomId;

    private UUID doctorId;
}
