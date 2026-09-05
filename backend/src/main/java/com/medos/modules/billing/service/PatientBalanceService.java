package com.medos.modules.billing.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single source of truth for patient outstanding balance.
 *
 * <p>Formula (mirrored in V8 backfill):
 * <pre>
 *   outstanding = MAX(0, SUM(charges WHERE status IN billed|paid)
 *                        - SUM(payments WHERE status = success))
 * </pre>
 *
 * <p>Single statement instead of streaming charges + payments into Java
 * and writing the patient row back, so the recalc scales and avoids N+1.
 *
 * <p>Uses {@link Propagation#REQUIRES_NEW} so the async event listener
 * (which runs on its own thread without a parent transaction) can still
 * execute the recalc. Synchronous callers running inside an existing
 * transaction get the same behaviour as before: the recalc is committed
 * in a separate transaction after the originating write commits.
 */
@Service
public class PatientBalanceService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recalculateBalance(UUID patientId) {
        entityManager.createNativeQuery("""
                UPDATE patients p
                SET outstanding = GREATEST(
                    COALESCE((
                        SELECT SUM(c.total_amount)
                        FROM charges c
                        WHERE c.patient_id = p.id
                          AND c.status IN ('billed', 'paid')
                    ), 0)
                    -
                    COALESCE((
                        SELECT SUM(pay.amount)
                        FROM payments pay
                        WHERE pay.patient_id = p.id
                          AND pay.status = 'success'
                    ), 0),
                    0
                )
                WHERE p.id = :patientId
                """)
                .setParameter("patientId", patientId)
                .executeUpdate();
    }
}
