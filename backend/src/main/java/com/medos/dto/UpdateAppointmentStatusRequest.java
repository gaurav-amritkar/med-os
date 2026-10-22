package com.medos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Moving an appointment across its lifecycle: scheduled → checked_in (token
 * issued) → in_consultation → completed; cancel / no-show from the
 * pre-completion states. Deleting is NOT a status change — it is a separate,
 * terminal-only operation (DELETE /{id}), because a cancelled appointment is a
 * record about a promise until the desk destroys it.
 *
 * <p>{@code status} is the destination; the allowed paths are the service's
 * rulebook. {@code note} is optional context (why it was cancelled).
 */
public record UpdateAppointmentStatusRequest(
        @NotBlank @Pattern(regexp = "checked_in|in_consultation|completed|cancelled|no_show") String status,
        @Size(max = 2000) String note
) {
}
