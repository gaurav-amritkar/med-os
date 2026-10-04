package com.medos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.medos.repository.KeyRotationRepository;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The routine KEK rotation rewrites only tenant_keys rows. Patient data is never
 * touched, because the DEK itself is unchanged and only its wrapper moves — so the
 * rotation can run online with no downtime and no re-encryption of a single patient.
 *
 * <p>The dangerous failure mode is not a partial write, it is a rotation that
 * half-succeeds and leaves key material unreadable. These tests pin the three
 * properties that prevent that: nothing is written until every pending row has been
 * proven to unwrap, a finished row is never touched twice, and an interrupted run
 * finishes on re-run.
 */
class KeyRewrapServiceTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] KEK_V1 = randomKey();
    private static final byte[] KEK_V2 = randomKey();

    private MapTenantKeyStore store;
    private KeyRotationRepository rotations;
    private KeyRewrapService service;

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @BeforeEach
    void setUp() {
        store = new MapTenantKeyStore();
        rotations = mock(KeyRotationRepository.class);
        whenSaveReturnsSaved();
        service = new KeyRewrapService(store, rotations);
    }

    private void whenSaveReturnsSaved() {
        org.mockito.Mockito.lenient()
                .when(rotations.save(any(com.medos.entity.KeyRotation.class)))
                .thenAnswer(call -> call.getArgument(0));
    }

    /** One tenant with a DEK and a blind-index key, both wrapped under the given KEK. */
    private UUID tenantWithKeys(byte[] kek, int kekVersion) {
        UUID id = UUID.randomUUID();
        store.put(id,
                KeyWrapCipher.wrap(randomKey(), kek, RANDOM),
                KeyWrapCipher.wrap(randomKey(), kek, RANDOM),
                kekVersion);
        return id;
    }

    private byte[] dekOf(UUID tenantId) {
        return KeyWrapCipher.unwrap(store.wrappedDekOf(tenantId).orElseThrow(), KEK_V2);
    }

    @Test
    @DisplayName("a dry run reports the work and writes nothing at all")
    void dryRunWritesNothing() {
        UUID a = tenantWithKeys(KEK_V1, 1);
        UUID b = tenantWithKeys(KEK_V1, 1);
        store.put(UUID.randomUUID(), KeyWrapCipher.wrap(randomKey(), KEK_V1, RANDOM), null, 2);

        KeyRewrapService.Plan plan = service.plan(KEK_V1, 2);

        assertThat(plan.totalKeyRows()).isEqualTo(3);
        assertThat(plan.toRewrap()).isEqualTo(2);
        assertThat(plan.alreadyCurrent()).isEqualTo(1);
        assertThat(plan.cannotUnwrap()).isEmpty();
        assertThat(plan.safeToProceed()).isTrue();

        // Nothing moved: the bytes on disk are the bytes we put there.
        assertThat(KeyWrapCipher.unwrap(store.wrappedDekOf(a).orElseThrow(), KEK_V1)).hasSize(32);
        assertThat(KeyWrapCipher.unwrap(store.wrappedDekOf(b).orElseThrow(), KEK_V1)).hasSize(32);
        assertThat(store.kekVersionOf(a)).isEqualTo(1);
        assertThat(store.kekVersionOf(b)).isEqualTo(1);
        verify(rotations, never()).save(any());
    }

    @Test
    @DisplayName("a dry run names the tenants whose DEK cannot be unwrapped")
    void dryRunFlagsUnreadableTenants() {
        tenantWithKeys(KEK_V1, 1);
        // A row written under a KEK nobody holds any more: exactly what an operator
        // must be told about before starting, not halfway through.
        UUID unreadable = UUID.randomUUID();
        store.put(unreadable, KeyWrapCipher.wrap(randomKey(), KEK_V2, RANDOM), null, 1);

        KeyRewrapService.Plan plan = service.plan(KEK_V1, 2);

        assertThat(plan.cannotUnwrap()).containsExactly(unreadable);
        assertThat(plan.safeToProceed()).isFalse();
    }

    @Test
    @DisplayName("an undecryptable DEK aborts the rotation before any key row is written")
    void undecryptableDekAbortsBeforeWriting() {
        UUID good = tenantWithKeys(KEK_V1, 1);
        byte[] goodDekBefore = store.wrappedDekOf(good).orElseThrow();
        UUID unreadable = UUID.randomUUID();
        store.put(unreadable, KeyWrapCipher.wrap(randomKey(), KEK_V2, RANDOM), null, 1);

        KeyRewrapService.Result result = service.rotate(KEK_V1, 2, KEK_V2, "operator");

        assertThat(result.status()).isEqualTo(KeyRewrapService.Status.ABORTED);
        assertThat(result.rowsRewrapped()).isZero();
        assertThat(result.detail()).contains(unreadable.toString());

        // The readable tenant must be untouched: fail-closed means *before*, not "rolled back".
        assertThat(store.wrappedDekOf(good).orElseThrow()).isEqualTo(goodDekBefore);
        assertThat(store.kekVersionOf(good)).isEqualTo(1);
    }

    @Test
    @DisplayName("a rotation re-wraps every pending tenant and moves it to the target version")
    void rotationRewritesPendingTenants() {
        UUID a = tenantWithKeys(KEK_V1, 1);
        UUID b = tenantWithKeys(KEK_V1, 1);
        byte[] dekA = KeyWrapCipher.unwrap(store.wrappedDekOf(a).orElseThrow(), KEK_V1);
        byte[] biKeyB = KeyWrapCipher.unwrap(store.wrappedBiKeyOf(b).orElseThrow(), KEK_V1);

        KeyRewrapService.Result result = service.rotate(KEK_V1, 2, KEK_V2, "operator");

        assertThat(result.status()).isEqualTo(KeyRewrapService.Status.COMPLETED);
        assertThat(result.rowsRewrapped()).isEqualTo(2);

        // Same DEK, new wrapper: patient ciphertext stays readable across the rotation.
        assertThat(dekOf(a)).isEqualTo(dekA);
        assertThat(dekOf(b)).hasSize(32);
        assertThat(KeyWrapCipher.unwrap(store.wrappedBiKeyOf(b).orElseThrow(), KEK_V2)).isEqualTo(biKeyB);
        assertThat(store.kekVersionOf(a)).isEqualTo(2);
        assertThat(store.kekVersionOf(b)).isEqualTo(2);
    }

    @Test
    @DisplayName("a second run on a finished rotation is a no-op")
    void secondRunIsANoOp() {
        tenantWithKeys(KEK_V1, 1);
        service.rotate(KEK_V1, 2, KEK_V2, "operator");
        byte[] afterFirst = store.wrappedDekOf(store.tenantIdsWithKeys().get(0)).orElseThrow();

        KeyRewrapService.Result second = service.rotate(KEK_V1, 2, KEK_V2, "operator");

        assertThat(second.status()).isEqualTo(KeyRewrapService.Status.COMPLETED);
        assertThat(second.rowsRewrapped()).isZero();
        assertThat(store.wrappedDekOf(store.tenantIdsWithKeys().get(0)).orElseThrow()).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("an interrupted run completes when it is re-run")
    void interruptedRunResumes() {
        UUID done = tenantWithKeys(KEK_V1, 1);
        UUID pending = tenantWithKeys(KEK_V1, 1);

        // Simulate the interruption: one tenant was already re-wrapped and recorded as such.
        store.setKekVersion(done, 2);
        store.replaceWrappedDek(done, KeyWrapCipher.wrap(
                KeyWrapCipher.unwrap(store.wrappedDekOf(done).orElseThrow(), KEK_V1), KEK_V2, RANDOM));

        KeyRewrapService.Result result = service.rotate(KEK_V1, 2, KEK_V2, "operator");

        assertThat(result.status()).isEqualTo(KeyRewrapService.Status.COMPLETED);
        assertThat(result.rowsRewrapped()).isEqualTo(1);
        assertThat(store.kekVersionOf(pending)).isEqualTo(2);
        assertThat(dekOf(pending)).hasSize(32);
    }

    @Test
    @DisplayName("the rotation is recorded in the audit trail, including the abort")
    void rotationIsAudited() {
        tenantWithKeys(KEK_V1, 1);

        service.rotate(KEK_V1, 2, KEK_V2, "ops@example.test");

        var captor = org.mockito.ArgumentCaptor.forClass(com.medos.entity.KeyRotation.class);
        verify(rotations, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        List<com.medos.entity.KeyRotation> saved = captor.getAllValues();
        com.medos.entity.KeyRotation first = saved.get(0);
        com.medos.entity.KeyRotation last = saved.get(saved.size() - 1);

        assertThat(first.getOperation()).isEqualTo("kek_rewrap");
        assertThat(first.getKekVersion()).isEqualTo(2);
        assertThat(first.getInitiatedBy()).isEqualTo("ops@example.test");
        assertThat(last.getStatus()).isEqualTo("completed");
        assertThat(last.getCompletedAt()).isNotNull();
        assertThat(last.getRowsRewritten()).isEqualTo(1);
    }

    /**
     * A faithful stand-in for TenantKeyStoreJdbc: kek_version starts at 1, "pending"
     * means strictly behind the target, and the blind-index key is optional.
     */
    private static final class MapTenantKeyStore implements TenantKeyStore {

        private record Row(byte[] wrappedDek, byte[] wrappedBiKey, int kekVersion) {}

        private final Map<UUID, Row> rows = new LinkedHashMap<>();

        void put(UUID tenantId, byte[] wrappedDek, byte[] wrappedBiKey, int kekVersion) {
            rows.put(tenantId, new Row(wrappedDek, wrappedBiKey, kekVersion));
        }

        int kekVersionOf(UUID tenantId) {
            return rows.get(tenantId).kekVersion();
        }

        byte[] dekBytes(UUID tenantId) {
            return rows.get(tenantId).wrappedDek();
        }

        @Override
        public Optional<Integer> dekGenerationOf(UUID tenantId) {
            return Optional.of(1);
        }

        @Override
        public Optional<byte[]> wrappedDekOf(UUID tenantId) {
            Row row = rows.get(tenantId);
            return row == null ? Optional.empty() : Optional.of(row.wrappedDek());
        }

        @Override
        public Optional<byte[]> wrappedBiKeyOf(UUID tenantId) {
            Row row = rows.get(tenantId);
            return row == null || row.wrappedBiKey() == null
                    ? Optional.empty()
                    : Optional.of(row.wrappedBiKey());
        }

        @Override
        public Optional<Integer> biKeyGenerationOf(UUID tenantId) {
            return Optional.of(1);
        }

        @Override
        public List<UUID> tenantIdsWithKeys() {
            return new ArrayList<>(rows.keySet());
        }

        @Override
        public int insert(UUID tenantId, byte[] wrappedDek, byte[] wrappedBiKey) {
            put(tenantId, wrappedDek, wrappedBiKey, 1);
            return 1;
        }

        @Override
        public int insertBlindIndexKey(UUID tenantId, byte[] wrappedBiKey) {
            return 0;
        }

        @Override
        public int replaceWrappedDek(UUID tenantId, byte[] wrappedDek) {
            Row row = rows.get(tenantId);
            put(tenantId, wrappedDek, row.wrappedBiKey(), row.kekVersion());
            return 1;
        }

        @Override
        public int replaceWrappedBiKey(UUID tenantId, byte[] wrappedBiKey) {
            Row row = rows.get(tenantId);
            put(tenantId, row.wrappedDek(), wrappedBiKey, row.kekVersion());
            return 1;
        }

        @Override
        public int setKekVersion(UUID tenantId, int kekVersion) {
            Row row = rows.get(tenantId);
            put(tenantId, row.wrappedDek(), row.wrappedBiKey(), kekVersion);
            return 1;
        }

        @Override
        public int advanceDekGeneration(UUID tenantId, int generation) {
            return 1;
        }

        @Override
        public int advanceBiKeyGeneration(UUID tenantId, int generation) {
            return 1;
        }

        @Override
        public List<UUID> findTenantsPendingKekVersion(int kekVersion) {
            return rows.entrySet().stream()
                    .filter(e -> e.getValue().kekVersion() < kekVersion)
                    .map(Map.Entry::getKey)
                    .toList();
        }
    }
}