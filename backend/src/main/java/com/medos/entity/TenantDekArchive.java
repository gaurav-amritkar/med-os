package com.medos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An archived DEK generation, as stored by {@code V6__tenant_dek_history.sql}.
 *
 * <p>This entity exists mainly so the H2 test profile ({@code ddl-auto=create-drop})
 * creates the table: the rotation flow itself reads and writes history through the
 * JDBC seam ({@code TenantKeyStore}), never through the persistence context — key
 * material must not join a caller's flush cycle (same reason {@code tenant_keys} is
 * written through {@code TenantKeyStoreJdbc}).
 *
 * <p>A synthetic id is deliberately used over a composite key: the archive is
 * written and never updated, and insert idempotency is enforced by the unique
 * constraint, not by identity plumbing.
 */
@Entity
@Table(name = "tenant_dek_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tenant_dek_history_generation",
                columnNames = {"tenant_id", "dek_generation"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDekArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

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
}
