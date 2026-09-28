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
 * Backfills the patient name blind index for rows written before V2 added the
 * column.
 *
 * <p>A keyed HMAC cannot be computed in SQL, so the migration only adds the
 * column and the index. This fills every row where {@code name_index IS NULL} on
 * startup, so patients registered before the migration become searchable by name
 * without a manual step.
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
