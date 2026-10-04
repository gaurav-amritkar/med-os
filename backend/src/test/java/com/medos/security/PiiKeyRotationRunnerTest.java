package com.medos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.medos.entity.KeyRotation;
import com.medos.repository.KeyRotationRepository;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * #89: the operator entry point for a KEK rotation.
 *
 * <p>The dangerous default here is doing something. A runner that rotated on every boot
 * would eventually rotate with no human asking, and the first time that happened
 * against a production key file it would be an incident rather than a chore. So the
 * default is a dry run that writes nothing, writing requires saying so explicitly, and
 * verification goes through the same read path the application uses.
 */
class PiiKeyRotationRunnerTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private FakeStore store;
    private KeyRotationRepository rotations;
    private PiiKeyRotationRunner runner;

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return key;
    }

    private static String b64(byte[] key) {
        return Base64.getEncoder().encodeToString(key);
    }

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        shutdowns = 0;
        rotations = mock(KeyRotationRepository.class);
        org.mockito.Mockito.lenient().when(rotations.save(any(KeyRotation.class)))
                .thenAnswer(call -> call.getArgument(0));
        runner = runnerFor(new KeyRewrapService(store, rotations));
    }

    private int shutdowns;

    private PiiKeyRotationRunner runnerFor(KeyRewrapService service) {
        return new PiiKeyRotationRunner(store, service, store.currentKek(), () -> shutdowns++);
    }

    @Test
    @DisplayName("an ordinary boot neither rotates nor exits: the app keeps serving")
    void ordinaryBootKeepsServing() throws Exception {
        UUID tenant = store.addTenant();

        runner.run(new DefaultApplicationArguments());

        assertThat(shutdowns)
                .as("only an explicitly requested rotation should stop the process")
                .isZero();
        assertThat(store.kekVersionOf(tenant)).isEqualTo(1);
    }

    @Test
    @DisplayName("each explicit mode finishes and exits, so a one-off container terminates")
    void explicitModesExitAfterFinishing() throws Exception {
        store.addTenant();

        runner.run(new DefaultApplicationArguments("--dry-run", "--target-version=2"));
        assertThat(shutdowns).isEqualTo(1);

        runner.run(new DefaultApplicationArguments("--verify"));
        assertThat(shutdowns).isEqualTo(2);

        runner.run(new DefaultApplicationArguments(
                "--apply",
                "--new-kek=" + b64(randomKey()),
                "--target-version=2"));
        assertThat(shutdowns).isEqualTo(3);
    }

    @Test
    @DisplayName("a rotation that reports success but leaves a tenant unreadable is caught")
    void postRotationReadBackCatchesASilentFailure() throws Exception {
        store.addTenant();
        // A rotation that claims COMPLETED while wrapping a row under a key nobody
        // configured. Only reading the data back catches this.
        KeyRewrapService lying = new KeyRewrapService(store, rotations) {
            @Override
            public Result rotate(byte[] oldKek, int targetKekVersion, byte[] newKek, String initiatedBy) {
                store.corrupt(store.tenantIdsWithKeys().get(0));
                return new Result(1, Status.COMPLETED, "looks fine");
            }
        };

        assertThatThrownBy(() -> runnerFor(lying).run(new DefaultApplicationArguments(
                "--apply",
                "--new-kek=" + b64(randomKey()),
                "--target-version=2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Verification FAILED");
    }

    @Test
    @DisplayName("with no mode requested the runner writes nothing")
    void dryRunIsTheDefault() throws Exception {
        UUID tenant = store.addTenant();
        byte[] wrappedBefore = store.wrappedDekOf(tenant).orElseThrow();

        runner.run(new DefaultApplicationArguments());

        assertThat(store.wrappedDekOf(tenant).orElseThrow()).isEqualTo(wrappedBefore);
        assertThat(store.kekVersionOf(tenant)).isEqualTo(1);
        verifyNoInteractions(rotations);
    }

    @Test
    @DisplayName("a dry run reports the work without touching key material")
    void dryRunReportsCounts() {
        store.addTenant();
        store.addTenant();

        String report = runner.report(store.currentKek(), 2);

        assertThat(report).contains("2").containsIgnoringCase("tenant");
    }

    @Test
    @DisplayName("apply refuses when no new KEK is supplied, and writes nothing")
    void applyRefusesWithoutNewKek() throws Exception {
        UUID tenant = store.addTenant();
        byte[] wrappedBefore = store.wrappedDekOf(tenant).orElseThrow();

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--apply")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("new KEK");

        assertThat(store.wrappedDekOf(tenant).orElseThrow()).isEqualTo(wrappedBefore);
        verifyNoInteractions(rotations);
    }

    @Test
    @DisplayName("apply refuses when the target version is not ahead of the current one")
    void applyRefusesNonIncreasingVersion() throws Exception {
        store.addTenant();

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments(
                "--apply",
                "--new-kek=" + b64(randomKey()),
                "--target-version=1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("target");
    }

    @Test
    @DisplayName("apply re-wraps under the new KEK and leaves the DEK identical")
    void applyRewrapsAndKeepsTheDek() throws Exception {
        UUID tenant = store.addTenant();
        byte[] dekBefore = store.plainDek(tenant);
        byte[] newKek = randomKey();

        runner.run(new DefaultApplicationArguments(
                "--apply",
                "--new-kek=" + b64(newKek),
                "--target-version=2",
                "--initiated-by=ops@example.test"));

        assertThat(store.kekVersionOf(tenant)).isEqualTo(2);
        assertThat(KeyWrapCipher.unwrap(store.wrappedDekOf(tenant).orElseThrow(), newKek))
                .as("the same DEK must come back under the new KEK")
                .isEqualTo(dekBefore);
    }

    @Test
    @DisplayName("apply records who ran it and which version, which is the DPDP evidence trail")
    void applyWritesEvidence() throws Exception {
        store.addTenant();

        runner.run(new DefaultApplicationArguments(
                "--apply",
                "--new-kek=" + b64(randomKey()),
                "--target-version=2",
                "--initiated-by=ops@example.test"));

        // The row is written as in_progress and then updated, so the evidence is the last save.
        var captor = org.mockito.ArgumentCaptor.forClass(KeyRotation.class);
        verify(rotations, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        KeyRotation row = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(row.getOperation()).isEqualTo("kek_rewrap");
        assertThat(row.getInitiatedBy()).isEqualTo("ops@example.test");
        assertThat(row.getKekVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("apply aborts and reports when a tenant cannot unwrap with the current KEK")
    void applyAbortsOnUnreadableTenant() throws Exception {
        UUID healthy = store.addTenant();
        byte[] wrappedBefore = store.wrappedDekOf(healthy).orElseThrow();
        UUID broken = store.addTenant();
        store.corrupt(broken);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments(
                "--apply",
                "--new-kek=" + b64(randomKey()),
                "--target-version=2")))
                .hasMessageContaining("abort");

        assertThat(store.wrappedDekOf(healthy).orElseThrow())
                .as("fail-closed means the readable tenant is untouched too")
                .isEqualTo(wrappedBefore);
    }

    @Test
    @DisplayName("verify fails loudly, naming the tenant, when one cannot unwrap")
    void verifyFailsLoudlyOnUnreadableTenant() throws Exception {
        store.addTenant();
        UUID broken = store.addTenant();
        store.corrupt(broken);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--verify")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(broken.toString());
    }

    @Test
    @DisplayName("verify passes when every tenant unwraps with the loaded KEK")
    void verifyPassesOnHealthyFleet() throws Exception {
        store.addTenant();
        store.addTenant();

        runner.run(new DefaultApplicationArguments("--verify"));
    }

    @Test
    @DisplayName("conflicting modes are refused rather than silently resolved")
    void conflictingModesAreRefused() throws Exception {
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--apply", "--verify")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("one");

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--dry-run", "--apply")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("an unknown flag is refused instead of being ignored")
    void unknownFlagIsRefused() throws Exception {
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--rotate-everything")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("--rotate-everything");
    }

    /** Minimal stand-in for the key store, plus the KEK the app is "running" with. */
    private static final class FakeStore implements TenantKeyStore {

        private final Map<UUID, byte[]> plainDeks = new LinkedHashMap<>();
        private final Map<UUID, byte[]> wrappedDeks = new LinkedHashMap<>();
        private final Map<UUID, byte[]> wrappedBiKeys = new LinkedHashMap<>();
        private final Map<UUID, Integer> versions = new LinkedHashMap<>();
        private byte[] currentKek;

        byte[] currentKek() {
            if (currentKek == null) {
                currentKek = randomKey();
            }
            return currentKek;
        }

        UUID addTenant() {
            UUID id = UUID.randomUUID();
            byte[] dek = randomKey();
            plainDeks.put(id, dek);
            wrappedDeks.put(id, KeyWrapCipher.wrap(dek, currentKek(), RANDOM));
            wrappedBiKeys.put(id, KeyWrapCipher.wrap(randomKey(), currentKek(), RANDOM));
            versions.put(id, 1);
            return id;
        }

        byte[] plainDek(UUID tenantId) {
            return plainDeks.get(tenantId);
        }

        int kekVersionOf(UUID tenantId) {
            return versions.get(tenantId);
        }

        /** Simulates material that was re-wrapped under a KEK this deployment cannot read. */
        void corrupt(UUID tenantId) {
            wrappedDeks.put(tenantId, KeyWrapCipher.wrap(plainDeks.get(tenantId), randomKey(), RANDOM));
        }

        @Override
        public Optional<Integer> dekGenerationOf(UUID tenantId) {
            return Optional.of(1);
        }

        @Override
        public Optional<byte[]> wrappedDekOf(UUID tenantId) {
            return Optional.ofNullable(wrappedDeks.get(tenantId));
        }

        @Override
        public Optional<byte[]> wrappedBiKeyOf(UUID tenantId) {
            return Optional.ofNullable(wrappedBiKeys.get(tenantId));
        }

        @Override
        public Optional<Integer> biKeyGenerationOf(UUID tenantId) {
            return Optional.of(1);
        }

        @Override
        public List<UUID> tenantIdsWithKeys() {
            return new ArrayList<>(wrappedDeks.keySet());
        }

        @Override
        public int insert(UUID tenantId, byte[] wrappedDek, byte[] wrappedBiKey) {
            wrappedDeks.put(tenantId, wrappedDek);
            wrappedBiKeys.put(tenantId, wrappedBiKey);
            versions.putIfAbsent(tenantId, 1);
            return 1;
        }

        @Override
        public int insertBlindIndexKey(UUID tenantId, byte[] wrappedBiKey) {
            wrappedBiKeys.put(tenantId, wrappedBiKey);
            return 1;
        }

        @Override
        public int replaceWrappedDek(UUID tenantId, byte[] wrappedDek) {
            wrappedDeks.put(tenantId, wrappedDek);
            return 1;
        }

        @Override
        public int replaceWrappedBiKey(UUID tenantId, byte[] wrappedBiKey) {
            wrappedBiKeys.put(tenantId, wrappedBiKey);
            return 1;
        }

        @Override
        public int setKekVersion(UUID tenantId, int kekVersion) {
            versions.put(tenantId, kekVersion);
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
        public int maxKekVersionInUse() {
            return versions.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        }

        @Override
        public List<UUID> findTenantsPendingKekVersion(int kekVersion) {
            return tenantIdsWithKeys().stream()
                    .filter(id -> versions.get(id) < kekVersion)
                    .toList();
        }
    }
}