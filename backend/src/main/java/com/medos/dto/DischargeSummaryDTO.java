package com.medos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DischargeSummaryDTO {
    private UUID id;
    private UUID admissionId;
    private UUID patientId;
    private UUID dischargingClinicianId;
    private String dischargingClinicianName;
    private String admissionDiagnosis;
    private String dischargeDiagnosis;
    private String clinicalCourse;
    private String proceduresPerformed;
    private String investigationsSummary;
    private String vitalsSummary;
    private String medicationsAtDischarge;
    private String conditionAtDischarge;
    private String followUpAdvice;
    private LocalDate nextReviewDate;
    private LocalDateTime signedAt;
    @Builder.Default
    private List<DischargeSummaryAddendumDTO> addenda = List.of();
}
