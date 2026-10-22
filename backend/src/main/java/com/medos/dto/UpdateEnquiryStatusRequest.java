package com.medos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Moving an enquiry across the desk's lifecycle. The note is the follow-up
 * promise (call-back time, who was reached) and lands in the audited history.
 */
public record UpdateEnquiryStatusRequest(
        @NotBlank @Pattern(regexp = "new_enquiry|follow_up|converted|cancelled") String status,
        /** Optional context (“spoke to her, call back Tuesday”); lands in the audited history. */
        @Size(max = 2000) String note
) {
}
