package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "opd_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpdQueue {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "patient_id", nullable = false, columnDefinition = "uuid")
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false, columnDefinition = "uuid")
    private UUID doctorId;

    @Column(name = "queue_number", nullable = false)
    private Integer queueNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "queue_status", length = 32)
    private Status queueStatus = Status.waiting;

    @Column(name = "check_in_at")
    private LocalDateTime checkInAt;

    @Column(name = "called_at")
    private LocalDateTime calledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (checkInAt == null) checkInAt = LocalDateTime.now();
        if (queueStatus == null) queueStatus = Status.waiting;
    }

    public enum Status {
        waiting, in_consultation, completed, cancelled
    }
}
