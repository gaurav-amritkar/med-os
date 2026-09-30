package com.medos.repository;

import com.medos.entity.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {
    Optional<TenantUser> findByUserIdAndTenantId(UUID userId, UUID tenantId);
    List<TenantUser> findByUserId(UUID userId);
    List<TenantUser> findByTenantId(UUID tenantId);

    /**
     * A user's memberships with the tenant eagerly fetched.
     *
     * <p>{@code tenant} is a LAZY {@code @ManyToOne}, so navigating
     * {@code membership.getTenant()} outside a transaction throws
     * {@code LazyInitializationException}. Login is not transactional and must
     * read the tenant's active flag, so the join is fetched explicitly — one
     * query, no proxy to initialise.
     */
    @Query("select tu from TenantUser tu join fetch tu.tenant where tu.user.id = :userId")
    List<TenantUser> findByUserIdWithTenant(@Param("userId") UUID userId);
}