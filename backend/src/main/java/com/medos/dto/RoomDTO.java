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
public class RoomDTO {
    private UUID id;
    private Long version;
    private String roomNumber;
    private String ward;
    private String roomType;
    private BigDecimal dailyRate;
    private Integer capacity;
    private Boolean occupied;
    private Integer floor;
    private String notes;
    private LocalDateTime createdAt;
}