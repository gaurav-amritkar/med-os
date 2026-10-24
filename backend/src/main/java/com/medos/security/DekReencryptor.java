package com.medos.security;

import java.util.UUID;

/**
 * Rewrites one tenant's encrypted columns onto a new DEK generation.
 *
 * <p>This is the expensive half of a compromise rotation (issue #91). The key move
 * itself — archive generation N, advance to N+1 — is fast and reversible while the
 * archive exists; making the old key safe to destroy requires visiting every value
 * that names it. That is what this seam does, in bounded batches so a large tenant
 * does not need one enormous transaction and an interrupted run can resume.
 *
 * <p>The batch is a full scan in id order, not a filter for "still dirty" rows.
 * The scan is necessary anyway: verification must prove that <em>no</em> value names
 * the old generation, and a blind-index rotation must revisit rows whose ciphertext
 * is already current but whose {@code name_index} is stale. A keyset cursor makes
 * the scan restartable; re-reading a batch after a crash is idempotent because a
 * value already at the target generation is left byte-identical.
 */
public interface DekReencryptor {

    /**
     * Rewrite one batch of the tenant's rows.
     *
     * @param tenantId          the one tenant being rotated; never fleet-wide
     * @param toGeneration      the generation every ciphertext value must end up on
     * @param rebuildNameIndex  also recompute {@code patients.name_index} with the
     *                          tenant's current blind-index key
     * @param cursor            where the previous batch stopped
     * @param batchSize         maximum rows per table in this call
     */
    Batch reencryptBatch(UUID tenantId, int toGeneration, boolean rebuildNameIndex,
                         Cursor cursor, int batchSize);

    /**
     * Read every value back and prove the rotation is complete.
     *
     * <p>Decrypts every encrypted value with the generation its prefix names, counts
     * any value still not at {@code targetGeneration}, and — when a blind-index key was
     * rotated — recomputes every name digest and counts stale ones. A single failure
     * throws rather than returning a partial verdict: the caller destroys an old key
     * on the strength of this answer, so "mostly verified" is not a state it can use.
     */
    Verification verify(UUID tenantId, int targetGeneration, boolean nameIndexRebuilt);

    /** Keyset position, so a batch never restarts from the top of a table. */
    record Cursor(UUID afterPatientId, UUID afterEncounterId) {
        public static Cursor start() {
            return new Cursor(null, null);
        }
    }

    /** Outcome of one batch. {@code remaining} counts rows not yet visited. */
    record Batch(int rowsRewritten, long remaining, Cursor next) {
        public boolean hasMore() {
            return remaining > 0;
        }
    }

    /** The read-back verdict. Both counters must be zero before an old key is destroyed. */
    record Verification(long valuesChecked, long notAtTarget, long staleNameIndexes) {
        public boolean passed() {
            return notAtTarget == 0 && staleNameIndexes == 0;
        }
    }
}
