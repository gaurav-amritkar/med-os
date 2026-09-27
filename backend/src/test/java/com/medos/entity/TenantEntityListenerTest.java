package com.medos.entity;

import com.medos.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the {@link TenantEntityListener} contract:
 *  1. Persist/update stamps tenant_id from TenantContext when the field is null.
 *  2. Fill-if-null: an explicitly set tenant_id is never overwritten.
 *  3. No tenant context (bootstrap/superadmin) leaves the field null.
 */
class TenantEntityListenerTest {

    private final TenantEntityListener listener = new TenantEntityListener();
    private final UUID tenantId = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void prePersist_stampsTenantFromContext_whenNull() {
        TenantContext.setTenantId(tenantId);
        Patient patient = Patient.builder().build();

        listener.prePersist(patient);

        assertEquals(tenantId, patient.getTenantId());
    }

    @Test
    void preUpdate_stampsTenantFromContext_whenNull() {
        TenantContext.setTenantId(tenantId);
        Charge charge = Charge.builder().build();

        listener.preUpdate(charge);

        assertEquals(tenantId, charge.getTenantId());
    }

    @Test
    void doesNotOverwriteExplicitTenantId() {
        UUID explicit = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        Patient patient = Patient.builder().tenantId(explicit).build();

        listener.prePersist(patient);
        listener.preUpdate(patient);

        assertEquals(explicit, patient.getTenantId(),
                "explicit stamp must win over TenantContext");
    }

    @Test
    void leavesNull_whenNoTenantContext() {
        // Bootstrap / superadmin flows run without a tenant context.
        Patient patient = Patient.builder().build();

        listener.prePersist(patient);

        assertNull(patient.getTenantId());
    }

    @Test
    void ignoresNonTenantOwnedEntities() {
        User user = new User();

        // Must not throw and must not touch unrelated entities.
        listener.prePersist(user);
    }
}
