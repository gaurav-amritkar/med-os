package com.medos.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DischargeSummaryAddendumRequest {
    private String text;
    private UUID authoredBy;
    private String authorName;
}