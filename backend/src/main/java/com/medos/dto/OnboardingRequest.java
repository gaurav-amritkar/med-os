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
    private Map<String, String> config;
}