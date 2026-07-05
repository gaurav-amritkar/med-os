package com.medos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PaymentRequest {
    @NotNull
    private UUID invoiceId;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotNull
    private String paymentMethod;

    private String transactionRef;
    private String notes;
}
