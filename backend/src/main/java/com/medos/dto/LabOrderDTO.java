package com.medos.dto;

import com.medos.entity.LabOrder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lab order row for the worklist and the encounter detail pane.
 */
public record LabOrderDTO(
        UUID id,
        UUID encounterId,
        UUID patientId,
        UUID doctorId,
        String testName,
        String testCode,
        String priority,
        String status,
        String result,
        LocalDateTime orderedAt,
        LocalDateTime resultAt
) {

    public static LabOrderDTO from(LabOrder o) {
        return new LabOrderDTO(
                o.getId(),
                o.getEncounterId(),
                o.getPatientId(),
                o.getDoctorId(),
                o.getTestName(),
                o.getTestCode(),
                o.getPriority() == null ? null : o.getPriority().name(),
                o.getStatus() == null ? null : o.getStatus().name(),
                o.getResult(),
                o.getOrderedAt(),
                o.getResultAt()
        );
    }
}
