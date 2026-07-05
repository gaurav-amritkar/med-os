package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "payment_number", unique = true, nullable = false, length = 32)
    private String paymentNumber;

    @Column(name = "invoice_id", columnDefinition = "uuid")
    private UUID invoiceId;

    @Column(name = "patient_id", nullable = false, columnDefinition = "uuid")
    private UUID patientId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private PaymentMethod paymentMethod;

    @Column(name = "transaction_ref", length = 128)
    private String transactionRef;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Status status = Status.success;

    @Column(name = "received_by", columnDefinition = "uuid")
    private UUID receivedBy;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) receivedAt = LocalDateTime.now();
        if (status == null) status = Status.success;
    }

    public enum PaymentMethod {
        cash, card, upi, netbanking, insurance, cheque
    }

    public enum Status {
        success, pending, failed, refunded
    }
}
