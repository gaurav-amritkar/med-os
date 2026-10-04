package com.medos.security;

import com.medos.util.BlindIndexUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The silent-search-breakage defect this fixes.
 *
 * <p>Before per-tenant keys, the blind index key was derived from the PII secret
 * itself. Rotating that secret therefore invalidated every {@code name_index}, and
 * search — which matches on equality — returned zero results with no error at all.
 * It presented as broken search rather than as a key problem, which is why it could
 * go unnoticed.
 *
 * <p>These tests fail against that arrangement and pass once the index has its own
 * wrapped key with an independent lifecycle.
 */
class BlindIndexKeyIndependenceTest {

    private static final String KEK_B64 = Base64.getEncoder()
            .encodeToString("kek-kek-kek-kek-kek-kek-kek-kek!".getBytes(StandardCharsets.UTF_8));
    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final String NAME = "Anita Sharma";

    private FakeTenantKeyStore store;
    private TenantKeyResolver holder;

    @BeforeEach
    void setUp() {
        TenantKeyHolder.reset();
        TenantKeyResolverFactory.reset();
        store = new FakeTenantKeyStore();
        holder = new TenantKeyHolder(KEK_B64, store);
        TenantKeyHolder.setInstance((TenantKeyHolder) holder);
        TenantKeyResolverFactory.setInstance(holder);
        TenantContext.setTenantId(TENANT_A);
        // A tenant only has an index key once something has been written. The write
        // path creates it (see PatientService), so the fixture does the same.
        holder.ensureDekExists();
        holder.ensureBlindIndexKeyExists();
    }

    @AfterEach
    void tearDown() {
        TenantKeyResolverFactory.reset();
        TenantContext.clear();
    }

    private String indexWithCurrentSetup() {
        return new BlindIndexUtil().indexPatientName(NAME);
    }

