package com.medos.service;

import com.medos.entity.Tenant;
import com.medos.entity.TenantConfig;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.repository.TenantConfigRepository;
import com.medos.repository.TenantRepository;
import com.medos.repository.TenantUserRepository;
import com.medos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {
    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final UserRepository userRepository;

    public Tenant createTenant(String name, Tenant.TenantType type, String slug, String contactEmail, String contactPhone, String address) {
        if (tenantRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Tenant with slug " + slug + " already exists");
        }
        Tenant tenant = Tenant.builder()
                .name(name)
                .type(type)
                .slug(slug)
                .contactEmail(contactEmail)
                .contactPhone(contactPhone)
                .address(address)
                .active(true)
                .build();
        return tenantRepository.save(tenant);
    }

    public List<Tenant> getAllTenants() {
        return tenantRepository.findAll();
    }

    public Tenant getTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    }

    public void activateTenant(UUID tenantId) {
        Tenant tenant = getTenant(tenantId);
        tenant.setActive(true);
        tenantRepository.save(tenant);
    }

    public void deactivateTenant(UUID tenantId) {
        Tenant tenant = getTenant(tenantId);
        tenant.setActive(false);
        tenantRepository.save(tenant);
    }

    public String getConfig(UUID tenantId, String configKey) {
        Tenant tenant = getTenant(tenantId);
        return tenantConfigRepository.findByTenantAndConfigKey(tenant, configKey)
                .map(TenantConfig::getConfigValue)
                .orElse(null);
    }

    public void setConfig(UUID tenantId, String configKey, String configValue) {
        Tenant tenant = getTenant(tenantId);
        TenantConfig config = tenantConfigRepository.findByTenantAndConfigKey(tenant, configKey)
                .orElse(TenantConfig.builder().tenant(tenant).configKey(configKey).build());
        config.setConfigValue(configValue);
        tenantConfigRepository.save(config);
    }

    public TenantUser assignUserToTenant(UUID userId, UUID tenantId, TenantUser.UserRole role) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Tenant tenant = getTenant(tenantId);
        return tenantUserRepository.save(TenantUser.builder()
                .user(user)
                .tenant(tenant)
                .role(role)
                .build());
    }

    public List<TenantUser> getUserTenants(UUID userId) {
        return tenantUserRepository.findByUserId(userId);
    }
}