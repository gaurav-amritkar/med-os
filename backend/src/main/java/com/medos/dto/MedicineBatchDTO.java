package com.medos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicineBatchDTO {
    private UUID id;
    private Long version;
    private UUID medicineId;
    private String batchNo;
    private LocalDate expiryDate;
    private Integer remainingQty;
    private BigDecimal purchasePrice;
    private String supplier;
    private LocalDateTime receivedDate;
}