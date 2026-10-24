package com.medos.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * A DPDP consent event captured after registration.
 *
 * <p>Consent is not a boolean that gets flipped: every grant or withdrawal is a
 * new, append-only row, so the history answers "who said yes, when, to which
 * notice". {@code granted=false} records a withdrawal rather than deleting the
 * earlier grant.
 */
@Data
public class PatientConsentRequest {

    /** True grants, false withdraws. Null is rejected rather than guessed. */
    @NotNull(message = "granted is required")
    private Boolean granted;

    /** What the patient was told the data would be used for. */
    private String purpose;

    /**
     * Notice version shown to the patient. Optional; the server stamps its
     * current version when omitted and refuses a version it does not know, so a
     * client cannot backdate a consent.
     */
    private String noticeVersion;
}
