package com.medos.security;

import com.medos.entity.Patient;
import com.medos.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: verifies Hibernate tenant filter scopes queries to the
 * current tenant via {@link TenantContext} + the AOP aspect.
 *
 * <p>Runs against the real H2 database with the full Spring context so the AOP
 * aspect ({@code TenantFilterAspect}) is active and auto-enables the filter on
 * every repository call when a tenant is set.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // Do not bind Redis — we are only testing JPA filtering.
        "spring.cache.type=simple",
        "spring.data.redis.repositories.enabled=false",
})
class TenantIsolationTest {

    @Autowired private PatientRepository patientRepository;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();

    @BeforeEach
    void seed() {
        TenantContext.clear();
        patientRepository.deleteAll();
        savePatient("TENANTA-0001", tenantA);
        savePatient("TENANTB-0001", tenantB);
    }

    @Test
    void withoutTenant_allVisible() {
        List<Patient> all = patientRepository.findAll();
        assertThat(all).hasSize(2);
    }

    @Test
    void withTenantA_onlyTenantARow() {
        TenantContext.setTenantId(tenantA);
        List<Patient> all = patientRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getTenantId()).isEqualTo(tenantA);
    }

    @Test
    void withTenantB_onlyTenantBRow() {
        TenantContext.setTenantId(tenantB);
        List<Patient> all = patientRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getTenantId()).isEqualTo(tenantB);
    }

    private void savePatient(String uhid, UUID tenantId) {
        Patient p = Patient.builder()
                .uhid(uhid)
                .tenantId(tenantId)
                .name("Patient " + uhid)
                .age(40)
                .gender("Male")
                .dpdpConsent(true)
                .outstanding(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();
        patientRepository.saveAndFlush(p);
    }
}