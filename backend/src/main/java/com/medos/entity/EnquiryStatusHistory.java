package com.medos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One audited transition on an enquiry's lifecycle. A NULL {@code fromStatus}
 * marks the creation row; every later row says what the desk moved it to, when,
 * and who said so. This is what lets the board answer "who promised to call
 * back?" from the record instead of memory — the desk never edits history.
 */
@Entity
@EntityListeners(TenantEntityListener.class)
@Table(name = "enquiry_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnquiryStatusHistory implements TenantOwned {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", columnDefinition = "uuid")
    private UUID tenantId;

    /** Soft reference to the enquiry (V7 FK, ON DELETE CASCADE). */
    @Column(name = "enquiry_id", nullable = false, columnDefinition = "uuid")
    private UUID enquiryId;

    /** Null on the creation row. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private Enquiry.EnquiryStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private Enquiry.EnquiryStatus toStatus;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "changed_by", columnDefinition = "uuid")
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) {
            changedAt = LocalDateTime.now();
        }
    }
}
