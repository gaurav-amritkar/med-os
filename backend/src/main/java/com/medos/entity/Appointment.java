package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@EntityListeners(TenantEntityListener.class)
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment implements TenantOwned {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false, columnDefinition = "uuid")
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false, columnDefinition = "uuid")
    private UUID doctorId;

    @Column(name = "appointment_date", nullable = false)
    private LocalDateTime appointmentDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Status status = Status.scheduled;

    @Column(columnDefinition = "TEXT")
    private String reason;

    /**
     * Per-tenant serial (V8), the board's Appt No. Assigned by the service as
     * (tenant max + 1); {@code uq_appointments_tenant_no} makes a concurrent
     * double-assign fail loudly so the desk retries, never reuses.
     */
    @Column(name = "appointment_no")
    private Integer appointmentNo;

    /**
     * Check-in token, issued the day the patient arrives. Per tenant AND per
     * day ({@code uq_appointments_tenant_day_token}): every morning the queue
     * restarts at 1 — a global token counter would make token 1 mean "whoever
     * booked first since time began".
     */
    @Column(name = "token_no")
    private Integer tokenNo;

    /** The day the token belongs to; pairs with {@link #tokenNo}. */
    @Column(name = "token_date")
    private LocalDate tokenDate;

    /** First visit or follow-up — the "Time & Type" column's type axis. */
    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_type", length = 32)
    private Type appointmentType;

    /** What the doctor wrote about this visit (V8 doc note). */
    @Column(columnDefinition = "TEXT")
    private String docNote;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = Status.scheduled;
        if (appointmentType == null) appointmentType = Type.first_visit;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Status {
        scheduled, checked_in, in_consultation, completed, cancelled, no_show
    }

    public enum Type {
        first_visit, follow_up
    }
}
