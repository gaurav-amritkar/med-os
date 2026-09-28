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
 * <p>Uses {@link Propagation#MANDATORY} so the recalc only runs inside an
 * existing transaction. The async event listener that triggers it is itself
 * {@code @Transactional}, so the change is committed atomically with the
 * originating write.
 */
@Service
public class PatientBalanceService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Recalculates a patient's outstanding balance from their charges and
     * payments.
     *
     * <p>REQUIRES_NEW, not MANDATORY: the only production caller is
     * {@code PatientBalanceEventListener}, which runs {@code @Async} after
     * commit, so there is no ambient transaction to join. MANDATORY threw
     * {@code IllegalTransactionStateException} on every invocation, so
     * {@code patients.outstanding} silently went stale after every charge,
     * invoice and payment. It previously worked as REQUIRES_NEW; it was changed
     * to MANDATORY to satisfy a unit test, which inverted the real requirement.
     */
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
