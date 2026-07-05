package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "encounters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Encounter {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "patient_id", nullable = false, columnDefinition = "uuid")
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false, columnDefinition = "uuid")
    private UUID doctorId;

    @Column(name = "appointment_id", columnDefinition = "uuid")
    private UUID appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Status status = Status.open;

    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    private String chiefComplaint;

    @Column(columnDefinition = "TEXT")
    private String diagnosis;

    @Column(name = "clinical_notes", columnDefinition = "TEXT")
    private String clinicalNotes;

    @Column(name = "vitals_json", columnDefinition = "TEXT")
    private String vitalsJson;

    @Column(name = "ai_note", columnDefinition = "TEXT")
    private String aiNote;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "signed_by", columnDefinition = "uuid")
    private UUID signedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = Status.open;
    }

    public enum Status {
        open, signed, cancelled
    }
}
