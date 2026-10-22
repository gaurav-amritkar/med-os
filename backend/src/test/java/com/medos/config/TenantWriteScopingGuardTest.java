package com.medos.config;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guard test behind the write side of tenant isolation (roadmap working
 * order #4; parity-spec risk register: Critical).
 *
 * <p>{@link TenantStatementInspector} scopes reads only. Writes are stamped by
 * {@link com.medos.entity.TenantEntityListener} — but only for entities that
 * implement {@link com.medos.entity.TenantOwned} and register the listener.
 * Nothing in Hibernate enforces that pairing: an entity mapped to a tenant
 * table without both halves silently writes {@code tenant_id IS NULL} rows,
 * invisible to every tenant and readable by full-table superadmin queries.
 *
 * <p>So the alignment is guarded here in both directions:
 * <ol>
 *   <li>every {@code @Entity} that implements {@code TenantOwned} is listed in
 *       {@code TENANT_TABLES} (reads scoped) and carries the listener (writes
 *       stamped);</li>
 *   <li>every table listed in {@code TENANT_TABLES} is actually mapped by a
 *       {@code TenantOwned} entity (no inspector entry without a stamped writer).</li>
 * </ol>
 *
 * <p>The first Wave 1 entity that forgets either half fails this test before
 * it can ship a cross-tenant write path.
 */
class TenantWriteScopingGuardTest {

    private static final PathMatchingResourcePatternResolver RESOLVER =
            new PathMatchingResourcePatternResolver();

    /** All classes annotated {@code @Entity} in the entity package. */
    private static List<Class<?>> entityClasses() throws Exception {
        List<Class<?>> entities = new ArrayList<>();
        for (var resource : RESOLVER.getResources("classpath*:com/medos/entity/*.class")) {
            URI uri = resource.getURI();
            if (!uri.getScheme().equals("file")) continue;
            String className = Path.of(uri).getFileName().toString().replace(".class", "");
            Class<?> clazz = Class.forName("com.medos.entity." + className);
            int mods = clazz.getModifiers();
            if (Modifier.isAbstract(mods) || clazz.isRecord()) continue;
            if (clazz.isAnnotationPresent(Entity.class)) entities.add(clazz);
        }
        // Same guard philosophy as the design-system walk: refuse to pass vacuously.
        assertThat(entities.size()).isGreaterThan(10);
        return entities;
    }

    private static String tableName(Class<?> entity) {
        Table table = entity.getAnnotation(Table.class);
        return table != null ? table.name() : entity.getSimpleName() + "_table";
    }

    @Test
    @DisplayName("every TenantOwned entity is scope-listed and listener-stamped")
    void tenantOwnedEntitiesAreReadScopedAndWriteStamped() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> entity : entityClasses()) {
            boolean owned = com.medos.entity.TenantOwned.class.isAssignableFrom(entity);
            boolean scoped = TenantStatementInspector.tenantTables().contains(tableName(entity));
            EntityListeners listeners = entity.getAnnotation(EntityListeners.class);
            boolean stamped = listeners != null
                    && Set.of(listeners.value()).contains(com.medos.entity.TenantEntityListener.class);

            if (owned && !scoped) {
                violations.add(entity.getSimpleName() + " writes tenant_id but its table '"
                        + tableName(entity) + "' is missing from TenantStatementInspector.TENANT_TABLES"
                        + " — reads would return other tenants' rows");
            }
            if (owned && !stamped) {
                violations.add(entity.getSimpleName() + " implements TenantOwned but does not"
                        + " register TenantEntityListener — tenant_id stays NULL on insert, a"
                        + " cross-tenant write");
            }
            if (!owned && scoped) {
                violations.add(entity.getSimpleName() + " maps tenant-owned table '"
                        + tableName(entity) + "' but does not implement TenantOwned — nothing"
                        + " stamps tenant_id on its writes");
            }
        }
        assertThat(violations).as(String.join("\n", violations)).isEmpty();
    }

    @Test
    @DisplayName("every table in the inspector's list maps to a real entity")
    void inspectorListHasNoOrphanEntries() throws Exception {
        Set<String> mappedTables = new HashSet<>();
        for (Class<?> entity : entityClasses()) {
            mappedTables.add(tableName(entity));
        }
        List<String> orphans = TenantStatementInspector.tenantTables().stream()
                .filter(t -> !mappedTables.contains(t))
                .map(t -> "TENANT_TABLES lists '" + t + "' but no @Entity maps it")
                .toList();
        assertThat(orphans).as(String.join("\n", orphans)).isEmpty();
    }

    @Test
    @DisplayName("entities in the entity package all live in the well-known package (walk sanity)")
    void entityWalkCoversTheKnownSet() throws Exception {
        // Anchor so refactors that move entities force this test to catch up
        // rather than quietly scanning nothing.
        assertThat(entityClasses()).extracting(Class::getSimpleName)
                .contains("Patient", "Invoice", "MedicineCatalog", "Admission");
    }
}
