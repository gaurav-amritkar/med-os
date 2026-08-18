package com.medos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionDTO {
    private UUID id;
    private UUID encounterId;
    private UUID patientId;
    private UUID medicineId;
    private String dosage;
    private String frequency;
    private String duration;
    private String instructions;
    private String status;
    private UUID prescribedBy;
    private LocalDateTime prescribedAt;
}