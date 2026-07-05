package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "invoice_number", unique = true, nullable = false, length = 32)
    private String invoiceNumber;

    @Column(name = "patient_id", nullable = false, columnDefinition = "uuid")
    private UUID patientId;

    @Column(name = "invoice_date")
    private LocalDateTime invoiceDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "gst_total", precision = 10, scale = 2)
    private BigDecimal gstTotal = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Status status = Status.issued;

    @Column(name = "generated_by", columnDefinition = "uuid")
    private UUID generatedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (invoiceDate == null) invoiceDate = LocalDateTime.now();
        if (status == null) status = Status.issued;
        if (gstTotal == null) gstTotal = BigDecimal.ZERO;
        if (discount == null) discount = BigDecimal.ZERO;
        if (paidAmount == null) paidAmount = BigDecimal.ZERO;
    }

    public enum Status {
        draft, issued, paid, partially_paid, cancelled
    }
}
