package com.medos.dto;

import jakarta.validation.constraints.Size;

/**
 * The doctor's note on a visit — the board's doc-note surface, distinct from a
 * status change. The appointment comes from the path, not the body.
 */
public record AppointmentNoteRequest(
        @Size(max = 4000) String docNote
) {
}
