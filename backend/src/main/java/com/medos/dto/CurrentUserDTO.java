package com.medos.dto;

import com.medos.entity.TenantUser;
import com.medos.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * The authenticated caller's own account.
 *
 * <p>Deliberately not the {@code User} entity, and deliberately not
 * {@link UserDTO}. {@code /users/me} returned the entity, so Jackson serialised
 * {@code passwordHash} to the client: any authenticated user could read their own
 * bcrypt hash, which permits offline cracking with no rate limit or lockout. The
 * entity is a persistence type, not an API type, and the same mistake is easy to
 * repeat because a field only has to be added for it to leak.
 *
 * <p>{@code UserDTO} is a listing projection — its {@code role} is the role held
 * <em>in the queried tenant</em>, which is only meaningful for a list of other
 * staff. A caller asking about themselves needs their own identity plus the tenant
 * they are acting in, so this type carries {@code tenantId} and {@code
 * tenantName} rather than a tenant-scoped role.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserDTO {

    private UUID id;
    private String username;
    private String fullName;
    private String email;
    private String specialization;
    private Boolean active;
    private UUID tenantId;
    private String tenantName;

    /** Modules enabled for this hospital (#127). Null for super-admin (no tenant). */
    private java.util.List<String> features;
    private TenantUser.UserRole role;

    /**
     * Build the response for a caller, from their user record and the membership
     * that selected the acting tenant.
     *
     * @param user     the authenticated user; never a source of the hash
     * @param tenantId the tenant the request is acting in
     * @param tenantName display name of that tenant
     * @param role     the role the user holds in that tenant
     */
    public static CurrentUserDTO of(User user, UUID tenantId, String tenantName,
                                    TenantUser.UserRole role, java.util.List<String> features) {
        if (user == null) {
            return null;
        }
        return CurrentUserDTO.builder()
                .features(features)
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .specialization(user.getSpecialization())
                .active(user.getActive())
                .tenantId(tenantId)
                .tenantName(tenantName)
                .role(role)
                .build();
    }
}
