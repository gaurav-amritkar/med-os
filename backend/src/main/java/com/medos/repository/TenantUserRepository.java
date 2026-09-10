package com.medos.repository;

import com.medos.entity.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {
    Optional<TenantUser> findByUserIdAndTenantId(UUID userId, UUID tenantId);
    List<TenantUser> findByUserId(UUID userId);
    List<TenantUser> findByTenantId(UUID tenantId);
}