package com.medos.security;

import com.medos.entity.KeyRotation;
import com.medos.repository.KeyRotationRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The {@code dek_rotation} half of the key lifecycle (issues #86/#88/#91; the KEK re-wrap
 * lives in {@link KeyRewrapService}).
 *
 * <p>A KEK re-wrap is cheap and reversible: it moves only the wrapper, so restoring the
 * old KEK file undoes it. A DEK rotation is the incident response for a suspected
 * compromise of the data key itself. It generates new DEK bytes for <b>one tenant</b>,
 * rewrites every ciphertext value that names the old generation, verifies the result,
 * and only then destroys the old wrapped key. Restoring a key file cannot undo it — a
 * verified backup is mandatory and is recorded on the audit row before anything changes.
 *
 * <p>The two operations are deliberately separate code paths. Conflating them is how
 * "rotate the key" turns into an outage: the routine action must stay online and
 * reversible, while the rare action must be slow, resumable, and loudly evidenced.
 *
 * <p>Failure states are part of the contract:
 * <ul>
 *   <li>{@code in_progress} — the audit row was written and the run may have started.
 *       A rerun resumes it rather than rotating again.</li>
 *   <li>{@code reencryption_incomplete} — the new generation is installed, but not every
 *       row was rewritten or verified. All data is still readable through the archive;
 *       rerun to continue. The old key is <b>not</b> destroyed in this state.</li>
 *   <li>{@code failed} — the rotation could not be applied at all. The old generation is
 *       still current.</li>
 *   <li>{@code completed} — every value was read back at the target generation and the
 *       old wrapped key was destroyed.</li>
 * </ul>
 */
@Slf4j
public class DekRotationService {

    /** Statuses that mean "an operator should rerun the same command". */
    private static final List<String> RESUMABLE = List.of("in_progress", "reencryption_incomplete");

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

    private static final int DEFAULT_BATCH_SIZE = 500;

    /**
     * A verified pre-rotation backup. Mandatory because the operation rewrites
     * ciphertext: if the run is wrong, the only recovery is restoring this backup.
     *
     * @param reference  human-readable restore point (snapshot id, backup path, PITR mark)
     * @param sha256     checksum of the backup artifact, 64 hex characters
     * @param verifiedAt when the operator proved the backup is restorable. A timestamp
     *                   in the future is refused as a transcription error.
     */
    public record BackupAttestation(String reference, String sha256, LocalDateTime verifiedAt) {

        public void validate() {
            if (reference == null || reference.isBlank()) {
                throw new IllegalArgumentException(
                        "A verified backup is mandatory before a DEK rotation. Refusing to rewrite "
                                + "ciphertext that cannot be recovered. Set MEDOS_KEY_ROTATION_BACKUP_REF.");
            }
            if (sha256 == null || !SHA256_HEX.matcher(sha256.trim()).matches()) {
                throw new IllegalArgumentException(
                        "The backup checksum must be exactly 64 hexadecimal characters (SHA-256). "
                                + "Set MEDOS_KEY_ROTATION_BACKUP_SHA256.");
            }
            if (verifiedAt == null) {
                throw new IllegalArgumentException(
                        "The backup verification time is mandatory: an unverified backup is not a "
                                + "restore point. Set MEDOS_KEY_ROTATION_BACKUP_VERIFIED_AT (ISO-8601).");
            }
            if (verifiedAt.isAfter(LocalDateTime.now().plusMinutes(5))) {
                throw new IllegalArgumentException(
                        "The backup verification time is in the future (" + verifiedAt
                                + "); refusing to treat it as evidence.");
            }
        }
    }

    /** Outcome of a rotation. */
    public record Result(int fromGeneration, int toGeneration, Status status,
                         String detail, int rowsRewritten) {
    }

    public enum Status { COMPLETED, INCOMPLETE, FAILED }

    private final TenantKeyResolver resolver;
    private final KeyRotationRepository rotations;
    private final DekReencryptor reencryptor;
    private final int batchSize;

    public DekRotationService(TenantKeyResolver resolver, KeyRotationRepository rotations,
                              DekReencryptor reencryptor) {
        this(resolver, rotations, reencryptor, DEFAULT_BATCH_SIZE);
    }

