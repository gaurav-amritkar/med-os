package com.medos.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory {@link TenantKeyStore} for tests that need real wrap/unwrap behaviour
 * without a database.
 *
 * <p>The port has ten methods rather than the forty a {@code JpaRepository} demands,
 * so a test cannot pass by accident against a method the lifecycle never calls.
 */
class FakeTenantKeyStore implements TenantKeyStore {

    private final Map<UUID, byte[]> deks = new LinkedHashMap<>();
    private final Map<UUID, byte[]> biKeys = new LinkedHashMap<>();
    private final Map<UUID, Integer> kekVersions = new LinkedHashMap<>();
    private final Map<UUID, Integer> dekGenerations = new LinkedHashMap<>();
    @Override
    public Optional<Integer> dekGenerationOf(UUID tenantId) {
        return dekGenerations.containsKey(tenantId) ? Optional.of(dekGenerations.get(tenantId)) : Optional.empty();
    }

    @Override
    public Optional<byte[]> wrappedDekOf(UUID tenantId) {
        return Optional.ofNullable(deks.get(tenantId)).map(byte[]::clone);
    }

    @Override
    public java.util.Optional<byte[]> wrappedBiKeyOf(UUID tenantId) {
        return Optional.ofNullable(biKeys.get(tenantId)).map(byte[]::clone);
    }

    @Override
    public List<UUID> tenantIdsWithKeys() {
        return new ArrayList<>(deks.keySet());
    }

    @Override
    public int insert(UUID tenantId, byte[] wrappedDek, byte[] wrappedBiKey) {
        deks.put(tenantId, wrappedDek.clone());
        dekGenerations.put(tenantId, 1);
        kekVersions.put(tenantId, 1);
        if (wrappedBiKey != null) {
            biKeys.put(tenantId, wrappedBiKey.clone());
        }
        return 1;
    }

    @Override
    public int replaceWrappedDek(UUID tenantId, byte[] wrappedDek) {
        deks.put(tenantId, wrappedDek.clone());
        return 1;
    }

    @Override
    public int replaceWrappedBiKey(UUID tenantId, byte[] wrappedBiKey) {
        biKeys.put(tenantId, wrappedBiKey.clone());
        return 1;
    }

    @Override
    public int setKekVersion(UUID tenantId, int kekVersion) {
        kekVersions.put(tenantId, kekVersion);
        return 1;
    }

    @Override
    public int advanceDekGeneration(UUID tenantId, int generation) {
        dekGenerations.put(tenantId, generation);
        return 1;
    }

    @Override
    public int advanceBiKeyGeneration(UUID tenantId, int generation) {
        return 1;
    }

    @Override
    public List<UUID> findTenantsPendingKekVersion(int kekVersion) {
        List<UUID> pending = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : kekVersions.entrySet()) {
            if (e.getValue() < kekVersion) {
                pending.add(e.getKey());
            }
        }
        return pending;
    }

    byte[] storedDek(UUID tenantId) {
        return deks.get(tenantId);
    }

    void corruptStoredDek(UUID tenantId) {
        deks.put(tenantId, new byte[4]);
    }
}
