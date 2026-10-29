package com.medos.entity;

import com.medos.util.EncryptionUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@EntityListeners(TenantEntityListener.class)
@Table(name = "discharge_summaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DischargeSummary implements TenantOwned {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "admission_id", nullable = false, columnDefinition = "uuid")
    private UUID admissionId;

    @Column(name = "patient_id", nullable = false, columnDefinition = "uuid")
    private UUID patientId;

    @Column(name = "discharging_clinician_id", nullable = false, columnDefinition = "uuid")
    private UUID dischargingClinicianId;

    @Column(name = "discharging_clinician_name", nullable = false, length = 128)
    private String dischargingClinicianName;

    @Convert(converter = EncryptionUtil.class)
    @Column(name = "admission_diagnosis", nullable = false, columnDefinition = "TEXT")
    private String admissionDiagnosis;

    @Convert(converter = EncryptionUtil.class)
    @Column(name = "discharge_diagnosis", nullable = false, columnDefinition = "TEXT")
    private String dischargeDiagnosis;

    @Convert(converter = EncryptionUtil.class)
    @Column(name = "clinical_course", nullable = false, columnDefinition = "TEXT")
    private String clinicalCourse;

    @Convert(converter = EncryptionUtil.class)
    @Column(name = "procedures_performed", nullable = false, columnDefinition = "TEXT")
    private String proceduresPerformed;

    @Convert(converter = EncryptionUtil.class)
    @Column(name = "investigations_summary", nullable = false, columnDefinition = "TEXT")
    private String investigationsSummary;

    @Convert(converter = EncryptionUtil.class)
    @Column(name = "vitals_summary", nullable = false, columnDefinition = "TEXT")
    private String vitalsSummary;

    @Convert(converter = EncryptionUtil.class)
    @Column(name = "medications_at_discharge", nullable = false, columnDefinition = "TEXT")
    private String medicationsAtDischarge;

    @Convert(converter = EncryptionUtil.class)
    @Column(name = "condition_at_discharge", nullable = false, columnDefinition = "TEXT")
    private String conditionAtDischarge;

    @Convert(converter = EncryptionUtil.class)
    @Column(name = "follow_up_advice", nullable = false, columnDefinition = "TEXT")
    private String followUpAdvice;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt;

    @PrePersist
    void onCreate() {
        if (signedAt == null) signedAt = LocalDateTime.now();
    }
}
