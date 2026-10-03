package com.medos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private long expiresIn;
    private UUID userId;
    private String username;
    private String fullName;
    private String role;
    private String specialization;
    private UUID tenantId;

    /**
     * True while the account still carries an admin-set password.
     *
     * <p>Sent at sign-in rather than only on the user record, because the client has to act
     * on it immediately: this is the one moment a user who was handed a password is about to
     * start working on it.
     */
    private Boolean mustChangePassword;
}
