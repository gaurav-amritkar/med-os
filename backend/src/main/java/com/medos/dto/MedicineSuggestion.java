package com.medos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class MedicineSuggestion {
    private UUID medicineId;
    private String name;
    private String genericName;
    private String category;
    private String unit;
    private BigDecimal unitPrice;
    private String dosage;
    private String frequency;
    private String duration;
    private Integer relevanceScore;
    private String rationale;
}
