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
public class StockTransactionDTO {
    private UUID id;
    private UUID medicineId;
    private UUID batchId;
    private String transactionType;
    private Integer quantity;
    private UUID patientId;
    private UUID prescriptionId;
    private String referenceNo;
    private String notes;
    private UUID performedBy;
    private LocalDateTime performedAt;
}