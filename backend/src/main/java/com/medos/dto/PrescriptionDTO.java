package com.medos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionDTO {
    private UUID id;
    private UUID encounterId;
    private UUID patientId;
    private UUID medicineId;
    /**
     * Medicine name, resolved for display. The prescription stores only the
     * medicine id, so a client cannot show a pharmacist which drug to dispense
     * without a second lookup per row.
     */
    private String medicineName;
    private String medicineGenericName;
    private String medicineUnit;
    private String dosage;
    private String frequency;
    private String duration;
    private String instructions;
    private String status;
    private UUID prescribedBy;
    private LocalDateTime prescribedAt;
}