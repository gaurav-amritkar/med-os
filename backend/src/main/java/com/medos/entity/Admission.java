package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "admissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Admission {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "patient_id", nullable = false, columnDefinition = "uuid")
    private UUID patientId;

    @Column(name = "room_id", nullable = false, columnDefinition = "uuid")
    private UUID roomId;

    @Column(name = "doctor_id", columnDefinition = "uuid")
    private UUID doctorId;

    @Column(name = "admission_date")
    private LocalDateTime admissionDate;

    @Column(name = "discharge_date")
    private LocalDateTime dischargeDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Status status = Status.admitted;

    @Column(name = "discharge_diagnosis", columnDefinition = "TEXT")
    private String dischargeDiagnosis;

    @Column(name = "room_charges", precision = 12, scale = 2)
    private BigDecimal roomCharges = BigDecimal.ZERO;

    @Column(name = "days_admitted")
    private Integer daysAdmitted = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (admissionDate == null) admissionDate = LocalDateTime.now();
        if (status == null) status = Status.admitted;
        if (roomCharges == null) roomCharges = BigDecimal.ZERO;
        if (daysAdmitted == null) daysAdmitted = 0;
    }

    public enum Status {
        admitted, discharged, transferred
    }
}
