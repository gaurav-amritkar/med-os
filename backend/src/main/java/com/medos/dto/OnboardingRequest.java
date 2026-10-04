package com.medos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Map;

@Data
public class OnboardingRequest {
    @NotBlank private String name;
    @NotBlank private String type;
    @NotBlank private String slug;
    @NotBlank @Email private String contactEmail;
    @NotBlank private String contactPhone;
    private String address;
    @NotBlank private String adminUsername;
    @NotBlank private String adminPassword;
    @NotBlank @Email private String adminEmail;
    private String adminFullName;
    /** Modules this hospital opted into (#127). Absent/empty = all of them. */
    private java.util.List<String> features;

    private Map<String, String> config;

    /**
     * Platform registration secret.
     *
     * <p>Interim gate until a {@code super_admin} platform role exists (#54). Creating a
     * tenant mints an {@code admin} membership, so it cannot be something any signed-in user
     * may do — authentication is not authorization, and this endpoint had no authorization
     * at all.
     *
     * <p>Over the wire rather than in a header so a deployment can revoke it by rotating one
     * secret, without a code change.
     */
    private String registrationToken;
}