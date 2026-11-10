package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@EntityListeners(TenantEntityListener.class)
@Table(name = "lab_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabOrder implements TenantOwned {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", columnDefinition = "uuid")
    private UUID tenantId;

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

    /**
     * The recorded result. Clinical content, so it is encrypted at rest like
     * every other clinical free-text column (#237).
     *
     * <p>Existing plaintext rows are readable: {@link EncryptionUtil}'s decrypt
     * path accepts a value with no version prefix as legacy plaintext and
     * returns it unchanged, so the upgrade does not require a stop-the-world
     * backfill before the new build starts. Rows are converted to ciphertext on
     * the next write, and V17 records which ones were backfilled.
     */
    @Convert(converter = com.medos.util.EncryptionUtil.class)
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
