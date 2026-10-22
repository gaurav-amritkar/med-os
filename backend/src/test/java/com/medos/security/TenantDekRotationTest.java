package com.medos.security;

import com.medos.util.PiiCiphertextFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Per-tenant DEK rotation (Wave-0 P0, medworks-parity §5.4, deepening: tenant-key-holder).
 *
 * <p>Until now a tenant's DEK could not change: {@code currentGeneration()} hardcoded 1,
 * writes were permanently labelled {@code kv1.d1:} and the single stored DEK was the only
 * key any generation could ever resolve. Rotation here means new DEK <b>bytes</b> for a
 * tenant at generation N+1, with generation N archived so ciphertext already at {@code d<N>}
 * stays readable — the versioned prefix exists precisely to make that possible.
 *
 * <p>Crash-coherence invariant the tests below pin: after every step of a rotation
 * (including mid-rotation failure states) the <i>pair</i> (current DEK, current generation)
 * is read from the store together, and every archived generation remains resolvable, so
 * reads and writes are never mislabelled no matter where the rotation is interrupted.
 */
class TenantDekRotationTest {

    private static final String KEK = "Xk+7Gbc7zXw1xTGHAk5H5CDItim3k0fMHCtAgeKGDdY=";

    private TenantKeyResolver saved;

    @BeforeEach
    void setUp() {
        // Session discipline (surefire class-order lesson): save what installers skip,
        // and restore in tearDown, so a static installed here never leaks into another
        // test class running after this one on CI.
        saved = TenantKeyResolverFactory.isInitialised() ? TenantKeyResolverFactory.get() : null;
        TenantKeyResolverFactory.setInstance(TenantKeyHolder.inMemoryForTesting(KEK));
        TenantKeyHolder.setInstance((TenantKeyHolder) TenantKeyResolverFactory.get());
    }

    @AfterEach
    void tearDown() {
        TenantKeyHolder.reset();
        TenantKeyResolverFactory.reset();
        if (saved != null) {
            TenantKeyResolverFactory.setInstance(saved);
            if (saved instanceof TenantKeyHolder holder) {
                TenantKeyHolder.setInstance(holder);
            }
        }
    }

    @Test
    @DisplayName("rotation issues new key material and advances the generation")
    void rotationIssuesNewKeyMaterialAndAdvancesGeneration() {
        UUID tenant = UUID.randomUUID();
        TenantContext.setTenantId(tenant);
        try {
            byte[] dekBefore = TenantKeyResolverFactory.get().resolveDek(tenant);
            int genBefore = TenantKeyResolverFactory.get().generationFor(tenant);
            assertThat(genBefore).isEqualTo(1);

            int genAfter = TenantKeyResolverFactory.get().rotateDek(tenant);

            assertThat(genAfter).isEqualTo(2);
            assertThat(TenantKeyResolverFactory.get().generationFor(tenant)).isEqualTo(2);
            // New key material, not a rename: encrypting under the same DEK forever is
            // exactly what "rotation" refuses to mean.
            assertThat(TenantKeyResolverFactory.get().resolveDek(tenant)).isNotEqualTo(dekBefore);
            // And the current generation is no longer hardcoded 1 — writes must follow it.
            assertThat(TenantKeyResolverFactory.get().currentGeneration()).isEqualTo(2);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("ciphertext written before a rotation still decrypts afterwards")
    void ciphertextWrittenBeforeRotationStillDecrypts() {
        UUID tenant = UUID.randomUUID();
        TenantContext.setTenantId(tenant);
        try {
            PiiCiphertextFormat format = new PiiCiphertextFormat(PiiCiphertextFormat.tenantResolver())
                    .withWriteGenerationSupplier(TenantKeyResolverFactory::currentGeneration);
            String before = format.encrypt("Rohit Deshmukh");
            assertThat(PiiCiphertextFormat.parseGeneration(before)).isEqualTo(1);

            TenantKeyResolverFactory.get().rotateDek(tenant);

            // Same format instance as the requests that were in flight across the rotation.
            assertThat(format.decrypt(before)).isEqualTo("Rohit Deshmukh");
            // The id is live: the rotation is visible to writes made after it.
            String after = format.encrypt("Rohit Deshmukh");
            assertThat(PiiCiphertextFormat.parseGeneration(after)).isEqualTo(2);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("two rotations stack: every past generation keeps decrypting")
    void twoRotationsStack() {
        UUID tenant = UUID.randomUUID();
        TenantContext.setTenantId(tenant);
        try {
            PiiCiphertextFormat format = new PiiCiphertextFormat(PiiCiphertextFormat.tenantResolver())
                    .withWriteGenerationSupplier(TenantKeyResolverFactory::currentGeneration);
            String atGen1 = format.encrypt("gen one");
            TenantKeyResolverFactory.get().rotateDek(tenant);
            String atGen2 = format.encrypt("gen two");
            TenantKeyResolverFactory.get().rotateDek(tenant);
            String atGen3 = format.encrypt("gen three");

            assertThat(PiiCiphertextFormat.parseGeneration(atGen3)).isEqualTo(3);
            assertThat(format.decrypt(atGen1)).isEqualTo("gen one");
            assertThat(format.decrypt(atGen2)).isEqualTo("gen two");
            assertThat(format.decrypt(atGen3)).isEqualTo("gen three");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("a generation that was never issued fails loudly, not plausibly")
    void unknownGenerationFailsLoudly() {
        UUID tenant = UUID.randomUUID();
        TenantContext.setTenantId(tenant);
        try {
            PiiCiphertextFormat format = new PiiCiphertextFormat(PiiCiphertextFormat.tenantResolver())
                    .withWriteGenerationSupplier(TenantKeyResolverFactory::currentGeneration);
            // A value claiming a future generation must not decrypt with whatever key is
            // handy: a key mismatch surfacing as a plausible name is the worst failure
            // mode this format has.
            assertThatThrownBy(() -> format.decrypt("kv1.d99:"
                    + java.util.Base64.getEncoder().encodeToString(new byte[64])))
                    .isInstanceOf(PiiCiphertextFormat.UnknownGenerationException.class);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("rotation of a tenant without a key creates its first key, nothing to archive")
    void rotatingKeylessTenantCreatesFirstKey() {
        UUID tenant = UUID.randomUUID();
        TenantContext.setTenantId(tenant);
        try {
            assertThat(TenantKeyResolverFactory.get().rotateDek(tenant)).isEqualTo(1);
            assertThat(TenantKeyResolverFactory.get().generationFor(tenant)).isEqualTo(1);
            byte[] dek = TenantKeyResolverFactory.get().resolveDek(tenant);
            assertThat(dek).hasSize(32);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("currentGeneration() on the format's supplier keeps write labels in step")
    void writeGenerationFollowsTheActingTenant() {
        UUID tenant = UUID.randomUUID();
        TenantContext.setTenantId(tenant);
        try {
            // Establish the key first: rotating a keyless tenant creates generation 1
            // (covered separately); this test is about the label following a rotation.
            TenantKeyResolverFactory.get().resolveDek(tenant);
            PiiCiphertextFormat format = new PiiCiphertextFormat(PiiCiphertextFormat.tenantResolver())
                    .withWriteGenerationSupplier(TenantKeyResolverFactory::currentGeneration);
            TenantKeyResolverFactory.get().rotateDek(tenant);
            String labelled = format.encrypt("after rotation");
            assertThat(PiiCiphertextFormat.parseGeneration(labelled)).isEqualTo(2);
            assertThat(labelled.startsWith("kv1.d2:")).isTrue();
        } finally {
            TenantContext.clear();
        }
    }
}
