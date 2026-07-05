package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "charges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Charge {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "patient_id", nullable = false, columnDefinition = "uuid")
    private UUID patientId;

    @Column(name = "encounter_id", columnDefinition = "uuid")
    private UUID encounterId;

    @Column(name = "admission_id", columnDefinition = "uuid")
    private UUID admissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false, length = 32)
    private ChargeType chargeType;

    @Column(nullable = false, length = 255)
    private String description;

    private Integer quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "gst_percent", precision = 5, scale = 2)
    private BigDecimal gstPercent = BigDecimal.ZERO;

    @Column(name = "gst_amount", precision = 10, scale = 2)
    private BigDecimal gstAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "invoice_id", columnDefinition = "uuid")
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Status status = Status.unbilled;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (quantity == null) quantity = 1;
        if (status == null) status = Status.unbilled;
        if (gstPercent == null) gstPercent = BigDecimal.ZERO;
        if (gstAmount == null) gstAmount = BigDecimal.ZERO;
    }

    public enum ChargeType {
        consultation, pharmacy, room, procedure, lab, misc
    }

    public enum Status {
        unbilled, billed, paid, cancelled
    }
}
