package com.medos.security;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link TenantKeyStore} for tests that need real wrap/unwrap behaviour
 * without a database.
 *
 * <p>Replaces the inner {@code InMemoryTenantKeyHolder}: same contract, top-level
 * so it can be reused across test classes.
 */
public class InMemoryTenantKeyStore implements TenantKeyStore {

    private final Map<UUID, byte[]> deks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> dekGenerations = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public Optional<Integer> dekGenerationOf(UUID tenantId) {
        return Optional.ofNullable(dekGenerations.get(tenantId));
    }

    @Override
    public Optional<byte[]> wrappedDekOf(UUID tenantId) {
        byte[] dek = deks.get(tenantId);
        return dek == null ? Optional.empty() : Optional.of(dek.clone());
    }

    @Override
    public List<UUID> tenantIdsWithKeys() {
        return new ArrayList<>(deks.keySet());
    }

    @Override
    public int insert(UUID tenantId, byte[] wrappedDek, byte[] wrappedBiKey) {
        deks.put(tenantId, wrappedDek.clone());
        dekGenerations.put(tenantId, 1);
        return 1;
    }

    @Override
    public int replaceWrappedDek(UUID tenantId, byte[] wrappedDek) {
        deks.put(tenantId, wrappedDek.clone());
        return 1;
    }

    // --- BI stubs (not used by TenantKeyIsolationTest) ---

    @Override
    public Optional<byte[]> wrappedBiKeyOf(UUID tenantId) { return Optional.empty(); }

    @Override
    public int insertBlindIndexKey(UUID tenantId, byte[] wrappedBiKey) { return 0; }

    @Override
    public int replaceWrappedBiKey(UUID tenantId, byte[] wrappedBiKey) { return 0; }

    @Override
    public int setKekVersion(UUID tenantId, int kekVersion) { return 0; }

    @Override
    public int advanceDekGeneration(UUID tenantId, int generation) {
        dekGenerations.put(tenantId, generation);
        return 1;
    }

    @Override
    public int advanceBiKeyGeneration(UUID tenantId, int generation) { return 0; }

    @Override
    public Optional<Integer> biKeyGenerationOf(UUID tenantId) { return Optional.empty(); }

    @Override
    public List<UUID> findTenantsPendingKekVersion(int kekVersion) { return List.of(); }

    // --- test helpers ---

    /** Simulate a key existing on disk without wrapping. */
    void putRawDek(UUID tenantId, byte[] dek) {
        deks.put(tenantId, dek.clone());
        dekGenerations.putIfAbsent(tenantId, 1);
    }

    void corruptDek(UUID tenantId) {
        deks.put(tenantId, new byte[4]);
    }
}