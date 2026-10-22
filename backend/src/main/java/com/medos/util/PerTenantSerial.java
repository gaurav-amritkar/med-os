package com.medos.util;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The per-tenant serial race policy, used by every board that numbers rows
 * inside one tenant (Enquiry Desk's ENQ, the Appointments board's APT and its
 * per-day token).
 *
 * <p>The pattern: compute {@code current max + 1} and insert inside ONE
 * transaction, each attempt its own transaction. Two concurrent writers can ask
 * for the same number — the unique index makes exactly one of them win, and the
 * loser retries with a fresh max. Callers keep {@code maxAttempts} small
 * (three): the desk is a low-contention surface, and a serial this hot would be
 * better served by a counter table than by more retries.
 */
public final class PerTenantSerial {

    private PerTenantSerial() {
    }

    /**
     * Runs {@code body} in its own transaction, handing it the candidate number
     * {@code currentMax + 1}. Retrying on the unique-index conflict a
     * concurrent sibling's win produces.
     *
     * @param currentMax reads the tenant's largest current number (tenant
     *                   scoping is the caller's query's job, via the inspector
     *                   or a parameter)
     * @param body       the real work — build/stamp/save — which must flush
     *                   before returning so the constraint violation throws
     *                   inside the loop, not at outer-commit time
     */
    public static <T> T assign(TransactionTemplate tx, Supplier<Integer> currentMax,
                               Function<Integer, T> body) {
        DataIntegrityViolationException conflict = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return tx.execute(status -> body.apply(next(currentMax)));
            } catch (DataIntegrityViolationException e) {
                conflict = e;
            }
        }
        throw conflict != null ? conflict
                : new IllegalStateException("could not assign a per-tenant serial number");
    }

    private static int next(Supplier<Integer> currentMax) {
        Integer max = currentMax.get();
        return max == null ? 1 : max + 1;
    }
}
