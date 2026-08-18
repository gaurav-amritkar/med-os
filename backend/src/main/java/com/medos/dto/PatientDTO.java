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
public class PatientDTO {
    private UUID id;
    private String uhid;
    private String name;
    private Integer age;
    private String gender;
    private String phone;
    private String email;
    private String address;
    private String bloodGroup;
    private Boolean dpdpConsent;
    private LocalDateTime dpdpConsentAt;
    private BigDecimal outstanding;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}