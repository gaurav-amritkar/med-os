package com.medos.entity;

import com.medos.security.TenantContext;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * Stamps {@code tenant_id} on {@link TenantOwned} entities from
 * {@link TenantContext} at write time, so every insert carries the tenant
 * even when the calling service forgets to set it.
 *
 * <p><b>Fill-if-null only:</b> an explicitly set {@code tenantId} is never
 * overwritten. This keeps bootstrap/superadmin flows (no tenant context)
 * working and makes explicit stamps from services authoritative.
 *
 * <p>{@link PreUpdate} is included so legacy rows persisted before this
 * listener existed get backfilled when they are next modified.
 */
public class TenantEntityListener {

    @PrePersist
    public void prePersist(Object entity) {
        stampIfAbsent(entity);
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        stampIfAbsent(entity);
    }

    private void stampIfAbsent(Object entity) {
        if (entity instanceof TenantOwned owned && owned.getTenantId() == null) {
            owned.setTenantId(TenantContext.getTenantId().orElse(null));
        }
    }
}
