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
public class InvoiceDTO {
    private UUID id;
    private Long version;
    private String invoiceNumber;
    private UUID patientId;
    private LocalDateTime invoiceDate;
    private BigDecimal subtotal;
    private BigDecimal gstTotal;
    private BigDecimal discount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private String status;
    private UUID generatedBy;
    private String notes;
    private LocalDateTime createdAt;
}