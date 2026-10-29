package com.medos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.medos.util.EncryptionUtil;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@EntityListeners(TenantEntityListener.class)
@Table(name = "discharge_summary_addenda")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DischargeSummaryAddendum implements TenantOwned {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "discharge_summary_id", nullable = false, columnDefinition = "uuid")
    private UUID dischargeSummaryId;

    @Convert(converter = EncryptionUtil.class)
    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "authored_by", nullable = false, columnDefinition = "uuid")
    private UUID authoredBy;

    @Column(name = "author_name", nullable = false, length = 128)
    private String authorName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}