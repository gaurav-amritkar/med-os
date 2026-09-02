package com.medos.modules.billing.event;

import lombok.Getter;
import java.util.UUID;

@Getter
public class PatientBalanceEvent {
    private final UUID patientId;

    public PatientBalanceEvent(UUID patientId) {
        this.patientId = patientId;
    }
}
