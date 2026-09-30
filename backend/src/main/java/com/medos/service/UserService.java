package com.medos.service;

import com.medos.dto.UserDTO;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.repository.TenantUserRepository;
import com.medos.repository.UserRepository;
import com.medos.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
