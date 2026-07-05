package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "medicine_batches",
        uniqueConstraints = @UniqueConstraint(columnNames = {"medicine_id", "batch_no"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicineBatch {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "medicine_id", nullable = false, columnDefinition = "uuid")
    private UUID medicineId;

    @Column(name = "batch_no", nullable = false, length = 64)
    private String batchNo;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "remaining_qty", nullable = false)
    private Integer remainingQty = 0;

    @Column(name = "purchase_price", precision = 10, scale = 2)
    private BigDecimal purchasePrice;

    @Column(length = 128)
    private String supplier;

    @Column(name = "received_date")
    private LocalDateTime receivedDate;

    @PrePersist
    protected void onCreate() {
        if (receivedDate == null) receivedDate = LocalDateTime.now();
        if (remainingQty == null) remainingQty = 0;
    }
}
