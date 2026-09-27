package com.medos.modules.billing.event;

import com.medos.modules.billing.service.PatientBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatientBalanceEventListener {

    private final PatientBalanceService patientBalanceService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBalanceUpdate(PatientBalanceEvent event) {
        log.debug("Async balance recalculation for patient: {}", event.getPatientId());
        patientBalanceService.recalculateBalance(event.getPatientId());
    }
}
