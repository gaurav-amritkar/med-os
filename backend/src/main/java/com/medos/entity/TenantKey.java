package com.medos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A tenant's key material, as stored by {@code V2__pii_key_lifecycle.sql}.
 *
 * <p>{@code wrappedDek} is AES-GCM(kek, dek) and is the only form that ever touches
 * the database. There is deliberately no field for a plaintext DEK, so there is
 * nothing for a future change to accidentally persist.
 *
 * <p>{@code wrappedBiKey} is the blind-index key, a separate wrapped key with its
 * own generation. It is independent so name search can be held steady while the
 * data key rotates, or rotated separately if it is suspected.
 */
@Entity
@Table(name = "tenant_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantKey {

    @Id
    @Column(name = "tenant_id")
    private java.util.UUID tenantId;

    /** AES-GCM(kek, dek). Never a plaintext key. */
    @Column(name = "wrapped_dek", nullable = false)
    private byte[] wrappedDek;

    /** AES-GCM(kek, blindIndexKey). Null until the independent index key lands (#87). */
    @Column(name = "wrapped_bi_key")
    private byte[] wrappedBiKey;

    @Column(name = "dek_generation", nullable = false)
    private Integer dekGeneration;

    @Column(name = "bi_key_generation", nullable = false)
    private Integer biKeyGeneration;

    /**
     * Which KEK version currently wraps the columns above. Without it a re-wrap
     * cannot identify rows it has already finished, so it could not resume.
     */
    @Column(name = "wrapped_kek_version", nullable = false)
    private Integer wrappedKekVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Tenant tenant;
}
