package com.medos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An archived DEK generation, as stored by {@code V6__tenant_dek_history.sql}.
 *
 * <p>This entity exists mainly so the H2 test profile ({@code ddl-auto=create-drop})
 * creates the table, and so prod's {@code ddl-auto=validate} proves the mapping and
 * the migration agree. The rotation flow itself reads and writes history through the
 * JDBC seam ({@code TenantKeyStore}), never through the persistence context — key
 * material must not join a caller's flush cycle (same reason {@code tenant_keys} is
 * written through {@code TenantKeyStoreJdbc}).
 *
 * <p>The identity is the migration's natural key — (tenant_id, dek_generation) via
 * an {@link IdClass}, matching V6's PRIMARY KEY exactly. A synthetic id column here
 * would disagree with the migration and fail schema validation at startup (that was
 * the first thing the rebuilt container proved).
 */
@Entity
@Table(name = "tenant_dek_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(TenantDekArchive.Identity.class)
public class TenantDekArchive {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "dek_generation", nullable = false)
    private Integer dekGeneration;

    /** AES-GCM(kek, dek of this generation). Never a plaintext key. */
    @Column(name = "wrapped_dek", nullable = false)
    private byte[] wrappedDek;

    /** KEK version that wrapped this material when it was archived. */
    @Column(name = "wrapped_kek_version", nullable = false)
    private Integer wrappedKekVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** The natural composite key V6 defines: one tenant, one generation, one row. */
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @Data
    public static class Identity implements Serializable {
        private UUID tenantId;
        private Integer dekGeneration;
    }
}
