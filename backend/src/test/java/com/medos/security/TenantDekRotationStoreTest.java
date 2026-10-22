package com.medos.security;

import com.medos.entity.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The store-level half of DEK rotation, against the real {@link TenantKeyStoreJdbc}
 * over H2: the rotation must archive the replaced generation (so historical ciphertext
 * stays decryptable), advance the row's generation, and record the run in
 * {@code key_rotations} as a {@code dek_rotation} operation — the DPDP §8(5)-evidence
 * row the V2 migration reserved for exactly this.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.cache.type=simple",
        "spring.data.redis.repositories.enabled=false",
})
class TenantDekRotationStoreTest {

    @Autowired
    private TenantKeyStore keyStore;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.medos.repository.TenantRepository tenantRepository;

    @Autowired
    private com.medos.repository.KeyRotationRepository rotations;

    @Autowired
    private org.springframework.core.env.Environment environment;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    private UUID tenantId;

    private UUID createTenant() {
        Tenant t = new Tenant();
        t.setName("Rotation Store Test " + UUID.randomUUID());
        t.setType(Tenant.TenantType.HOSPITAL);
        t.setSlug("rotation-store-" + UUID.randomUUID().toString().substring(0, 8));
        t.setActive(true);
        return tenantRepository.saveAndFlush(t).getId();
    }

    @Test
    @DisplayName("rotation archives the old wrapped DEK and advances the generation")
    void rotationArchivesOldGeneration() {
        tenantId = createTenant();
        byte[] dek1 = randomDek();
        keyStore.insert(tenantId, KeyWrapCipher.wrap(dek1, kek(), new java.security.SecureRandom()), null);

        TenantContext.setTenantId(tenantId);
        try {
            TenantKeyHolder holder = holderWithRealStore();
            byte[] resolved1 = holder.resolveDek(tenantId);
            int newGen = holder.rotateDek(tenantId);
            assertThat(newGen).isEqualTo(2);

            // tenant_keys now carries the NEW generation with NEW material...
            assertThat(keyStore.dekGenerationOf(tenantId)).contains(2);
            byte[] resolved2 = holder.resolveDek(tenantId);
            assertThat(resolved2).isNotEqualTo(resolved1);
            // ...while generation 1 is archived and unwrap-able.
            byte[] archivedGen1 = keyStore.wrappedDekOfGeneration(tenantId, 1)
                    .orElseThrow(() -> new AssertionError("generation 1 was not archived"));
            assertThat(KeyWrapCipher.unwrap(archivedGen1, kek())).isEqualTo(dek1);
            // ...and resolves through the holder too.
            assertThat(holder.dekForGenerationOrNull(tenantId, 1)).isEqualTo(dek1);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("every archived generation stays resolvable across repeated rotations")
    void repeatedRotationsStack() {
        tenantId = createTenant();
        byte[] dek1 = randomDek();
        TenantKeyHolder holder = holderWithRealStore();
        keyStore.insert(tenantId, KeyWrapCipher.wrap(dek1, kek(), new java.security.SecureRandom()), null);

        TenantContext.setTenantId(tenantId);
        try {
            assertThat(holder.rotateDek(tenantId)).isEqualTo(2);
            byte[] dek2 = holder.resolveDek(tenantId);
            assertThat(holder.rotateDek(tenantId)).isEqualTo(3);

            assertThat(holder.dekForGenerationOrNull(tenantId, 1)).isEqualTo(dek1);
            assertThat(holder.dekForGenerationOrNull(tenantId, 2)).isEqualTo(dek2);
            assertThat(holder.generationFor(tenantId)).isEqualTo(3);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("a rotation is recorded in key_rotations as operation dek_rotation")
    void rotationIsAudited() {
        tenantId = createTenant();
        keyStore.insert(tenantId, KeyWrapCipher.wrap(randomDek(), kek(), new java.security.SecureRandom()), null);

        TenantContext.setTenantId(tenantId);
        try {
            // The service owns the rotation; the test only verifies the audit trail it
            // leaves (calling both would rotate twice — the second run is safe, but the
            // recorded generation would then be 2 past the first).
            com.medos.security.DekRotationService.Result result =
                    new com.medos.security.DekRotationService(TenantKeyResolverFactory.get(), rotations)
                            .rotate(tenantId, "ops-admin");
            org.assertj.core.api.Assertions.assertThat(result.status())
                    .isEqualTo(com.medos.security.DekRotationService.Status.COMPLETED);

            Integer rows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM key_rotations WHERE operation = 'dek_rotation' AND tenant_id = ?",
                    Integer.class, tenantId);
            assertThat(rows).isEqualTo(1);
            Integer recordedGen = jdbc.queryForObject(
                    "SELECT dek_generation FROM key_rotations WHERE operation = 'dek_rotation' AND tenant_id = ?",
                    Integer.class, tenantId);
            assertThat(recordedGen).isEqualTo(result.toGeneration());
            assertThat(result.toGeneration()).isEqualTo(2);
            String status = jdbc.queryForObject(
                    "SELECT status FROM key_rotations WHERE operation = 'dek_rotation' AND tenant_id = ?",
                    String.class, tenantId);
            assertThat(status).isEqualTo("completed");
        } finally {
            TenantContext.clear();
        }
    }

    private TenantKeyHolder holderWithRealStore() {
        // The Spring context wires the production holder against this H2 store already;
        // re-register to make sure the factory and holder statics agree on the instance.
        TenantKeyHolder holder;
        if (!TenantKeyHolder.isInitialised()) {
            // CI-order lesson (same as TenantIsolationTest's guard): a unit test class
            // that runs earlier in this JVM may have cleared the statics a *cached*
            // context relies on, and a cached context never re-runs EncryptionConfig.
            // Rebuild the production pair exactly as EncryptionConfig does rather than
            // depending on class order.
            holder = new TenantKeyHolder(
                    environment.getProperty("medos.security.pii-encryption-key"),
                    keyStore, txManager);
            TenantKeyHolder.setInstance(holder);
        } else {
            holder = TenantKeyHolder.get();
        }
        TenantKeyResolverFactory.setInstance(holder);
        return holder;
    }

    private byte[] kek() {
        // IDENTICAL to the KEK the Spring context booted with — unwraps in this test
        // must use the same key material the production holder does.
        return TestKekHolder.kek();
    }

    private static byte[] randomDek() {
        byte[] dek = new byte[32];
        new java.security.SecureRandom().nextBytes(dek);
        return dek;
    }

    /** The KEK comes from configuration; tests read the one the context actually booted with. */
    static final class TestKekHolder {
        static byte[] kek() {
            // Mirror of EncryptionConfig's decode: test profile PII key from application-test.yml.
            String base64 = "jIMpvDp7I0XTfZLdyYKeryj/7t7yKPydMu+4tOJMBus="; // test profile value
            try {
                byte[] bytes = java.util.Base64.getDecoder().decode(base64);
                if (bytes.length != 32) throw new IllegalStateException("test KEK wrong length");
                return bytes;
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("test KEK not valid Base64", e);
            }
        }
    }
}
