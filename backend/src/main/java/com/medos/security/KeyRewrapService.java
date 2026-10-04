package com.medos.security;

import com.medos.entity.KeyRotation;
import com.medos.exception.BusinessException;
import com.medos.repository.KeyRotationRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * #88: the routine KEK rotation. Re-wraps every tenant's keys under a new KEK, rewriting
 * only tenant_keys rows — patient data is never rewritten because the DEK itself is
 * unchanged and only its wrapper moves. Online, so there is no downtime window.
 *
 * <p>Idempotent by construction: it targets exactly the rows whose stored wrapper is behind
 * the target KEK version, so a second call re-wraps nothing and an interrupted run finishes
 * by re-running. Fail-closed: before any key row is written, every pending row must unwrap
 * with the old KEK; a single undecryptable row aborts before any write.
 */
public class KeyRewrapService {

    /** Plain dry-run report: counts and the list of rows that would block a real run. */
    public record Plan(int totalKeyRows, int toRewrap, int alreadyCurrent, List<UUID> cannotUnwrap) {
        public boolean safeToProceed() {
            return cannotUnwrap.isEmpty();
        }
    }

    /** Outcome of a real run. */
    public record Result(int rowsRewrapped, Status status, String detail) {}

    public enum Status { COMPLETED, ABORTED }

    private final TenantKeyStore keyStore;
    private final KeyRotationRepository rotations;
    private final SecureRandom random = new SecureRandom();

    public KeyRewrapService(TenantKeyStore keyStore, KeyRotationRepository rotations) {
        this.keyStore = keyStore;
        this.rotations = rotations;
    }

    /** Dry run: what a rotation would do, and which pending rows would block it. No writes. */
    public Plan plan(byte[] oldKek, int targetKekVersion) {
        List<UUID> all = keyStore.tenantIdsWithKeys();
        List<UUID> pending = keyStore.findTenantsPendingKekVersion(targetKekVersion);
        List<UUID> cannotUnwrap = new ArrayList<>();
        for (UUID id : pending) {
            try {
                keyStore.wrappedDekOf(id).ifPresent(w -> KeyWrapCipher.unwrap(w, oldKek));
            } catch (RuntimeException e) {
                cannotUnwrap.add(id);
            }
        }
        return new Plan(all.size(), pending.size(), all.size() - pending.size(), cannotUnwrap);
    }

    /**
     * Fail-closed re-wrap: aborts before writing any key row when any pending row fails to
     * unwrap with the old KEK. Idempotent — rows already at the target version are skipped,
     * so a second call is a no-op and an interrupted run finishes on re-run.
     */
    public Result rotate(byte[] oldKek, int targetKekVersion, byte[] newKek, String initiatedBy) {
        Plan plan = plan(oldKek, targetKekVersion);
        KeyRotation row = new KeyRotation();
        row.setOperation("kek_rewrap");
        row.setKekVersion(targetKekVersion);
        row.setStartedAt(LocalDateTime.now());
        row.setStatus("in_progress");
        row.setInitiatedBy(initiatedBy);
        rotations.save(row);

        if (!plan.cannotUnwrap().isEmpty()) {
            // Fail closed before writing any key row at all.
            row.setStatus("failed");
            row.setCompletedAt(LocalDateTime.now());
            row.setRowsRewritten(0);
            rotations.save(row);
            return new Result(0, Status.ABORTED,
                    "aborting: " + plan.cannotUnwrap().size()
                            + " tenant key(s) do not unwrap with the old KEK: " + plan.cannotUnwrap());
        }

        int rewritten = 0;
        for (UUID id : keyStore.findTenantsPendingKekVersion(targetKekVersion)) {
            byte[] wrappedDek = keyStore.wrappedDekOf(id).orElse(null);
            if (wrappedDek == null) {
                continue;
            }
            byte[] dek = KeyWrapCipher.unwrap(wrappedDek, oldKek);
            keyStore.replaceWrappedDek(id, KeyWrapCipher.wrap(dek, newKek, random));
            byte[] bi = keyStore.wrappedBiKeyOf(id).orElse(null);
            if (bi != null) {
                keyStore.replaceWrappedBiKey(id,
                        KeyWrapCipher.wrap(KeyWrapCipher.unwrap(bi, oldKek), newKek, random));
            }
            keyStore.setKekVersion(id, targetKekVersion);
            rewritten++;
        }
        row.setStatus("completed");
        row.setCompletedAt(LocalDateTime.now());
        row.setRowsRewritten(rewritten);
        rotations.save(row);
        return new Result(rewritten, Status.COMPLETED,
                rewritten + " tenant key(s) re-wrapped -> kek v" + targetKekVersion);
    }
}
