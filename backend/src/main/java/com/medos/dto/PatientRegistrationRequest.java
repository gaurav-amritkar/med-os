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

    @NotNull
    private Boolean dpdpConsent;
    private String consentPurpose;
}
