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
}
