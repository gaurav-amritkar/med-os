package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lab_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabOrder {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "encounter_id", columnDefinition = "uuid")
    private UUID encounterId;

    @Column(name = "patient_id", nullable = false, columnDefinition = "uuid")
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false, columnDefinition = "uuid")
    private UUID doctorId;

    @Column(name = "test_name", nullable = false, length = 128)
    private String testName;

    @Column(name = "test_code", length = 64)
    private String testCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Priority priority = Priority.normal;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Status status = Status.ordered;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(name = "result_at")
    private LocalDateTime resultAt;

    @Column(name = "ordered_at")
    private LocalDateTime orderedAt;

    @PrePersist
    protected void onCreate() {
        if (orderedAt == null) orderedAt = LocalDateTime.now();
        if (priority == null) priority = Priority.normal;
        if (status == null) status = Status.ordered;
    }

    public enum Priority {
        normal, urgent, stat
    }

    public enum Status {
        ordered, collected, in_progress, completed, cancelled
    }
}
