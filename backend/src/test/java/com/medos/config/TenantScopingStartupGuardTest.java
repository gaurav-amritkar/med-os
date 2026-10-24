package com.medos.config;

import com.medos.config.TenantScopingStartupGuard.EntityShape;
import com.medos.entity.Patient;
import com.medos.entity.TenantKey;
import com.medos.entity.TenantUser;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * The runtime counterpart to {@code TenantWriteScopingGuardTest}.
 *
 * <p>The CI test asks whether the source tree is aligned; this one asks whether
 * the running application <em>refuses to start</em> when it is not. A guard that
 * only logs would still serve cross-tenant rows, so every case here asserts a
 * failure, not a warning.
 */
class TenantScopingStartupGuardTest {

    /** A context that maps a bad model must die before it accepts traffic. */
    private static class ModelGuard extends TenantScopingStartupGuard {
        private final List<EntityShape> model;

        ModelGuard(List<EntityShape> model) {
            super(mock(EntityManagerFactory.class));
            this.model = model;
        }

        @Override
        protected List<EntityShape> mappedEntities() {
            return model;
        }
    }

    @Test
    @DisplayName("a TenantOwned entity missing from TENANT_TABLES prevents startup")
    void unscopedTenantOwnedEntityPreventsStartup() {
        var guard = new ModelGuard(List.of(
                new EntityShape("Orphan", "orphan_rows", true, true, true)));

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orphan_rows")
                .hasMessageContaining("missing from TenantStatementInspector.TENANT_TABLES");
    }

    @Test
    @DisplayName("a TenantOwned entity without the stamping listener prevents startup")
    void unstampedTenantOwnedEntityPreventsStartup() {
        var guard = new ModelGuard(List.of(
                new EntityShape("Unstamped", "patients", true, false, true)));

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unstamped")
                .hasMessageContaining("does not register TenantEntityListener");
    }

    @Test
    @DisplayName("a business entity with tenant_id but no TenantOwned contract prevents startup")
    void tenantColumnWithoutOwnershipPreventsStartup() {
        var guard = new ModelGuard(List.of(
                new EntityShape("Drifted", "drifted_table", false, false, true)));

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("drifted_table")
                .hasMessageContaining("does not implement TenantOwned");
    }

    @Test
    @DisplayName("a listed tenant table with no owning entity prevents startup")
    void tenantTableWithoutOwningEntityPreventsStartup() {
        var guard = new ModelGuard(List.of(
                new EntityShape("Patients", "patients", false, false, false)));

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not implement TenantOwned");
    }

    @Test
    @DisplayName("a tenant-scoped table missing from the mapped model is reported as drift")
    void orphanInspectorEntryPreventsStartup() {
        var guard = new ModelGuard(List.of(
                TenantScopingStartupGuard.shapeOf(Patient.class)));

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no mapped entity uses it");
    }

    @Test
    @DisplayName("the real mapped entities are aligned")
    void realEntitiesPass() {
        assertThat(TenantScopingStartupGuard.validate(List.of(
                TenantScopingStartupGuard.shapeOf(Patient.class),
                TenantScopingStartupGuard.shapeOf(TenantKey.class),
                TenantScopingStartupGuard.shapeOf(TenantUser.class),
                // Every listed table must appear, or the drift check fires.
                TenantScopingStartupGuard.shapeOf(com.medos.entity.Admission.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.Appointment.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.Charge.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.Encounter.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.Enquiry.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.EnquiryStatusHistory.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.Invoice.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.LabOrder.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.MedicineBatch.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.MedicineCatalog.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.OpdQueue.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.Payment.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.Prescription.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.Room.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.StockTransaction.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.KeyRotation.class),
                TenantScopingStartupGuard.shapeOf(com.medos.entity.TenantDekArchive.class))))
                .isEmpty();
    }

    @Test
    @DisplayName("key-management tables are allowlisted by name, not by accident")
    void keyManagementTablesAreExplicitlyAllowlisted() {
        assertThat(TenantScopingStartupGuard.NON_OWNED_TENANT_TABLES)
                .containsExactlyInAnyOrder("key_rotations", "tenant_dek_history",
                        "tenant_keys", "tenant_users");
    }
}
