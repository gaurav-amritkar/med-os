package com.medos.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PatientRegistrationRequest {
    @NotBlank
    @Size(min = 2, max = 128)
    private String name;

    @NotNull
    @Min(0)
    @Max(150)
    private Integer age;

    @NotBlank
    private String gender;

    @NotBlank
    @Pattern(regexp = "^[0-9+\\-\\s()]{7,20}$")
    private String phone;

    @Email
    private String email;

    private String address;
    private String bloodGroup;

    /**
     * Consent is decoupled from registration (Wave 0, medworks-parity design):
     * optional, not a precondition. When true, the consent is written to the
     * {@code consents} table as an auditable event; when omitted, the patient
     * registers with no consent captured and no event is fabricated for them.
     */
    private Boolean dpdpConsent;
    private String consentPurpose;
}
