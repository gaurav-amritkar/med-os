package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(unique = true, nullable = false, length = 32)
    private String uhid;

    @Column(nullable = false, length = 128)
    private String name;

    private Integer age;

    @Column(length = 16)
    private String gender;

    @Column(length = 20)
    private String phone;

    @Column(length = 128)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "blood_group", length = 8)
    private String bloodGroup;

    @Column(name = "dpdp_consent", nullable = false)
    private Boolean dpdpConsent = false;

    @Column(name = "dpdp_consent_at")
    private LocalDateTime dpdpConsentAt;

    @Column(precision = 12, scale = 2)
    private BigDecimal outstanding = BigDecimal.ZERO;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (dpdpConsent == null) dpdpConsent = false;
        if (outstanding == null) outstanding = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
