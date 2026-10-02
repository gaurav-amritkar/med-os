package com.medos.config;

import com.medos.entity.Patient;
import com.medos.entity.Tenant;
import com.medos.repository.PatientRepository;
import com.medos.repository.TenantRepository;
import com.medos.security.TenantContext;
import com.medos.util.BlindIndexUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Backfills the patient name blind index for any row whose {@code name_index} is
 * NULL.
 *
 * <p>A keyed HMAC cannot be computed in SQL, so the schema only declares the column
 * and the index. A database provisioned from the current V1 has the column from the
 * start, which makes this a no-op: no production database existed when the blind
 * index was folded into the baseline, so there are no pre-V2 rows. It is kept as a
 * safety net rather than deleted, because it is the only thing that would repair a
 * database loaded by other means (a restored dump, or an INSERT that bypassed the
 * entity layer).
 *
 * <p><b>Iterates per tenant.</b> Each tenant's blind index is derived from that
 * tenant's key, so backfilling across tenants in one pass would compute a digest
 * under the wrong key and leave every patient unsearchable by name. It also means a
 * failure is confined to one tenant rather than blocking startup for all of them.
 *
 * <p>Runs after the admin bootstrap so the schema and the PII key are both in
 * place. Failures are logged and swallowed: a missing backfill degrades search to
 * UHID-only, which is strictly better than refusing to start.
 */
@Slf4j
@Order(20)
@Component
@RequiredArgsConstructor
public class PatientNameIndexBackfillRunner implements ApplicationRunner {

    private final PatientRepository patientRepository;
    private final BlindIndexUtil blindIndexUtil;
    private final TenantRepository tenantRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (blindIndexUtil.derivedKeyLength() == 0) {
            log.info("Skipping patient name index backfill: no PII key configured");
            return;
        }
        List<Tenant> tenants;
        try {
            tenants = tenantRepository.findAll();
        } catch (Exception e) {
            log.warn("Patient name index backfill did not run: tenants could not be listed. "
                    + "Name search will match UHID only. Cause: {}", e.getClass().getSimpleName());
            return;
        }
        for (Tenant tenant : tenants) {
            backfillFor(tenant);
        }
    }

    /**
     * Backfill one tenant, with that tenant in scope for the duration.
     *
     * <p>{@code TenantContext} is a ThreadLocal, so it must be cleared afterwards:
     * leaving a tenant bound on the startup thread would make an unscoped query on
     * that thread silently read one hospital's data.
     */
    @Transactional
    public void backfillFor(Tenant tenant) {
        if (tenant == null || tenant.getId() == null) {
            return;
        }
        TenantContext.setTenantId(tenant.getId());
        try {
            if (com.medos.security.TenantKeyHolder.isInitialised()) {
                com.medos.security.TenantKeyHolder.get().ensureDekExists();
                com.medos.security.TenantKeyHolder.get().ensureBlindIndexKeyExists();
            }
            var unindexed = patientRepository.findByNameIndexIsNull();
            if (unindexed.isEmpty()) {
                return;
            }
            int updated = 0;
            for (Patient patient : unindexed) {
                patient.setNameIndex(blindIndexUtil.indexPatientName(patient.getName()));
                updated++;
            }
            patientRepository.saveAll(unindexed);
            log.info("Backfilled the patient name blind index for {} row(s) in tenant {}",
                    updated, tenant.getId());
        } catch (Exception e) {
            // Scoped to this tenant: one bad tenant must not stop the others, and must
            // not stop the application from serving.
            log.warn("Patient name index backfill did not complete for tenant {}; name search "
                    + "in that tenant will match UHID only. Cause: {}",
                    tenant.getId(), e.getClass().getSimpleName());
        } finally {
            TenantContext.clear();
        }
    }
}
