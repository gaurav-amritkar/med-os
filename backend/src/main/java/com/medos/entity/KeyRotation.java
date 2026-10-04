package com.medos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One run of a key rotation, recorded for operations and as DPDP §8(5) evidence.
 *
 * <p>{@code operation} distinguishes {@code kek_rewrap} (re-wraps DEKs, touches no patient
 * data, reversible) from {@code dek_rotation} (re-encrypts one tenant's ciphertext, not
 * reversible). A re-wrap interrupted mid-run stays {@code in_progress} and is completed by
 * re-running it.
 */
@Entity
@Table(name = "key_rotations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyRotation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 32)
    private String operation;

    /** KEK version installed by a re-wrap, or the DEK generation a rotation moved to. */
    @Column(name = "kek_version")
    private Integer kekVersion;

    @Column(name = "dek_generation")
    private Integer dekGeneration;

    /** NULL for a fleet-wide re-wrap; set for a tenant-scoped DEK rotation. */
    @Column(name = "tenant_id", columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "rows_rewritten")
    private Integer rowsRewritten;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "initiated_by", length = 255)
    private String initiatedBy;
}
