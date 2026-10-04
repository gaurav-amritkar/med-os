package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "tenant_users", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "tenant_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantUser {
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    /**
     * Whether this person may sign in to this hospital.
     *
     * <p>Deliberately per membership rather than on {@code User}: one clinician may work
     * at two clinics, and revoking access at one must not revoke it at the other.
     */
    // @Builder.Default, because Lombok's builder ignores a plain initialiser: a
    // builder-built membership would carry null into a NOT NULL column.
    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    public boolean isActive() {
        return !Boolean.FALSE.equals(active);
    }

    public enum UserRole {
        admin, doctor, nurse, receptionist, pharmacist, billing, super_admin
    }
}