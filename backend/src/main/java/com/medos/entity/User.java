package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(unique = true, nullable = false, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 128)
    private String fullName;

    @Column(unique = true, length = 128)
    private String email;

    @Column(length = 128)
    private String specialization;


    // @Builder.Default is required, not decorative: Lombok's builder ignores a plain field
    // initialiser, so without it every builder-built User carries null into a NOT NULL
    // column. Onboarding and the bootstrap runner both build this way.
    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /**
     * True while the account still carries an admin-set password.
     *
     * <p>That password was chosen by one person and handed to another, so until its owner
     * replaces it, two people know it. Cleared once they choose their own.
     */
    @Builder.Default
    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = false;

    /**
     * True only for the platform account that provisions tenants. Holds no {@code
     * tenant_users} row and signs in with {@code tenantId=null}. Anyone receiving this
     * flag must tread carefully: without it there is no horizontal scoping, so the
     * controller-level role gate for {@code super_admin} creation is the only thing
     * preventing a tenant admin from minting their own.
     */
    @Builder.Default
    @Column(name = "is_super_admin", nullable = false)
    private Boolean isSuperAdmin = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (active == null) active = true;
    }
}
