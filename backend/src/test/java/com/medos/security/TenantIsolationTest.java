package com.medos.security;

import com.medos.entity.Patient;
import com.medos.entity.Tenant;
import com.medos.entity.TenantEntityListener;
import com.medos.repository.PatientRepository;
import com.medos.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the tenant filter is applied on every repository call when a tenant
 * is set, and not applied when none is.
 *
 * <p>Fixture notes, both learned the hard way:
 *
 * <ul>
 *   <li>The delete is a native statement in its own transaction. {@code deleteAll()} is
 *       routed through the tenant inspector and therefore only removes the bound
 *       tenant's rows, which leaves earlier fixtures behind to collide on the
 *       database-wide unique uhid index.
 *   <li>The DEK is created before the entity is built. Created from inside the
 *       converter it would order a {@code tenant_keys} INSERT between two of this
 *       fixture's INSERTs, and the second would fail on the uhid index.
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.cache.type=simple",
        "spring.data.redis.repositories.enabled=false",
})
class TenantIsolationTest {

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void seed() {
        // The previous test method may have left a tenant bound to this thread.
        TenantContext.clear();

        new TransactionTemplate(txManager).executeWithoutResult(
                status -> entityManager.createNativeQuery("DELETE FROM patients").executeUpdate());
        entityManager.clear();

        tenantA = createTenant();
        tenantB = createTenant();
        savePatient("TENANTA", tenantA);
        savePatient("TENANTB", tenantB);
    }

    private UUID createTenant() {
        Tenant t = new Tenant();
        t.setName("Isolation Test Hospital");
        t.setType(Tenant.TenantType.HOSPITAL);
        t.setSlug("isolation-" + UUID.randomUUID().toString().substring(0, 8));
        t.setActive(true);
        return tenantRepository.saveAndFlush(t).getId();
    }

    /**
     * Saving a patient writes encrypted PII, so the owning tenant must be in scope;
     * EncryptionUtil refuses to fall back to a shared key.
     */
    private void savePatient(String prefix, UUID tenantId) {
        TenantContext.setTenantId(tenantId);
        try {
            TenantKeyHolder.get().ensureDekExists();
            Patient p = Patient.builder()
                    .uhid(prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .tenantId(tenantId)
                    .name("Patient " + prefix)
                    .age(40)
                    .gender("Male")
                    .dpdpConsent(true)
                    .outstanding(BigDecimal.ZERO)
                    .createdAt(LocalDateTime.now())
                    .build();
            patientRepository.saveAndFlush(p);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("with a tenant set, only that tenant's rows are visible")
    void withTenantA_onlyTenantARow() {
        TenantContext.setTenantId(tenantA);
        try {
            List<Patient> all = patientRepository.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getTenantId()).isEqualTo(tenantA);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("a different tenant sees only its own rows")
    void withTenantB_onlyTenantBRow() {
        TenantContext.setTenantId(tenantB);
        try {
            List<Patient> all = patientRepository.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getTenantId()).isEqualTo(tenantB);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Reading encrypted PII with no tenant in scope is now refused.
     *
     * <p>This assertion was inverted by per-tenant keys. It previously read every
     * tenant's patients unscoped and decrypted them, which is only possible while one
     * global key exists. With per-tenant DEKs there is no key to resolve, so the
     * converter throws instead of silently returning ciphertext. That is the intended
     * outcome: an unscoped query is either a bug in the caller or a privilege problem,
     * and neither should produce patient data.
     */
    @Test
    @DisplayName("reading encrypted PII with no tenant in scope is refused")
    void withoutTenant_cannotDecryptPii() {
        TenantContext.clear();
        assertThatThrownBy(() -> patientRepository.findAll())
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
