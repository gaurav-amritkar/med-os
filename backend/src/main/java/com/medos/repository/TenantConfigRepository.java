package com.medos.repository;

import com.medos.entity.Tenant;
import com.medos.entity.TenantConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantConfigRepository extends JpaRepository<TenantConfig, UUID> {
    Optional<TenantConfig> findByTenantAndConfigKey(Tenant tenant, String configKey);
}