    public DekRotationService(TenantKeyResolver resolver, KeyRotationRepository rotations,
                              DekReencryptor reencryptor, int batchSize) {
        if (reencryptor == null) {
            throw new IllegalArgumentException("A DEK rotation needs a re-encryptor");
        }
        this.resolver = resolver;
        this.rotations = rotations;
        this.reencryptor = reencryptor;
        this.batchSize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
    }

    /**
     * Rotate one tenant's DEK, re-encrypt its data, verify, and destroy the old key.
     *
     * @param tenantId    exactly one tenant; never a wildcard
     * @param initiatedBy the operator, recorded as DPDP §8(5) evidence
     * @param backup      a verified pre-rotation restore point
     * @param rotateBiKey also rotate the name blind-index key and rebuild
     *                    {@code patients.name_index}
     */
    public Result rotate(UUID tenantId, String initiatedBy, BackupAttestation backup,
                         boolean rotateBiKey) {
        backup.validate();
        if (initiatedBy == null || initiatedBy.isBlank()) {
            throw new IllegalArgumentException(
                    "A DEK rotation requires --initiated-by=<who>; an unnamed operator is no "
                            + "evidence for a data-key change.");
        }

        Optional<KeyRotation> resumable = findResumable(tenantId);
        KeyRotation row = resumable.orElse(null);

        int fromGeneration;
        int toGeneration;
        if (row != null) {
            if (row.getDekGeneration() == null) {
                throw new IllegalStateException("The unfinished DEK rotation " + row.getId()
                        + " has no target generation and cannot be resumed");
            }
            toGeneration = row.getDekGeneration();
            fromGeneration = toGeneration - 1;
            int current = resolver.generationFor(tenantId);
            if (current > toGeneration) {
                throw new IllegalStateException("Tenant " + tenantId + " is already on DEK generation "
                        + current + ", ahead of the unfinished rotation's target " + toGeneration
                        + ". Refusing to resume: another rotation overtook this run.");
            }
            if (current < fromGeneration) {
                throw new IllegalStateException("Tenant " + tenantId + " is on DEK generation "
                        + current + ", behind the unfinished rotation's source " + fromGeneration
                        + ". Refusing to resume an impossible state.");
            }
            log.info("Resuming unfinished DEK rotation {} for tenant {}: generation {} -> {}",
                    row.getId(), tenantId, fromGeneration, toGeneration);
        } else {
            int before;
            try {
                before = resolver.generationFor(tenantId);
            } catch (RuntimeException e) {
                // A tenant with no key at all is rotated into generation 1, which is the
                // same state the primitive path produced. There is nothing to archive.
                before = 0;
            }
            fromGeneration = before;
            toGeneration = before == 0 ? 1 : before + 1;

            row = KeyRotation.builder()
                    .operation("dek_rotation")
                    .tenantId(tenantId)
                    .dekGeneration(toGeneration)
                    .startedAt(LocalDateTime.now())
                    .status("in_progress")
                    .initiatedBy(initiatedBy)
                    .backupRef(backup.reference())
                    // Set before the key is touched, so a resumed run knows the index
                    // rebuild is still owed even if it crashed before rotating it.
                    .biKeyRotated(rotateBiKey)
                    .rowsRewritten(0)
                    .build();
            rotations.save(row);
        }

        boolean rebuildIndex = rotateBiKey || Boolean.TRUE.equals(row.getBiKeyRotated());

        try {
            int current = resolver.generationFor(tenantId);
            if (current != toGeneration) {
                int actual = resolver.rotateDek(tenantId);
                if (actual != toGeneration) {
                    throw new IllegalStateException("Expected DEK generation " + toGeneration
                            + " after rotation, but the store reports " + actual);
                }
            }
            if (rebuildIndex) {
                // On a resumed run this may rotate the index key a second time; that is
                // safe because the rebuild below converges on whatever key is current,
                // and it is preferable to leaving a requested rotation unapplied.
                resolver.rotateBlindIndexKey(tenantId);
            }
        } catch (RuntimeException e) {
            // Distinguish "nothing changed" from "the key moved but the run stopped".
            // The second state must stay resumable: the audit row carries the target
            // generation and whether the index rebuild is owed, and marking it failed
            // would make a rerun start a *new* rotation instead of finishing this one —
            // and would drop a pending blind-index rebuild, leaving search silently
            // broken.
            if (generationNow(tenantId) == toGeneration) {
                log.error("DEK rotation for tenant {} advanced to generation {} but did not finish "
                        + "its key work; leaving it resumable", tenantId, toGeneration, e);
                return incomplete(row, fromGeneration, toGeneration, 0,
                        "the data key advanced to generation " + toGeneration + " but the run "
                                + "stopped before re-encryption: " + e.getMessage()
                                + "; rerun the same command to resume (the old key was NOT destroyed)");
            }
            row.setStatus("failed");
            row.setCompletedAt(LocalDateTime.now());
            rotations.save(row);
            log.error("DEK rotation failed for tenant {} before data was rewritten", tenantId, e);
            return new Result(fromGeneration, toGeneration, Status.FAILED,
                    "aborted before data re-encryption: " + e.getMessage(), 0);
        }

        int rowsRewritten = 0;
        try {
            DekReencryptor.Cursor cursor = DekReencryptor.Cursor.start();
            while (true) {
                DekReencryptor.Batch batch = reencryptor.reencryptBatch(
                        tenantId, toGeneration, rebuildIndex, cursor, batchSize);
                rowsRewritten += batch.rowsRewritten();
                cursor = batch.next();
                if (!batch.hasMore()) {
                    break;
                }
            }

            DekReencryptor.Verification verification =
                    reencryptor.verify(tenantId, toGeneration, rebuildIndex);
            if (!verification.passed()) {
                return incomplete(row, fromGeneration, toGeneration, rowsRewritten,
                        "verification found " + verification.notAtTarget() + " value(s) not at "
                                + "generation " + toGeneration + " and "
                                + verification.staleNameIndexes() + " stale name index(es); the old "
                                + "key was NOT destroyed — rerun the same command to resume");
            }

            // The only irreversible step, after the read-back has proved nothing needs
            // the old generation. A resumed run that crashed between verification and
            // destruction finds the row already gone and treats it as done.
            resolver.destroyArchivedDek(tenantId, fromGeneration);
        } catch (RuntimeException e) {
            return incomplete(row, fromGeneration, toGeneration, rowsRewritten,
                    "re-encryption did not complete: " + e.getMessage()
                            + "; all data remains readable through the archived generation and the "
                            + "old key was NOT destroyed");
        }

        row.setStatus("completed");
        row.setRowsRewritten(rowsRewritten);
        row.setCompletedAt(LocalDateTime.now());
        rotations.save(row);
        log.info("DEK rotation for tenant {} completed: generation {} -> {}, {} row(s) rewritten, "
                        + "old generation destroyed, backup {}",
                tenantId, fromGeneration, toGeneration, rowsRewritten, row.getBackupRef());
        return new Result(fromGeneration, toGeneration, Status.COMPLETED,
                "every ciphertext value was read back at generation " + toGeneration
                        + " and generation " + fromGeneration + " was destroyed; recovery requires "
                        + "the verified backup " + row.getBackupRef(),
                rowsRewritten);
    }

    private Result incomplete(KeyRotation row, int fromGeneration, int toGeneration,
                              int rowsRewritten, String detail) {
        row.setStatus("reencryption_incomplete");
        row.setRowsRewritten(rowsRewritten);
        row.setCompletedAt(null);
        rotations.save(row);
        log.warn("DEK rotation for tenant {} is incomplete at generation {} -> {}: {}",
                row.getTenantId(), fromGeneration, toGeneration, detail);
        return new Result(fromGeneration, toGeneration, Status.INCOMPLETE, detail, rowsRewritten);
    }

    /** The tenant's current generation, or -1 when it cannot be read at all. */
    private int generationNow(UUID tenantId) {
        try {
            return resolver.generationFor(tenantId);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private Optional<KeyRotation> findResumable(UUID tenantId) {
        List<KeyRotation> rows = rotations.findByOperationAndTenantIdOrderByStartedAtDesc(
                "dek_rotation", tenantId);
        if (rows == null) {
            return Optional.empty();
        }
        return rows.stream().filter(r -> RESUMABLE.contains(r.getStatus())).findFirst();
    }
}
