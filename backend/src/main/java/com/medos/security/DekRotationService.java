package com.medos.security;

import com.medos.entity.KeyRotation;
import com.medos.repository.KeyRotationRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The {@code dek_rotation} half of the key lifecycle (#86/#88 family; the KEK re-wrap
 * lives in {@link KeyRewrapService}). Generates new DEK <b>bytes</b> for one tenant and
 * moves its current DEK generation N → N+1, archiving generation N so ciphertext
 * labelled {@code kv1.d<N>:} stays readable (V6 {@code tenant_dek_history}).
 *
 * <p>Difference from a KEK re-wrap, which is why the two are separate operations:
 * a re-wrap changes only the wrapper (no patient data is rewritten, reversible by
 * re-running with the old KEK); a DEK rotation changes the data key itself — every
 * row written before it is protected by a key this tenant no longer uses, so the
 * audit row {@code key_rotations.operation = 'dek_rotation'} is the DPDP §8(5)
 * evidence that the change happened, when, and on whose instruction.
 *
 * <p>Not resumable in the re-wrap sense: a rotation is a single atomic-enough move
 * (archive → advance → replace) whose interrupted states stay decodable by
 * construction. Rows still encrypted at older generations decrypt through the
 * archive; the corridor backfill that rewrites them to the current generation is a
 * separate, per-tenant, resumable job (#86 follow-up).
 */
@Slf4j
public class DekRotationService {

    /** Outcome of a rotation. */
    public record Result(int fromGeneration, int toGeneration, Status status, String detail) {}

    public enum Status { COMPLETED, FAILED }

    private final TenantKeyResolver resolver;
    private final KeyRotationRepository rotations;

    public DekRotationService(TenantKeyResolver resolver, KeyRotationRepository rotations) {
        this.resolver = resolver;
        this.rotations = rotations;
    }

    /**
     * Rotate one tenant's DEK and record the run. The audit row is written even when
     * the rotation fails — a failed rotation is one of the events §8(5) evidence must
     * show, not something to log only to stdout.
     */
    public Result rotate(UUID tenantId, String initiatedBy) {
        int before = safeGeneration(tenantId);
        KeyRotation row = KeyRotation.builder()
                .operation("dek_rotation")
                .tenantId(tenantId)
                .startedAt(LocalDateTime.now())
                .status("in_progress")
                .initiatedBy(initiatedBy)
                .build();
        rotations.save(row);
        try {
            int toGeneration = resolver.rotateDek(tenantId);
            row.setDekGeneration(toGeneration);
            row.setStatus("completed");
            row.setCompletedAt(LocalDateTime.now());
            row.setRowsRewritten(0);
            rotations.save(row);
            log.info("DEK rotation for tenant {}: generation {} -> {}", tenantId, before, toGeneration);
            return new Result(before, toGeneration, Status.COMPLETED,
                    "DEK rotation completed; generation " + toGeneration
                            + " is now current; superseded ciphertext decrypts via the archive");
        } catch (RuntimeException e) {
            row.setStatus("failed");
            row.setCompletedAt(LocalDateTime.now());
            rotations.save(row);
            log.error("DEK rotation failed for tenant {}", tenantId, e);
            return new Result(before, before, Status.FAILED,
                    "aborted before state change: " + e.getMessage());
        }
    }

    private int safeGeneration(UUID tenantId) {
        try {
            return resolver.generationFor(tenantId);
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
