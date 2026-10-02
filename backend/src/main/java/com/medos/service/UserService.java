package com.medos.service;

import com.medos.dto.UserDTO;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.repository.TenantUserRepository;
import com.medos.dto.CreateUserRequest;
import com.medos.dto.UserDTO;
import com.medos.entity.Tenant;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.exception.BusinessException;
import com.medos.repository.TenantRepository;
import com.medos.repository.TenantUserRepository;
import com.medos.repository.UserRepository;
import com.medos.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.medos.util.AuditLogger auditLogger;

    /**
     * The staff roster for the tenant in context.
     *
     * <p>A {@code User} has no {@code tenant_id} and is not
     * {@code TenantOwned}, so {@code TenantStatementInspector} adds no tenant
     * predicate to a query on it. Membership is the only link between a user
     * and a tenant, and it lives in {@code tenant_users} — which is itself not
     * inspector-scoped, so the scoping is done explicitly here from the
     * membership rows.
     *
     * <p>With no tenant in context the answer is an empty list, not every user.
     * "No tenant means no restriction" is what turned this endpoint into a
     * cross-tenant disclosure; a later super-admin role must grant the wider
     * view deliberately rather than inherit it from a missing claim.
     */
    @Transactional(readOnly = true)
    public List<UserDTO> listTenantUsers() {
        return TenantContext.getTenantId()
                .map(this::rosterFor)
                .orElseGet(List::of);
    }

    private List<UserDTO> rosterFor(UUID tenantId) {
        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenantId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        // Two queries regardless of roster size. Reading membership.getUser()
        // inside the loop instead would lazy-load once per row; the join
        // columns are needed for the role either way, so the ids are collected
        // first and the users fetched in one go.
        List<UUID> userIds = memberships.stream()
                .map(TenantUser::getUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .distinct()
                .toList();
        Map<UUID, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        return memberships.stream()
                .map(membership -> toDto(membership, usersById))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * A membership's user, tolerating a membership whose user was not pre-fetched.
     *
     * <p>{@code active} is the membership's flag, falling back to the account's own flag for
     * rows written before {@code tenant_users.active} existed.
     */
    private UserDTO toDtoIncludingInactive(TenantUser membership, Map<UUID, User> usersById) {
        UUID userId = membership.getUser() != null ? membership.getUser().getId() : null;
        // Fall back to the membership's own reference: a write path holds exactly one
        // membership and has no reason to pre-fetch a map of every user in the tenant.
        User user = usersById.getOrDefault(userId,
                membership.getUser() != null && membership.getUser().getId() != null
                        ? membership.getUser()
                        : null);
        if (user == null) {
            return null;
        }
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .specialization(user.getSpecialization())
                .active(membership.isActive() && Boolean.TRUE.equals(user.getActive()))
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .role(membership.getRole() != null ? membership.getRole().name() : null)
                .build();
    }

    private UserDTO toDto(TenantUser membership, Map<UUID, User> usersById) {
        User user = usersById.get(membership.getUser() != null ? membership.getUser().getId() : null);
        if (user == null || !Boolean.TRUE.equals(user.getActive())) {
            return null;
        }
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .specialization(user.getSpecialization())
                .active(user.getActive())
                .role(membership.getRole() != null ? membership.getRole().name() : null)
                .build();
    }

    // ---------------------------------------------------------------------
    // Staff management
    //
    // A role belongs to a membership, not to a person: one clinician may work at two
    // clinics and hold a different role at each. So every method here is scoped to the
    // acting tenant, and "deactivate" ends one membership rather than the shared identity.
    // ---------------------------------------------------------------------

    /**
     * Everyone attached to this tenant, including the deactivated.
     *
     * <p>Distinct from {@link #listTenantUsers()}, which hides inactive accounts because
     * its callers are dashboards rather than administration. A staff page has to show a
     * deactivated person, or there is no way to see who left and no way to reactivate them.
     */
    @Transactional(readOnly = true)
    public List<UserDTO> listUsers(UUID tenantId) {
        return rosterIncludingInactive(tenantId);
    }

    /**
     * Add someone to this tenant, creating the account if they do not have one.
     *
     * <p>An existing account gains a membership rather than a second identity, so a doctor
     * who already works at another hospital is not duplicated.
     */
    @Transactional
    public UserDTO createUser(UUID tenantId, CreateUserRequest request) {
        // users.email is UNIQUE. Checked up front so the admin gets a sentence they can act
        // on, rather than a constraint violation surfacing as an opaque 500 at flush.
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            userRepository.findByEmail(request.getEmail()).ifPresent(existing -> {
                if (!existing.getUsername().equalsIgnoreCase(request.getUsername())) {
                    throw new BusinessException(
                            "That email is already used by another account (" + existing.getUsername()
                                    + "). Each account needs its own email so its owner can sign in.");
                }
            });
        }

        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        if (user == null) {
            user = User.builder()
                    .username(request.getUsername())
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .specialization(request.getSpecialization())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    // The admin chose this password and hands it over, so two people know
                    // it until its owner replaces it.
                    .mustChangePassword(true)
                    .active(true)
                    .build();
            user = userRepository.save(user);
        }

        if (tenantUserRepository.findByUserIdAndTenantId(user.getId(), tenantId).isPresent()) {
            throw new BusinessException("User is already part of this organisation");
        }

        TenantUser membership = new TenantUser();
        membership.setUser(user);
        membership.setTenant(requireTenant(tenantId));
        membership.setRole(request.getRole());
        membership.setActive(true);
        TenantUser saved = tenantUserRepository.save(membership);

        auditLogger.log("CREATE", "User", user.getId().toString(), null,
                "Added " + request.getRole() + " " + user.getUsername());

        return toDtoIncludingInactive(saved, Map.of(user.getId(), user));
    }

    /** Change a member's role, refusing to remove the tenant's last working admin. */
    @Transactional
    public UserDTO updateRole(UUID tenantId, UUID userId, TenantUser.UserRole role) {
        TenantUser membership = requireMembership(tenantId, userId);
        if (membership.getRole() == TenantUser.UserRole.admin && role != TenantUser.UserRole.admin) {
            requireAnotherAdminRemains(tenantId, userId, "demote");
        }
        membership.setRole(role);
        TenantUser saved = tenantUserRepository.save(membership);
        auditLogger.log("UPDATE", "User", userId.toString(), null,
                "Role changed to " + role);
        return toDtoIncludingInactive(saved, Map.of());
    }

    /**
     * End or restore this person's access to this hospital.
     *
     * <p>Scoped to the membership on purpose. Flipping the shared {@code User.active} row
     * would lock a clinician out of every other clinic they work at, which the schema
     * explicitly allows them to hold.
     */
    @Transactional
    public UserDTO setActive(UUID tenantId, UUID userId, boolean active) {
        TenantUser membership = requireMembership(tenantId, userId);
        if (!active && membership.getRole() == TenantUser.UserRole.admin) {
            requireAnotherAdminRemains(tenantId, userId, "deactivate");
        }
        membership.setActive(active);
        TenantUser saved = tenantUserRepository.save(membership);
        auditLogger.log(active ? "ACTIVATE" : "DEACTIVATE", "User", userId.toString(), null,
                "Access " + (active ? "restored" : "ended") + " for this organisation");
        return toDtoIncludingInactive(saved, Map.of());
    }

    /**
     * Refuse an action that would leave the tenant with no way to administer itself.
     *
     * <p>An admin who removes the last admin cannot undo it: the endpoint they would need
     * is the one they just locked themselves out of, and nobody else can call it.
     */
    private void requireAnotherAdminRemains(UUID tenantId, UUID userId, String action) {
        long otherAdmins = tenantUserRepository.findByTenantId(tenantId).stream()
                .filter(m -> m.isActive())
                .filter(m -> m.getRole() == TenantUser.UserRole.admin)
                .filter(m -> m.getUser() != null && !m.getUser().getId().equals(userId))
                .count();
        if (otherAdmins == 0) {
            throw new BusinessException(
                    "Cannot " + action + " the last active admin: this organisation would have "
                            + "nobody who could administer it. Add or promote another admin first.");
        }
    }

    private TenantUser requireMembership(UUID tenantId, UUID userId) {
        return tenantUserRepository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new BusinessException("User not found in this organisation"));
    }

    private Tenant requireTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Organisation not found"));
    }

    private List<UserDTO> rosterIncludingInactive(UUID tenantId) {
        List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenantId);
        List<UUID> userIds = memberships.stream()
                .map(TenantUser::getUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .distinct()
                .toList();
        Map<UUID, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        List<UserDTO> roster = new ArrayList<>();
        for (TenantUser membership : memberships) {
            UserDTO dto = toDtoIncludingInactive(membership, usersById);
            if (dto != null) {
                roster.add(dto);
            }
        }
        return roster;
    }
}
