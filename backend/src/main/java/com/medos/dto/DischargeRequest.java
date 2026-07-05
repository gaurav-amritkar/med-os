package com.medos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DischargeRequest {
    @NotBlank
    private String dischargeDiagnosis;
    private String notes;
}
