package com.medos.entity;

import com.medos.util.EncryptionUtil;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@EntityListeners(TenantEntityListener.class)
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient implements TenantOwned {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(unique = true, nullable = false, length = 32)
    private String uhid;

    @Column(name = "tenant_id", columnDefinition = "uuid", nullable = false)
    private UUID tenantId;

    /**
     * Keyed blind index of {@link #name} (see BlindIndexUtil). Deterministic so
     * the encrypted name can be matched by equality, since AES-GCM ciphertext
     * is randomised per value and cannot be searched. Not PII in the clear, but
     * as sensitive as the name it indexes.
     */
    @Column(name = "name_index", length = 64)
    private String nameIndex;

    @Column(nullable = false, columnDefinition = "TEXT") // TEXT: AES-GCM output exceeds varchar(128)
    @Convert(converter = EncryptionUtil.class)
    private String name;

    private Integer age;

    @Column(length = 16)
    private String gender;

    @Column(columnDefinition = "TEXT") // TEXT: Base64(IV+ct+tag) ~60 chars exceeds varchar(20)
    @Convert(converter = EncryptionUtil.class)
    private String phone;

    @Column(columnDefinition = "TEXT") // TEXT: AES-GCM output exceeds varchar(128)
    @Convert(converter = EncryptionUtil.class)
    private String email;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = EncryptionUtil.class)
    private String address;

    @Column(name = "blood_group", columnDefinition = "TEXT")
    @Convert(converter = EncryptionUtil.class)
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
