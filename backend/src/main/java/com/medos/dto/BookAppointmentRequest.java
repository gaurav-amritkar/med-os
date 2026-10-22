package com.medos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Booking an appointment from the front desk. The visit moment is a
 * datetime-local input rendered as {@code appointmentDate}; {@code type} and
 * {@code reason} feed the board's Time & Type column.
 */
public record BookAppointmentRequest(
        @NotNull UUID patientId,
        @NotNull UUID doctorId,
        /** ISO local date-time, e.g. 2026-10-23T14:30 (the page sends datetime-local). */
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?") String appointmentDate,
        @NotBlank @Pattern(regexp = "first_visit|follow_up") String type,
        @NotNull @Size(max = 2000) String reason
) {
}
