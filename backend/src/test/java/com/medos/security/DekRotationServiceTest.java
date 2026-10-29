package com.medos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.medos.entity.KeyRotation;
import com.medos.repository.KeyRotationRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #91: the compromise rotation, at the service boundary.
 *
 * <p>The invariants pinned here are the ones that make rotating a key safe:
 * a verified backup is mandatory before anything changes; the old key remains archived
 * for late commits; a partial run is resumable without rotating twice; and
 * an unfinished run can never overtake a newer generation.
 */
class DekRotationServiceTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private FakeTenantKeyStore store;
    private TenantKeyHolder holder;
    private Rotations rotations;
    private TenantKeyHolder previousHolder;
    private TenantKeyResolver previousResolver;

    @BeforeEach
    void setUp() {
        // Remember whatever the Spring context (or an earlier class) installed, so this
        // unit test can hand the statics back untouched. Leaving an in-memory holder
        // behind would make a later @SpringBootTest use it while its cached context
        // still believes the production store is wired — the failure looks like a
        // rotation that did not reach the database.
        previousHolder = TenantKeyHolder.isInitialised() ? TenantKeyHolder.get() : null;
        previousResolver = TenantKeyResolverFactory.isInitialised()
                ? TenantKeyResolverFactory.get() : null;

        store = new FakeTenantKeyStore();
        byte[] kek = new byte[32];
        RANDOM.nextBytes(kek);
        holder = new TenantKeyHolder(Base64.getEncoder().encodeToString(kek), store);
        TenantKeyHolder.setInstance(holder);
        TenantKeyResolverFactory.setInstance(holder);
        rotations = new Rotations();
    }

    @AfterEach
    void tearDown() {
        TenantKeyHolder.reset();
        TenantKeyResolverFactory.reset();
        if (previousHolder != null) {
            TenantKeyHolder.setInstance(previousHolder);
        }
        if (previousResolver != null) {
            TenantKeyResolverFactory.setInstance(previousResolver);
        }
    }

    @Test
    @DisplayName("a verified backup is mandatory: every missing piece is refused before any write")
    void refusesWithoutAVerifiedBackup() {
        UUID tenant = UUID.randomUUID();
        byte[] dekBefore = holder.resolveDek(tenant);
        ScriptedReencryptor reencryptor = new ScriptedReencryptor();

        List<DekRotationService.BackupAttestation> invalid = List.of(
                new DekRotationService.BackupAttestation(
                        null, "a".repeat(64), LocalDateTime.now().minusHours(1)),
                new DekRotationService.BackupAttestation(
                        "  ", "a".repeat(64), LocalDateTime.now().minusHours(1)),
                new DekRotationService.BackupAttestation(
                        "pgdump://nightly", "not-a-sha", LocalDateTime.now().minusHours(1)),
                new DekRotationService.BackupAttestation(
                        "pgdump://nightly", "a".repeat(64), null),
                new DekRotationService.BackupAttestation(
                        "pgdump://nightly", "a".repeat(64), LocalDateTime.now().plusDays(1)));

        for (DekRotationService.BackupAttestation backup : invalid) {
            assertThatThrownBy(() -> service(reencryptor).rotate(tenant, "ops", backup, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("backup");
        }

        assertThat(rotations.saves()).as("nothing may be recorded or changed").isZero();
        assertThat(holder.generationFor(tenant)).isEqualTo(1);
        assertThat(holder.dekForGenerationOrNull(tenant, 1)).isEqualTo(dekBefore);
    }

    @Test
    @DisplayName("an unnamed operator is refused, including on a resume")
    void refusesAnUnnamedOperator() {
        UUID tenant = UUID.randomUUID();
        holder.resolveDek(tenant);
        ScriptedReencryptor reencryptor = new ScriptedReencryptor();

        assertThatThrownBy(() -> service(reencryptor).rotate(tenant, "  ", backup(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initiated-by");
        assertThat(rotations.saves()).isZero();
        assertThat(holder.generationFor(tenant)).isEqualTo(1);

        // The same refusal on the resume path: an interrupted run must not be
        // finishable by nobody. The runner requires --initiated-by on every
        // invocation, so this is defence in depth for a direct service call.
        ScriptedReencryptor interrupted = new ScriptedReencryptor();
        interrupted.failOnBatch = true;
        assertThat(service(interrupted).rotate(tenant, "ops@example.test", backup(), false).status())
                .isEqualTo(DekRotationService.Status.INCOMPLETE);
        int savesAfterTheInterruptedRun = rotations.saves();

        assertThatThrownBy(() -> service(new ScriptedReencryptor())
                .rotate(tenant, "  ", backup(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initiated-by");
        assertThat(rotations.saves())
                .as("a refused resume must not record anything")
                .isEqualTo(savesAfterTheInterruptedRun);
        assertThat(holder.generationFor(tenant))
                .as("the refusal happens before the key is touched again")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a complete rotation retains the old DEK for writes that commit after verification")
    void retainsTheOldKeyAfterVerification() {
        UUID tenant = UUID.randomUUID();
        byte[] dek1 = holder.resolveDek(tenant);
        ScriptedReencryptor reencryptor = new ScriptedReencryptor();
        reencryptor.batchesBeforeDone = 1;
        reencryptor.rowsPerBatch = 3;

        DekRotationService.Result result = service(reencryptor)
                .rotate(tenant, "ops@example.test", backup(), false);

        assertThat(result.status()).isEqualTo(DekRotationService.Status.COMPLETED);
        assertThat(result.fromGeneration()).isEqualTo(1);
        assertThat(result.toGeneration()).isEqualTo(2);
        assertThat(result.rowsRewritten()).isEqualTo(6);
        assertThat(holder.generationFor(tenant)).isEqualTo(2);
        assertThat(holder.dekForGenerationOrNull(tenant, 1))
                .as("a late d1 commit must remain decryptable after the rotation process exits")
                .isEqualTo(dek1);
        assertThat(dek1).isNotNull();

        assertThat(rotations.rows).hasSize(1);
        KeyRotation row = rotations.rows.get(0);
        assertThat(row.getOperation()).isEqualTo("dek_rotation");
        assertThat(row.getTenantId()).isEqualTo(tenant);
        assertThat(row.getStatus()).isEqualTo("completed");
        assertThat(row.getDekGeneration()).isEqualTo(2);
        assertThat(row.getBackupRef()).isEqualTo("pgdump://nightly/2026-10-24");
        assertThat(row.getRowsRewritten()).isEqualTo(6);
        assertThat(row.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("a failed read-back leaves the old key alive and records a resumable run")
    void failedVerificationDoesNotDestroyTheOldKey() {
        UUID tenant = UUID.randomUUID();
        byte[] dek1 = holder.resolveDek(tenant);
        ScriptedReencryptor reencryptor = new ScriptedReencryptor();
        reencryptor.notAtTarget = 2;
        reencryptor.staleNameIndexes = 1;

        DekRotationService.Result result = service(reencryptor)
                .rotate(tenant, "ops@example.test", backup(), false);

        assertThat(result.status()).isEqualTo(DekRotationService.Status.INCOMPLETE);
        assertThat(holder.dekForGenerationOrNull(tenant, 1))
                .as("destroying the old key on an unverified run is the one unrecoverable mistake")
                .isEqualTo(dek1);
        assertThat(rotations.rows).hasSize(1);
        assertThat(rotations.rows.get(0).getStatus()).isEqualTo("reencryption_incomplete");
        assertThat(rotations.rows.get(0).getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("a run interrupted mid-batch resumes without rotating a second time")
    void resumesWithoutRotatingTwice() {
        UUID tenant = UUID.randomUUID();
        holder.resolveDek(tenant);
        ScriptedReencryptor first = new ScriptedReencryptor();
        first.failOnBatch = true;

        DekRotationService.Result interrupted = service(first)
                .rotate(tenant, "ops@example.test", backup(), false);
        assertThat(interrupted.status()).isEqualTo(DekRotationService.Status.INCOMPLETE);
        assertThat(holder.generationFor(tenant)).isEqualTo(2);
        byte[] generation2 = holder.dekForGenerationOrNull(tenant, 2);

        ScriptedReencryptor second = new ScriptedReencryptor();
        second.batchesBeforeDone = 0;
        DekRotationService.Result resumed = service(second)
                .rotate(tenant, "ops@example.test", backup(), false);

        assertThat(resumed.status()).isEqualTo(DekRotationService.Status.COMPLETED);
        assertThat(holder.generationFor(tenant))
                .as("resuming must not advance the generation again")
                .isEqualTo(2);
        assertThat(holder.dekForGenerationOrNull(tenant, 2)).isEqualTo(generation2);
        assertThat(holder.dekForGenerationOrNull(tenant, 1)).isNotNull();
        assertThat(rotations.rows)
                .as("a resume updates the existing evidence row instead of starting a second run")
                .hasSize(1);
        assertThat(second.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("an unfinished run cannot be resumed after a newer rotation overtook it")
    void refusesToResumePastANewerGeneration() {
        UUID tenant = UUID.randomUUID();
        holder.resolveDek(tenant);
        holder.rotateDek(tenant);
        holder.rotateDek(tenant);
        assertThat(holder.generationFor(tenant)).isEqualTo(3);

        KeyRotation stale = KeyRotation.builder()
                .operation("dek_rotation")
                .tenantId(tenant)
                .dekGeneration(2)
                .startedAt(LocalDateTime.now().minusHours(1))
                .status("reencryption_incomplete")
                .initiatedBy("ops")
                .backupRef("pgdump://nightly")
                .biKeyRotated(false)
                .rowsRewritten(0)
                .build();
        rotations.rows.add(stale);

        assertThatThrownBy(() -> service(new ScriptedReencryptor())
                .rotate(tenant, "ops", backup(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ahead");
    }

    @Test
    @DisplayName("rotating the blind-index key is requested, recorded, and passed to the rebuild")
    void rotatesAndRebuildsTheBlindIndex() {
        UUID tenant = UUID.randomUUID();
        holder.resolveDek(tenant);
        ensureBlindIndexKey(tenant);
        assertThat(store.biKeyGenerationOf(tenant)).contains(1);
        ScriptedReencryptor reencryptor = new ScriptedReencryptor();
        reencryptor.batchesBeforeDone = 0;

        DekRotationService.Result result = service(reencryptor)
                .rotate(tenant, "ops@example.test", backup(), true);

        assertThat(result.status()).isEqualTo(DekRotationService.Status.COMPLETED);
        assertThat(store.biKeyGenerationOf(tenant)).contains(2);
        assertThat(reencryptor.lastRebuildNameIndex).isTrue();
        assertThat(rotations.rows.get(0).getBiKeyRotated()).isTrue();
    }

    @Test
    @DisplayName("rotating one tenant leaves another tenant's keyring byte-identical")
    void leavesOtherTenantsKeyringUntouched() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        holder.resolveDek(tenantA);
        ensureBlindIndexKey(tenantA);
        holder.resolveDek(tenantB);
        ensureBlindIndexKey(tenantB);

        byte[] bDek = store.wrappedDekOf(tenantB).orElseThrow();
        byte[] bBi = store.storedBiKey(tenantB);
        ScriptedReencryptor reencryptor = new ScriptedReencryptor();
        reencryptor.batchesBeforeDone = 0;

        service(reencryptor).rotate(tenantA, "ops", backup(), true);

        assertThat(store.wrappedDekOf(tenantB).orElseThrow()).isEqualTo(bDek);
        assertThat(store.storedBiKey(tenantB)).isEqualTo(bBi);
        assertThat(store.dekGenerationOf(tenantB)).contains(1);
        assertThat(store.biKeyGenerationOf(tenantB)).contains(1);
    }

    @Test
    @DisplayName("a run that advances the key but stops before re-encryption stays resumable, not failed")
    void partialKeyWorkStaysResumable() {
        UUID tenant = UUID.randomUUID();
        holder.resolveDek(tenant);
        byte[] dek1 = holder.dekForGenerationOrNull(tenant, 1);
        ScriptedReencryptor reencryptor = new ScriptedReencryptor();

        // The data key advances, then the index-key step fails: the run has changed
        // state, so recording it as `failed` would make a rerun start a fresh
        // rotation and drop the pending rebuild.
        TenantKeyResolver failingIndex = org.mockito.Mockito.spy(holder);
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated index-key failure"))
                .when(failingIndex).rotateBlindIndexKey(tenant);
        DekRotationService.Result result = new DekRotationService(
                failingIndex, rotations.repository, reencryptor, 2)
                .rotate(tenant, "ops@example.test", backup(), true);

        assertThat(result.status()).isEqualTo(DekRotationService.Status.INCOMPLETE);
        assertThat(holder.generationFor(tenant)).isEqualTo(2);
        assertThat(holder.dekForGenerationOrNull(tenant, 1)).isEqualTo(dek1);
        assertThat(rotations.rows).hasSize(1);
        assertThat(rotations.rows.get(0).getStatus()).isEqualTo("reencryption_incomplete");
        assertThat(rotations.rows.get(0).getBiKeyRotated())
                .as("the rerun must know the index rebuild is still owed")
                .isTrue();

        ScriptedReencryptor resume = new ScriptedReencryptor();
        DekRotationService.Result resumed = service(resume)
                .rotate(tenant, "ops@example.test", backup(), false);
        assertThat(resumed.status()).isEqualTo(DekRotationService.Status.COMPLETED);
        assertThat(holder.generationFor(tenant))
                .as("resuming must not advance the generation again")
                .isEqualTo(2);
        assertThat(resume.lastRebuildNameIndex)
                .as("the rebuild is inherited from the interrupted row even though this call omitted the flag")
                .isTrue();
    }

    private DekRotationService service(DekReencryptor reencryptor) {
        return new DekRotationService(holder, rotations.repository, reencryptor, 2);
    }

    /** The index-key creation API is acting-tenant scoped; bind one for the call. */
    private void ensureBlindIndexKey(UUID tenantId) {
        TenantContext.setTenantId(tenantId);
        try {
            holder.ensureBlindIndexKeyExists();
        } finally {
            TenantContext.clear();
        }
    }

    private static DekRotationService.BackupAttestation backup() {
        return new DekRotationService.BackupAttestation(
                "pgdump://nightly/2026-10-24", "a".repeat(64), LocalDateTime.now().minusHours(1));
    }

    /** Repository double that keeps the rows the service writes, newest first. */
    private static final class Rotations {
        private final List<KeyRotation> rows = new ArrayList<>();
        private final KeyRotationRepository repository = mock(KeyRotationRepository.class);
        private int saves;

        Rotations() {
            when(repository.save(any(KeyRotation.class))).thenAnswer(call -> {
                KeyRotation row = call.getArgument(0);
                saves++;
                if (row.getId() == null) {
                    row.setId(UUID.randomUUID());
                }
                if (!rows.contains(row)) {
                    rows.add(row);
                }
                return row;
            });
            when(repository.findByOperationAndTenantIdOrderByStartedAtDesc(anyString(), any()))
                    .thenAnswer(call -> {
                        String operation = call.getArgument(0);
                        UUID tenant = call.getArgument(1);
                        return rows.stream()
                                .filter(r -> operation.equals(r.getOperation()))
                                .filter(r -> tenant.equals(r.getTenantId()))
                                .sorted(Comparator.comparing(KeyRotation::getStartedAt).reversed())
                                .toList();
                    });
        }

        int saves() {
            return saves;
        }
    }

    /** A re-encryptor that reports scripted progress so the service's state machine is testable. */
    private static final class ScriptedReencryptor implements DekReencryptor {
        int batchesBeforeDone = 0;
        int rowsPerBatch = 1;
        int calls;
        boolean failOnBatch;
        boolean lastRebuildNameIndex;
        long notAtTarget;
        long staleNameIndexes;

        @Override
        public Batch reencryptBatch(UUID tenantId, int toGeneration, boolean rebuildNameIndex,
                                    Cursor cursor, int batchSize) {
            calls++;
            if (failOnBatch) {
                throw new IllegalStateException("simulated crash while re-encrypting");
            }
            lastRebuildNameIndex = rebuildNameIndex;
            boolean more = calls <= batchesBeforeDone;
            return new Batch(rowsPerBatch, more ? 1 : 0, new Cursor(UUID.randomUUID(), null));
        }

        @Override
        public Verification verify(UUID tenantId, int targetGeneration, boolean nameIndexRebuilt) {
            return new Verification(10, notAtTarget, staleNameIndexes);
        }
    }
}
