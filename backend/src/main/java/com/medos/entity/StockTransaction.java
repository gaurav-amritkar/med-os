package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransaction {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "medicine_id", nullable = false, columnDefinition = "uuid")
    private UUID medicineId;

    @Column(name = "batch_id", columnDefinition = "uuid")
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 32)
    private TransactionType transactionType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "patient_id", columnDefinition = "uuid")
    private UUID patientId;

    @Column(name = "prescription_id", columnDefinition = "uuid")
    private UUID prescriptionId;

    @Column(name = "reference_no", length = 64)
    private String referenceNo;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "performed_by", columnDefinition = "uuid")
    private UUID performedBy;

    @Column(name = "performed_at")
    private LocalDateTime performedAt;

    @PrePersist
    protected void onCreate() {
        if (performedAt == null) performedAt = LocalDateTime.now();
    }

    public enum TransactionType {
        in, out, adjustment, return_tx
    }
}
