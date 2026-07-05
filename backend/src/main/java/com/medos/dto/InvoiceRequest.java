package com.medos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class InvoiceRequest {
    @NotNull
    private UUID patientId;

    private List<UUID> chargeIds;

    private BigDecimal discount;
    private String notes;
}