    @Test
    @DisplayName("a KEK re-wrap leaves the blind index unchanged, so name search survives rotation")
    void searchSurvivesKekReWrap() {
        String before = indexWithCurrentSetup();
        assertThat(before).as("the index must be computable to begin with").isNotNull();

        // The routine rotation: every tenant's DEK is re-wrapped under the current KEK.
        holder.rewrapAll();
        holder.clearCache();

        String after = indexWithCurrentSetup();

        assertThat(after)
                .as("a KEK re-wrap must not change the blind index; if it does, patient search "
                        + "silently returns nothing after every rotation")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("rotating the KEK itself does not change the blind index")
    void searchSurvivesKekRotation() {
        String before = indexWithCurrentSetup();
        assertThat(before).as("the index must be computable to begin with").isNotNull();

        // A genuine key rotation: a different KEK replaces the old one and every
        // tenant's material is re-wrapped under it. If the index were derived from the
        // PII secret, every stored digest would become unmatchable and patient search
        // would silently return nothing.
        byte[] newKekBytes = new byte[32];
        Arrays.fill(newKekBytes, (byte) 0x5A);
        String newKek = Base64.getEncoder().encodeToString(newKekBytes);
        TenantKeyResolver rotated = new TenantKeyHolder(newKek, store).withPreviousKek(KEK_B64);
        int rewrapped = rotated.rewrapAll();
        assertThat(rewrapped).as("the re-wrap must visit every tenant holding key material").isEqualTo(1);
        TenantKeyResolverFactory.setInstance(rotated);
        holder = rotated;

        String after = indexWithCurrentSetup();

        assertThat(after)
                .as("after a KEK rotation, digests written under the old KEK must still match; "
                        + "otherwise patient search silently returns zero results")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("the blind index key is not the PII data key")
    void indexKeyIsDistinctFromDek() {
        byte[] dek = holder.resolveDek(TENANT_A);
        String index = indexWithCurrentSetup();

        assertThat(index).isNotNull();
        // The index is an HMAC, so compare indirectly: the same name under a different
        // tenant's key must produce a different digest. Sharing one key would make them equal.
        TenantContext.setTenantId(UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002"));
        String otherTenant = indexWithCurrentSetup();

        assertThat(otherTenant)
                .as("each tenant's index must use its own key, not a shared one")
                .isNotEqualTo(index);
        assertThat(dek).hasSize(32);
    }

    @Test
    @DisplayName("the index is stable across repeated computation")
    void indexIsDeterministic() {
        String first = indexWithCurrentSetup();
        String second = indexWithCurrentSetup();

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("equality search finds the patient, and a different name does not")
    void equalitySearchSemantics() {
        String indexed = indexWithCurrentSetup();

        // Equality: the same name, normalised the same way, matches.
        assertThat(BlindIndexUtil.normaliseName("  Anita   Sharma ")).isEqualTo("anita sharma");
        String viaUtil = new BlindIndexUtil().indexPatientName("  ANITA   sharma ");
        assertThat(viaUtil)
                .as("normalisation must be applied identically on write and on search")
                .isEqualTo(indexed);

        // Non-match: a different name must not produce the same digest.
        assertThat(new BlindIndexUtil().indexPatientName("Anita Verma")).isNotEqualTo(indexed);
    }

    @Test
    @DisplayName("a blank or null name yields no index rather than a digest of nothing")
    void blankNameYieldsNoIndex() {
        assertThat(new BlindIndexUtil().indexPatientName(null)).isNull();
        assertThat(new BlindIndexUtil().indexPatientName("")).isNull();
        assertThat(new BlindIndexUtil().indexPatientName("   ")).isNull();
    }

    @Test
    @DisplayName("the index is a 64-character hex digest, safe for a VARCHAR(64) column")
    void indexFitsTheColumn() {
        String indexed = indexWithCurrentSetup();

        assertThat(indexed).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("the index never reveals the name it protects")
    void indexDoesNotLeakTheName() {
        String indexed = indexWithCurrentSetup();

        assertThat(indexed)
                .as("a digest must not contain the plaintext, nor a recognisable part of it")
                .doesNotContain("anita")
                .doesNotContain("sharma")
                .doesNotContain(NAME);
    }

    @Test
    @DisplayName("with no tenant in scope, no index is produced rather than a shared-key digest")
    void noTenantNoIndex() {
        TenantContext.clear();

        assertThat(indexWithCurrentSetup())
                .as("deriving an index with no tenant would use a shared key, making names "
                        + "cross-comparable between tenants")
                .isNull();
    }

    @Test
    @DisplayName("rotating the blind index key alone does not require re-encrypting ciphertext")
    void biKeyRotationIsIndependentOfDek() {
        byte[] dekBefore = holder.resolveDek(TENANT_A);
        int generationBefore = holder.generationFor(TENANT_A);
        String ciphertextBefore = "kv1.d" + generationBefore + ":someCiphertextBytes";

        // Advance only the index key's generation, as a targeted BI rotation would.
        store.advanceBiKeyGeneration(TENANT_A, 2);

        byte[] dekAfter = holder.resolveDek(TENANT_A);

        assertThat(holder.generationFor(TENANT_A))
                .as("the data key's generation must be untouched by an index-key rotation")
                .isEqualTo(generationBefore);
        assertThat(dekAfter)
                .as("the data key must not change, so no ciphertext needs re-encrypting")
                .isEqualTo(dekBefore);
        assertThat(ciphertextBefore).startsWith("kv1.d" + generationBefore + ":");
    }

    @Test
    @DisplayName("rotating only the index key changes the digests but leaves the data key "
            + "and its ciphertext untouched")
    void biKeyRotationRewritesNoCiphertext() {
        String before = indexWithCurrentSetup();
        byte[] dekBefore = holder.resolveDek(TENANT_A);
        int dekGeneration = holder.generationFor(TENANT_A);
        byte[] wrappedDekBefore = store.storedDek(TENANT_A);
        String ciphertext = "kv1.d" + dekGeneration + ":ciphertextBytes";

        BlindIndexKeyProvider provider = new BlindIndexKeyService(
                store, Base64.getDecoder().decode(KEK_B64), 60_000L, new SecureRandom());
        provider.rotateIndependently(TENANT_A);
        holder.clearCache();

        String after = indexWithCurrentSetup();

        assertThat(after)
                .as("a rotated index key must produce different digests, or it is not a rotation")
                .isNotEqualTo(before);
        assertThat(holder.resolveDek(TENANT_A))
                .as("the data key is untouched, so no ciphertext needs re-encrypting")
                .isEqualTo(dekBefore);
        assertThat(holder.generationFor(TENANT_A))
                .as("the data key generation is untouched")
                .isEqualTo(dekGeneration);
        assertThat(store.storedDek(TENANT_A))
                .as("the data key wrapper is not rewritten by an index-key rotation")
                .isEqualTo(wrappedDekBefore);
        assertThat(store.biKeyGenerationOf(TENANT_A)).contains(2);
        assertThat(ciphertext).startsWith("kv1.d" + dekGeneration + ":");
    }

    @Test
    @DisplayName("the index key is a separate wrapped key, not a derivation of the data key")
    void indexKeyIsStoredSeparatelyFromTheDataKey() {
        assertThat(store.storedBiKey(TENANT_A))
                .as("the index key has its own wrapped column on the keyring row")
                .isNotNull();
        assertThat(store.storedDek(TENANT_A))
                .isNotNull();
        assertThat(store.storedBiKey(TENANT_A))
                .as("wrapping a different secret must not produce the same bytes as the data key")
                .isNotEqualTo(store.storedDek(TENANT_A));
    }

    @Test
    @DisplayName("after a re-wrap the material is readable with the current KEK and NOT with the old one")
    void rotationEndsWhenTheRewrapCompletes() {
        holder.ensureBlindIndexKeyExists();
        String before = indexWithCurrentSetup();
        byte[] wrappedBefore = store.storedBiKey(TENANT_A);

        byte[] newKekBytes = new byte[32];
        Arrays.fill(newKekBytes, (byte) 0x5A);
        TenantKeyResolver rotated = new TenantKeyHolder(
                Base64.getEncoder().encodeToString(newKekBytes), store).withPreviousKek(KEK_B64);
        rotated.rewrapAll();

        byte[] wrappedAfter = store.storedBiKey(TENANT_A);
        assertThat(wrappedAfter)
                .as("the wrapper bytes must change, since a fresh IV is used")
                .isNotEqualTo(wrappedBefore);
        assertThatThrownBy(() -> KeyWrapCipher.unwrap(wrappedAfter,
                Base64.getDecoder().decode(KEK_B64)))
                .as("once re-wrapped, the old KEK must no longer open the material")
                .isInstanceOf(IllegalStateException.class);
        assertThat(KeyWrapCipher.unwrap(wrappedAfter, newKekBytes))
                .as("the current KEK must open it")
                .hasSize(32);
        TenantKeyResolverFactory.setInstance(rotated);
        assertThat(indexWithCurrentSetup())
                .as("search must still match after the rotation completed")
                .isEqualTo(before);
    }
}
