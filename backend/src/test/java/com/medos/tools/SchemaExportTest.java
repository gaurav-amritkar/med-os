package com.medos.tools;

import com.medos.entity.*;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manual tool: exports the exact PostgreSQL DDL Hibernate derives from the
 * entities, so {@code database/migrations/V2__initial_schema.sql} can be
 * regenerated after entity changes and {@code ddl-auto: validate} keeps passing.
 *
 * <p>Run: {@code mvn test -Dtest=SchemaExportTest -Dmedos.gen-schema=true}
 * — output is written to {@code target/schema-pg.sql}. Copy the table/index
 * statements (no sequences — those are V1) into the migration.
 *
 * <p>Disabled by default so CI never writes files as a side effect.
 */
@EnabledIfSystemProperty(named = "medos.gen-schema", matches = "true")
class SchemaExportTest {

    @Test
    void exportPostgresDdl() throws Exception {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
                .applySetting("jakarta.persistence.schema-generation.scripts.create-target",
                        "target/schema-pg.sql")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            for (Class<?> entity : ENTITIES) {
                sources.addAnnotatedClass(entity);
            }
            Metadata metadata = sources.buildMetadata();
            metadata.buildSessionFactory().close();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }

        Path out = Path.of("target/schema-pg.sql");
        assertTrue(Files.exists(out), "schema-pg.sql was not generated");
        String sql = Files.readString(out);
        assertTrue(sql.contains("create table"), "no create table statements generated");
        System.out.println("Generated " + out.toAbsolutePath() + " (" + sql.length() + " chars)");
    }

    private static final Class<?>[] ENTITIES = {
            Tenant.class, TenantConfig.class, TenantUser.class,
            User.class, Patient.class, Appointment.class, Encounter.class,
            Prescription.class, LabOrder.class, Room.class, Admission.class,
            MedicineCatalog.class, MedicineBatch.class, StockTransaction.class,
            Charge.class, Invoice.class, Payment.class, Consent.class,
            OpdQueue.class, Notification.class, AuditLog.class,
            DiseaseMedicineMap.class
    };
}
