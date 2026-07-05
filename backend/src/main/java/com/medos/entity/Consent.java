package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consent {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "patient_id", nullable = false, columnDefinition = "uuid")
    private UUID patientId;

    @Column(name = "consent_type", nullable = false, length = 64)
    private String consentType;

    @Column(nullable = false)
    private Boolean granted;

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @Column(name = "granted_by", length = 128)
    private String grantedBy;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @PrePersist
    protected void onCreate() {
        if (grantedAt == null) grantedAt = LocalDateTime.now();
    }
}
