package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "prescriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prescription {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "encounter_id", nullable = false, columnDefinition = "uuid")
    private UUID encounterId;

    @Column(name = "patient_id", nullable = false, columnDefinition = "uuid")
    private UUID patientId;

    @Column(name = "medicine_id", nullable = false, columnDefinition = "uuid")
    private UUID medicineId;

    @Column(length = 64)
    private String dosage;

    @Column(length = 64)
    private String frequency;

    @Column(length = 64)
    private String duration;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Status status = Status.pending;

    @Column(name = "prescribed_by", nullable = false, columnDefinition = "uuid")
    private UUID prescribedBy;

    @Column(name = "prescribed_at")
    private LocalDateTime prescribedAt;

    @PrePersist
    protected void onCreate() {
        if (prescribedAt == null) prescribedAt = LocalDateTime.now();
        if (status == null) status = Status.pending;
    }

    public enum Status {
        pending, dispensed, partially_dispensed, cancelled
    }
}
