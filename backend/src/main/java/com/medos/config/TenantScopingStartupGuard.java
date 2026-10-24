package com.medos.config;

import com.medos.entity.TenantEntityListener;
import com.medos.entity.TenantOwned;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.metamodel.EntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fails application startup when the tenant-isolation wiring is incomplete.
 *
 * <p>{@link TenantStatementInspector} scopes SQL and
 * {@link TenantEntityListener} stamps writes — but only for entities that both
 * implement {@link TenantOwned} and register the listener. Until now that
 * pairing was only verified by {@code TenantWriteScopingGuardTest}, which runs in
 * CI. A runtime assembled differently (a hand-built jar, a hot-fixed class, an
 * entity merged without the test run) could still boot and write rows no
 * tenant-scoped read can see.
 *
 * <p>This bean walks the JPA metamodel — what Hibernate actually mapped, not
 * what a classpath scan happens to find — and refuses to start on any of:
 *
 * <ul>
 *   <li>a {@link TenantOwned} entity whose table is missing from
 *       {@link TenantStatementInspector#tenantTables()} (reads would cross
 *       tenants);</li>
 *   <li>a {@link TenantOwned} entity without {@link TenantEntityListener}
 *       ({@code tenant_id} stays NULL on insert);</li>
 *   <li>an entity that maps a scalar {@code tenant_id} column but does not
 *       implement {@link TenantOwned}, unless it is listed in
 *       {@link #NON_OWNED_TENANT_TABLES};</li>
 *   <li>an entity that maps a tenant-scoped table without implementing
 *       {@link TenantOwned} (nothing stamps its writes);</li>
 *   <li>a table listed as tenant-scoped that no mapped entity uses (drift).</li>
 * </ul>
 *
 * <p>{@code tenant_users} is a deliberate member of the allowlist: it is the
 * membership link between a user and a hospital, read during login before a
 * tenant context exists, so the inspector cannot scope it.
 *
 * <p>The key-management tables are allowlisted rather than exempted by accident:
 * {@code tenant_keys} and {@code tenant_dek_history} use {@code tenant_id} as
 * their <em>primary key</em> and are read and written by the key services under
 * an explicitly selected tenant, never by a caller's flush cycle;
 * {@code key_rotations} also holds fleet-wide rows whose {@code tenant_id} is
 * NULL. Adding a business table to that allowlist should be treated as a bug.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantScopingStartupGuard implements InitializingBean {

    /** Tenant-keyed tables that are administrative rather than tenant-owned. */
    static final Set<String> NON_OWNED_TENANT_TABLES = Set.of(
            "key_rotations", "tenant_dek_history", "tenant_keys", "tenant_users");

    private final EntityManagerFactory entityManagerFactory;

    @Override
    public void afterPropertiesSet() {
        List<EntityShape> mappedEntities = mappedEntities();
        List<String> violations = validate(mappedEntities);
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Tenant scoping is misconfigured; refusing to start because these entities "
                            + "would read or write rows outside the tenant boundary:\n - "
                            + String.join("\n - ", violations));
        }
        log.info("Tenant scoping startup guard passed: {} mapped entities, {} tenant-scoped tables",
                mappedEntities.size(), TenantStatementInspector.tenantTables().size());
    }

    /** What Hibernate actually mapped. Overridable so tests can inject a bad model. */
    protected List<EntityShape> mappedEntities() {
        return entityManagerFactory.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .map(TenantScopingStartupGuard::shapeOf)
                .toList();
    }

    /** Pure check over mapped shapes, so a violation can be proven without a context. */
    static List<String> validate(Collection<EntityShape> entities) {
        List<String> violations = new ArrayList<>();
        Set<String> mappedTables = new HashSet<>();
        for (EntityShape entity : entities) {
            mappedTables.add(entity.table());
            String table = entity.table();

            if (entity.tenantOwned() && !TenantStatementInspector.tenantTables().contains(table)) {
                violations.add(entity.describe() + " implements TenantOwned but its table is"
                        + " missing from TenantStatementInspector.TENANT_TABLES — reads would"
                        + " return other tenants' rows");
            }
            if (entity.tenantOwned() && !entity.listenerStamped()) {
                violations.add(entity.describe() + " implements TenantOwned but does not"
                        + " register TenantEntityListener — tenant_id stays NULL on insert, a"
                        + " cross-tenant write");
            }
            if (!entity.tenantOwned() && entity.mapsTenantColumn()
                    && !NON_OWNED_TENANT_TABLES.contains(table)) {
                violations.add(entity.describe() + " maps a tenant_id column but does not"
                        + " implement TenantOwned — nothing stamps tenant_id on its writes"
                        + " (add it to NON_OWNED_TENANT_TABLES only if it is tenant-keyed"
                        + " administrative data)");
            }
            if (!entity.tenantOwned() && TenantStatementInspector.tenantTables().contains(table)) {
                violations.add(entity.describe() + " maps tenant-scoped table '" + table
                        + "' but does not implement TenantOwned — nothing stamps tenant_id on"
                        + " its writes");
            }
        }

        for (String table : TenantStatementInspector.tenantTables()) {
            if (!mappedTables.contains(table)) {
                violations.add("TenantStatementInspector.TENANT_TABLES lists '" + table
                        + "' but no mapped entity uses it — the list has drifted from the model");
            }
        }
        violations.sort(Comparator.naturalOrder());
        return violations;
    }

    /** The facts the guard needs, extracted from a mapped entity class. */
    record EntityShape(String entityName, String table, boolean tenantOwned,
                       boolean listenerStamped, boolean mapsTenantColumn) {

        String describe() {
            return entityName + " ('" + table + "')";
        }
    }

    static EntityShape shapeOf(Class<?> entity) {
        return new EntityShape(
                entity.getSimpleName(),
                tableName(entity),
                TenantOwned.class.isAssignableFrom(entity),
                registersListener(entity),
                declaresTenantIdColumn(entity));
    }

    private static String tableName(Class<?> entity) {
        Table table = entity.getAnnotation(Table.class);
        return table != null && !table.name().isBlank() ? table.name() : entity.getSimpleName();
    }

    private static boolean registersListener(Class<?> entity) {
        EntityListeners listeners = entity.getAnnotation(EntityListeners.class);
        return listeners != null && Set.of(listeners.value()).contains(TenantEntityListener.class);
    }

    /**
     * True when the entity maps a scalar {@code tenant_id} column, whether through a
     * {@code tenantId} field or an explicit {@code @Column(name = "tenant_id")}.
     * Association join columns are deliberately ignored: membership links such as
     * {@code tenant_users} point at a tenant without being owned by it. Walks the
     * hierarchy so a mapped superclass cannot hide the column.
     */
    private static boolean declaresTenantIdColumn(Class<?> entity) {
        for (Class<?> type = entity; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(Transient.class)) {
                    continue;
                }
                Column column = field.getAnnotation(Column.class);
                if (column != null && "tenant_id".equals(column.name())) {
                    return true;
                }
                // Default JPA naming maps a tenantId field to tenant_id.
                if (column == null && "tenantId".equals(field.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
