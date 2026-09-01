package com.medos.modules.billing.event;

import com.medos.modules.billing.service.PatientBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatientBalanceEventListener {

    private final PatientBalanceService patientBalanceService;

    @Async
    @EventListener
    @Transactional
    public void handleBalanceUpdate(PatientBalanceEvent event) {
        log.debug("Async balance recalculation for patient: {}", event.getPatientId());
        patientBalanceService.recalculateBalance(event.getPatientId());
    }
}
