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
public class PaymentDTO {
    private UUID id;
    private String paymentNumber;
    private UUID invoiceId;
    private UUID patientId;
    private BigDecimal amount;
    private String paymentMethod;
    private String transactionRef;
    private String status;
    private UUID receivedBy;
    private LocalDateTime receivedAt;
    private String notes;
}