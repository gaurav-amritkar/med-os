package com.medos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionDTO {
    private UUID id;
    private Long version;
    private UUID patientId;
    private UUID roomId;
    private UUID doctorId;
    private LocalDateTime admissionDate;
    private LocalDateTime dischargeDate;
    private String status;
    private String dischargeDiagnosis;
    private BigDecimal roomCharges;
    private Integer daysAdmitted;
    private LocalDateTime createdAt;
}