package com.medos.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The narrow set of key-material operations the lifecycle needs.
 *
 * <p>Extracted so {@link TenantKeyHolder} does not depend on the whole
 * {@code JpaRepository} surface. A test double then implements eight methods instead
 * of forty, and a method the lifecycle never calls cannot be stubbed by accident.
 */
public interface TenantKeyStore {

    Optional<Integer> dekGenerationOf(UUID tenantId);

    Optional<byte[]> wrappedDekOf(UUID tenantId);

    /**
     * The wrapped DEK for one specific generation: the current generation reads from
     * {@code tenant_keys}, superseded generations from the {@code tenant_dek_history}
     * archive. A generation that was never issued reads as empty — the caller
     * converts that into a loud {@code UnknownGenerationException}, never a wrong key.
     */
    Optional<byte[]> wrappedDekOfGeneration(UUID tenantId, int generation);

    /**
     * Archive a superseded DEK generation. Idempotent: archiving a generation that is
     * already archived is a no-op (a rerun after an interrupted rotation must not fail
     * on the second attempt's duplicate row).
     */
    int archiveDek(UUID tenantId, int generation, byte[] wrappedDek, int kekVersion);

    /** The KEK version currently wrapping this tenant's key columns. */
    Optional<Integer> wrappedKekVersionOf(UUID tenantId);

    Optional<byte[]> wrappedBiKeyOf(UUID tenantId);

    Optional<Integer> biKeyGenerationOf(UUID tenantId);

    List<UUID> tenantIdsWithKeys();

    int insert(UUID tenantId, byte[] wrappedDek, byte[] wrappedBiKey);

    /** Write only the index key, leaving the data key untouched. */
    int insertBlindIndexKey(UUID tenantId, byte[] wrappedBiKey);

    int replaceWrappedDek(UUID tenantId, byte[] wrappedDek);

    int replaceWrappedBiKey(UUID tenantId, byte[] wrappedBiKey);

    int setKekVersion(UUID tenantId, int kekVersion);

    int advanceDekGeneration(UUID tenantId, int generation);

    int advanceBiKeyGeneration(UUID tenantId, int generation);

    List<UUID> findTenantsPendingKekVersion(int kekVersion);

    /**
     * Highest KEK version any tenant is wrapped with, or 0 when no tenant has keys yet.
     * An operator tool needs this to refuse a target version that is not actually ahead,
     * rather than silently doing nothing.
     */
    int maxKekVersionInUse();
}
