package com.medos.service;

import com.medos.modules.billing.service.PatientBalanceService;
import com.medos.modules.billing.event.PatientBalanceEvent;
import com.medos.modules.billing.event.PatientBalanceEventListener;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link PatientBalanceService}.
 *
 * The SQL is the contract — we just need to confirm the service:
 *   1. Issues the right UPDATE
 *   2. Binds the patient id
 *   3. Requires an enclosing transaction (Propagation.MANDATORY)
 *
 * Verifying the math end-to-end belongs in an integration test against
 * Postgres (the V8 backfill is the canonical reference). This test pins
 * the Java side so the contract is not silently changed.
 */
@ExtendWith(MockitoExtension.class)
class PatientBalanceServiceTest {

    @Mock private EntityManager entityManager;
    @Mock private Query query;

    private final PatientBalanceService service = new PatientBalanceService();

    private final UUID patientId = UUID.randomUUID();

    @BeforeEach
    void injectEntityManager() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    }

    @Test
    void recalculateBalance_runsSingleUpdateAndBindsPatientId() {
        when(query.setParameter("patientId", patientId)).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);

        service.recalculateBalance(patientId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertNotNull(sql);
        assertEquals(true, sql.contains("UPDATE patients"),
                "must update the patients table");
        assertEquals(true, sql.contains("charges"),
                "must aggregate charges");
        assertEquals(true, sql.contains("payments"),
                "must aggregate payments");
        assertEquals(true, sql.contains("'billed', 'paid'"),
                "must include billed and paid charge statuses");
        assertEquals(true, sql.contains("'success'"),
                "must include only successful payments");
        assertEquals(true, sql.contains("GREATEST"),
                "must clamp the result to non-negative");

        verify(query).setParameter("patientId", patientId);
        verify(query).executeUpdate();
    }

    @Test
    void recalculateBalance_usesRequiresNew_propagation() throws Exception {
        // Reflection on the class itself, not the proxy, to read the @Transactional metadata.
        //
        // This asserted MANDATORY until 2026-09-28, and the requirement it encoded
        // was false. The only production caller is PatientBalanceEventListener,
        // which is @Async with @TransactionalEventListener(AFTER_COMMIT): there is
        // no enclosing transaction to join, and there cannot be, because the event
        // is published only after the charge/payment transaction has committed.
        // MANDATORY therefore threw IllegalTransactionStateException on every
        // invocation and patients.outstanding silently went stale.
        //
        // REQUIRES_NEW is what the async caller actually needs: its own transaction,
        // committed independently of the already-committed charge it is reconciling.
        var method = PatientBalanceService.class
                .getDeclaredMethod("recalculateBalance", UUID.class);
        var annotation = method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertNotNull(annotation, "@Transactional must be present");
        assertEquals(Propagation.REQUIRES_NEW, annotation.propagation(),
                "the only caller is @Async AFTER_COMMIT, so it must start its own transaction "
                        + "rather than require one that cannot exist");
    }

    @Test
    void balanceListener_isAsync_soRecalculationHasNoAmbientTransaction() throws Exception {
        // Replaces a previous test that asserted only
        // `assertNotNull(IllegalTransactionStateException.class)` — a tautology
        // that passed regardless of the code under test and so gave false
        // confidence about propagation.
        //
        // This asserts the reason the propagation is REQUIRES_NEW. The listener
        // runs off the request thread after the triggering transaction has
        // committed, so there is no enclosing transaction and the recalculation
        // must open its own. If someone makes the listener synchronous this
        // fails, which is the point: the two facts are coupled.
        var method = PatientBalanceEventListener.class
                .getDeclaredMethod("handleBalanceUpdate", PatientBalanceEvent.class);

        assertNotNull(method.getAnnotation(org.springframework.scheduling.annotation.Async.class),
                "the listener must be @Async: a synchronous listener would reintroduce "
                        + "the enclosing-transaction coupling that broke balance updates");

        var txEvent = method.getAnnotation(
                org.springframework.transaction.event.TransactionalEventListener.class);
        assertNotNull(txEvent, "the listener must be transactional-aware");
        assertEquals(org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT,
                txEvent.phase(),
                "must run after the charge/payment commit, so it can never join that transaction");
    }
}
