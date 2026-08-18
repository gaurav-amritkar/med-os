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
public class MedicineCatalogDTO {
    private UUID id;
    private String name;
    private String genericName;
    private String manufacturer;
    private String category;
    private String unit;
    private BigDecimal unitPrice;
    private Integer reorderLevel;
    private String keywords;
    private String indications;
    private Boolean active;
    private LocalDateTime createdAt;
}