package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "medicine_catalog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicineCatalog {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(unique = true, nullable = false, length = 128)
    private String name;

    @Column(name = "generic_name", length = 128)
    private String genericName;

    @Column(length = 128)
    private String manufacturer;

    @Column(length = 64)
    private String category;

    @Column(length = 32)
    private String unit;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "reorder_level")
    private Integer reorderLevel = 10;

    @Column(columnDefinition = "TEXT")
    private String keywords;

    @Column(columnDefinition = "TEXT")
    private String indications;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (active == null) active = true;
        if (reorderLevel == null) reorderLevel = 10;
    }
}
