package com.medos.config;

import com.medos.entity.Patient;
import com.medos.repository.PatientRepository;
import com.medos.util.BlindIndexUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backfills the patient name blind index for any row whose {@code name_index} is
 * NULL.
 *
 * <p>A keyed HMAC cannot be computed in SQL, so the schema only declares the
 * column and the index. A database provisioned from the current V1 has the column
 * from the start, which makes this a no-op: no production database existed when
 * the blind index was folded into the baseline, so there are no pre-V2 rows. It is
 * kept as a safety net rather than deleted, because it is the only thing that
 * would repair a database loaded by other means (a restored dump, or an
 * INSERT that bypassed the entity layer).
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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            if (blindIndexUtil.derivedKeyLength() == 0) {
                log.info("Skipping patient name index backfill: no PII key configured");
                return;
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
            log.info("Backfilled the patient name blind index for {} row(s)", updated);
        } catch (Exception e) {
            log.warn("Patient name index backfill did not complete; name search will match UHID only", e);
        }
    }
}
