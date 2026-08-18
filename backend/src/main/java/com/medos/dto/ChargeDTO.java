package com.medos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeDTO {
    private UUID id;
    private UUID patientId;
    private UUID encounterId;
    private UUID admissionId;
    private String chargeType;
    private String description;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private BigDecimal gstPercent;
    private BigDecimal gstAmount;
    private BigDecimal totalAmount;
    private UUID invoiceId;
    private String status;
    private LocalDateTime createdAt;
}