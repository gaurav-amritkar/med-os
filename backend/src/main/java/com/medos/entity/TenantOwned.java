package com.medos.entity;

import java.util.UUID;

/**
 * Marks an entity that is owned by a tenant and carries a {@code tenant_id} column.
 *
 * <p>Implementations satisfy this interface via Lombok-generated
 * {@code getTenantId()}/{@code setTenantId(UUID)} accessors. The
 * {@link TenantEntityListener} stamps {@code tenant_id} from
 * {@link com.medos.security.TenantContext} on persist/update when the field
 * is still null, closing the write-side of tenant isolation
 * ({@link com.medos.config.TenantStatementInspector} covers the read side).
 *
 * <p>The implementor set must stay aligned with {@code TENANT_TABLES} in
 * {@code TenantStatementInspector} — these are the same tables.
 */
public interface TenantOwned {

    UUID getTenantId();

    void setTenantId(UUID tenantId);
}
