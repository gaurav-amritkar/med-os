package com.medos.config;

import com.medos.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G16 closure (Wave-1 substrate): the inspector scopes *writes* too, not just reads.
 *
 * <p>Reads were the original coverage; writes relied on services remembering to set
 * {@code tenantId}, and a forgotten assignment was invisible at the SQL boundary. So
 * the same root-FROM rule now applies to UPDATE/DELETE (the predicate is folded into
 * the existing WHERE, or becomes it), and an INSERT into a tenant table that omits
 * {@code tenant_id} fails loudly instead of writing an orphan row nobody's reads can
 * ever see. Hibernate-issued inserts always name every mapped column, so the fail
 * loud only fires for foreign SQL — native repository queries, hand-written tools.
 */
class TenantStatementInspectorWriteScopingTest {

    private static final UUID TENANT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @BeforeEach
    void tenantInScope() {
        TenantContext.setTenantId(TENANT);
    }

    @AfterEach
    void cleanScope() {
        TenantContext.clear();
    }

    private final TenantStatementInspector inspector = new TenantStatementInspector();

    @Test
    @DisplayName("an UPDATE on a tenant table gets the tenant predicate folded into its WHERE")
    void updateWithWhereIsScoped() {
        String sql = "update appointments set status='CHECKED_IN' where id='c1'";
        assertThat(inspector.inspect(sql))
                .isEqualTo("update appointments set status='CHECKED_IN' where appointments.tenant_id = '" + TENANT
                        + "' and id='c1'");
    }

    @Test
    @DisplayName("an UPDATE with no WHERE clause (a bulk write) is still fully scoped")
    void updateWithoutWhereIsScoped() {
        String sql = "update opd_queue set status='CALLED'";
        assertThat(inspector.inspect(sql))
                .isEqualTo("update opd_queue set status='CALLED' where opd_queue.tenant_id = '" + TENANT + "'");
    }

    @Test
    @DisplayName("a DELETE on a tenant table gets the tenant predicate folded into its WHERE")
    void deleteWithWhereIsScoped() {
        String sql = "delete from prescriptions where id='p9'";
        assertThat(inspector.inspect(sql))
                .isEqualTo("delete from prescriptions where prescriptions.tenant_id = '" + TENANT + "' and id='p9'");
    }

    @Test
    @DisplayName("a DELETE with no WHERE is scoped to the tenant, not the world")
    void deleteWithoutWhereIsScoped() {
        String sql = "delete from charges";
        assertThat(inspector.inspect(sql)).isEqualTo("delete from charges where charges.tenant_id = '" + TENANT + "'");
    }

    @Test
    @DisplayName("writes on non-tenant tables pass through untouched")
    void writesOnNonTenantTablesAreUntouched() {
        assertThat(inspector.inspect("update users set status='SUSPENDED' where id='u1'"))
                .isEqualTo("update users set status='SUSPENDED' where id='u1'");
        assertThat(inspector.inspect("delete from tenants where id='t0'"))
                .isEqualTo("delete from tenants where id='t0'");
    }

    @Test
    @DisplayName("an INSERT into a tenant table without tenant_id fails loudly")
    void insertWithoutTenantIdIsRefused() {
        assertThatThrownBy(() -> inspector.inspect(
                "insert into appointments (id, patient_id, doctor_id, scheduled_at) values ('a1','p1','d1',now())"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_id");
    }

    @Test
    @DisplayName("an INSERT that names tenant_id passes through unchanged")
    void insertWithTenantIdPassesThrough() {
        String sql = "insert into appointments (id, tenant_id, patient_id) values ('a1','" + TENANT + "','p1')";
        assertThat(inspector.inspect(sql)).isEqualTo(sql);
    }

    @Test
    @DisplayName("an INSERT into a non-tenant table passes through unchanged")
    void insertOnNonTenantTableIsUntouched() {
        String sql = "insert into tenants (id, name) values ('t1','clinic')";
        assertThat(inspector.inspect(sql)).isEqualTo(sql);
    }

    @Test
    @DisplayName("with no tenant in scope (superadmin), writes pass through unchanged")
    void superadminWritesAreUnchanged() {
        TenantContext.clear();
        String sql = "update appointments set status='CHECKED_IN' where id='c1'";
        assertThat(inspector.inspect(sql)).isEqualTo(sql);
    }

    @Test
    @DisplayName("the tenant literal is injected when a SET value contains the word where")
    void whereKeywordInsideSetValueIsNotTopLevel() {
        String sql = "update charges set invoice_note='asked where the ward is' where id='e2'";
        assertThat(inspector.inspect(sql))
                .isEqualTo("update charges set invoice_note='asked where the ward is' where charges.tenant_id = '"
                        + TENANT + "' and id='e2'");
    }
}
