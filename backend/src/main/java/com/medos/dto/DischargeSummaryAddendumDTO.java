package com.medos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DischargeSummaryAddendumDTO {
    private UUID id;
    private String text;
    private UUID authoredBy;
    private String authorName;
    private LocalDateTime createdAt;
}
