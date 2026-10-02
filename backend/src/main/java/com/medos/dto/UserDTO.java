package com.medos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A staff member, as seen by one tenant.
 *
 * <p>{@code role} is the role held <em>in the querying tenant</em>, not a
 * property of the user: the same person may be an admin in one clinic and a
 * doctor in another, and a caller asking "who works here" needs the role that
 * applies here.
 *
 * <p>Deliberately not the {@code User} entity. That entity carries
 * {@code passwordHash}, and returning it from a listing endpoint means any
 * future change to the serialisation config becomes a credential disclosure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private UUID id;
    private String username;
    private String fullName;
    private String email;
    private String specialization;
    private Boolean active;
    private String role;

    /**
     * True while the account still carries an admin-set password, so the UI can send the
     * user to change it instead of letting them work on a shared secret.
     */
    private Boolean mustChangePassword;
}
