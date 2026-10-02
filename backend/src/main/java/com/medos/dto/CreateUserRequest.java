package com.medos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.medos.entity.TenantUser;

/**
 * A request to add someone to the acting tenant's staff.
 *
 * <p>The password is set by the admin and handed over in person, because the project has no
 * mailer. That is why the account is created flagged to change it: see
 * {@code User.mustChangePassword}.
 */
public class CreateUserRequest {

    @NotBlank
    @Size(min = 3, max = 64)
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
            message = "Use letters, digits, dot, underscore or hyphen only")
    private String username;

    @NotBlank
    @Size(max = 128)
    private String fullName;

    @NotNull
    private TenantUser.UserRole role;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @Email
    @Size(max = 128)
    private String email;

    @Size(max = 128)
    private String specialization;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public TenantUser.UserRole getRole() {
        return role;
    }

    public void setRole(TenantUser.UserRole role) {
        this.role = role;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    /** Never renders the password, so it cannot reach a log through string building. */
    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + ", role=" + role + "]";
    }
}
