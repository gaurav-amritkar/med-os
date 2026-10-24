package com.medos.service;

import com.medos.entity.Tenant;
import com.medos.repository.TenantRepository;
import com.medos.util.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deactivating a tenant is the action that cuts every person in a hospital
 * off — login rejects them and their issued tokens stop working per request.
 * That action must leave a record of who did it, so the "why can nobody sign
 * in?" support question is answered from the audit log rather than from memory.
 *
 * <p>Issue #53 requires the deactivation to be audited; this pins the record
 * for both directions, including the old-value reported on an idempotent call.
 */
@ExtendWith(MockitoExtension.class)
class TenantLifecycleAuditTest {

    private static final UUID TENANT_ID = UUID.fromString("cccc0000-0000-4000-8000-000000000001");

    @Mock private TenantRepository tenantRepository;
    @Mock private AuditLogger auditLogger;
    @InjectMocks private TenantService tenantService;

    private Tenant tenant(Boolean active) {
        return Tenant.builder()
                .id(TENANT_ID)
                .name("Nilmara Hospital")
                .type(Tenant.TenantType.HOSPITAL)
                .slug("nilmara")
                .active(active)
                .build();
    }

    @BeforeEach
    void persistSaves() {
        when(tenantRepository.findById(TENANT_ID)).thenAnswer(inv -> {
            Tenant t = tenant(true);
            if (inv.getArgument(0) == null) throw new IllegalArgumentException("not found");
            return Optional.of(t);
        });
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("deactivate writes a DEACTIVATE audit row with the old state")
    void deactivateIsAudited() {
        tenantService.deactivateTenant(TENANT_ID);

        verify(auditLogger).log(eq("DEACTIVATE"), eq("tenant"), eq(TENANT_ID.toString()),
                eq("active=true"), eq("active=false"));
    }

    @Test
    @DisplayName("activate writes an ACTIVATE audit row with the old state")
    void activateIsAudited() {
        tenantService.activateTenant(TENANT_ID);

        verify(auditLogger).log(eq("ACTIVATE"), eq("tenant"), eq(TENANT_ID.toString()),
                eq("active=true"), eq("active=true"));
    }

    @Test
    @DisplayName("a null active flag is audited as inactive, not as an accident")
    void nullActiveIsAuditedAsInactive() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant(null)));

        tenantService.deactivateTenant(TENANT_ID);

        verify(auditLogger).log(eq("DEACTIVATE"), eq("tenant"), eq(TENANT_ID.toString()),
                eq("active=false"), eq("active=false"));
    }
}
