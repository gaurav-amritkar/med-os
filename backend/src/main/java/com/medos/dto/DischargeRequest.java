package com.medos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class DischargeRequest {
    private UUID admissionId;
    private UUID patientId;
    private UUID dischargingClinicianId;
    private String dischargingClinicianName;
    private String admissionDiagnosis;
    @NotBlank
    private String dischargeDiagnosis;
    private String clinicalCourse;
    private String proceduresPerformed;
    private String investigationsSummary;
    private String vitalsSummary;
    private String medicationsAtDischarge;
    private String conditionAtDischarge;
    private String followUpAdvice;
    private LocalDate nextReviewDate;
    private String notes;
}
