package com.medos.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.medos.entity.Encounter;
import com.medos.entity.Patient;
import com.medos.entity.Tenant;
import com.medos.repository.EncounterRepository;
import com.medos.repository.KeyRotationRepository;
import com.medos.repository.PatientRepository;
import com.medos.repository.TenantRepository;
import com.medos.util.BlindIndexUtil;
import com.medos.util.EncryptionUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSetMetaData;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Issue #91 against the real tables: re-encryption, tenant isolation, resumable
 * batches, mixed-generation reads, and blind-index rebuild.
 *
 * <p>The acceptance criterion is tenant isolation, so the strongest assertion here is
 * a checksum of every keyring and patient column for a <em>second</em> tenant before
 * and after rotating the first. A rotation that reaches across tenants would change
 * that checksum, and no amount of "the target tenant still reads" would notice.
 */
@SpringBootTest
@ActiveProfiles("test")
class JdbcDekReencryptorTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private EncounterRepository encounterRepository;

    @Autowired
    private KeyRotationRepository rotations;

    @Autowired
    private TenantKeyStore keyStore;

    @Autowired
    private DekReencryptor reencryptor;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private Environment environment;

    @Value("${medos.security.pii-encryption-key}")
    private String kekBase64;

    private JdbcTemplate jdbc;
    private TenantKeyHolder holder;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM encounters");
        jdbc.update("DELETE FROM patients");
        jdbc.update("DELETE FROM key_rotations");
        TenantContext.clear();

        // Rebuild the production pair against the real store. A unit test earlier in the
        // same JVM may have installed an in-memory holder, and a cached Spring context
        // never re-runs EncryptionConfig to undo it.
        holder = new TenantKeyHolder(kekBase64, keyStore, txManager);
        TenantKeyHolder.setInstance(holder);
        TenantKeyResolverFactory.setInstance(holder);
        EncryptionUtil.init(kekBase64);
    }

    @Test
    @DisplayName("a full rotation re-encrypts one tenant, destroys its old DEK, and leaves another tenant byte-identical")
    void rotatesOnlyTheNamedTenant() throws Exception {
        UUID tenantA = createTenant();
        UUID patientA = addPatient(tenantA, "Asha", "9876500011");
        UUID encounterA = addEncounter(tenantA, patientA, "fever", "viral");
        UUID tenantB = createTenant();
        UUID patientB = addPatient(tenantB, "Bilal", "9876500022");
        addEncounter(tenantB, patientB, "cough", "bronchitis");
        TenantContext.clear();

        // Give B archived history too, so the isolation check covers the archive table.
        holder.rotateDek(tenantB);

        String aCiphertextBefore = patientCiphertext(tenantA);
        java.util.Map<String, String> bBefore = tenantFingerprintParts(tenantB);

        DekRotationService.Result result = service()
                .rotate(tenantA, "ops@example.test", backup(), false);

        assertThat(result.status()).isEqualTo(DekRotationService.Status.COMPLETED);
        assertThat(result.fromGeneration()).isEqualTo(1);
        assertThat(result.toGeneration()).isEqualTo(2);
        assertThat(result.rowsRewritten()).isGreaterThanOrEqualTo(2);
        assertThat(holder.generationFor(tenantA)).isEqualTo(2);
        assertThat(holder.dekForGenerationOrNull(tenantA, 1))
                .as("the old DEK is destroyed only after a full read-back")
                .isNull();

        assertThat(tenantFingerprintParts(tenantB))
                .as("the acceptance criterion: another tenant's keyring and ciphertext are untouched")
                .isEqualTo(bBefore);
        assertThat(patientCiphertext(tenantA))
                .as("the target tenant's ciphertext must actually change")
                .isNotEqualTo(aCiphertextBefore);
        assertThat(countAtGeneration(tenantA, "patients", "name", 1)).isZero();
        assertThat(countAtGeneration(tenantA, "encounters", "chief_complaint", 1)).isZero();

        // The data is not merely re-labelled; it still decrypts through the entity path.
        TenantContext.setTenantId(tenantA);
        try {
            Patient patient = patientRepository.findById(patientA).orElseThrow();
            assertThat(patient.getName()).isEqualTo("Asha");
            assertThat(patient.getPhone()).isEqualTo("9876500011");
            Encounter encounter = encounterRepository.findById(encounterA).orElseThrow();
            assertThat(encounter.getChiefComplaint()).isEqualTo("fever");
            assertThat(encounter.getDiagnosis()).isEqualTo("viral");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("a partially rewritten tenant still reads correctly with mixed d1 and d2 values")
    void partialBatchReadsMixedGenerations() {
        UUID tenant = createTenant();
        UUID first = addPatient(tenant, "Asha", "9876500011");
        UUID second = addPatient(tenant, "Bilal", "9876500022");
        addEncounter(tenant, first, "fever", "viral");
        addEncounter(tenant, second, "cough", "bronchitis");
        TenantContext.clear();

        // Move the key, leave every row on the archived generation.
        holder.rotateDek(tenant);
        assertThat(countAtGeneration(tenant, "patients", "name", 1)).isEqualTo(2);

        // One row per table: enough to leave the tenant genuinely half-rotated.
        DekReencryptor.Batch batch = reencryptor.reencryptBatch(
                tenant, 2, false, DekReencryptor.Cursor.start(), 1);
        assertThat(batch.hasMore()).isTrue();
        assertThat(countAtGeneration(tenant, "patients", "name", 1)).isEqualTo(1);
        assertThat(countAtGeneration(tenant, "patients", "name", 2)).isEqualTo(1);

        TenantContext.setTenantId(tenant);
        try {
            assertThat(patientRepository.findById(first).orElseThrow().getName()).isEqualTo("Asha");
            assertThat(patientRepository.findById(second).orElseThrow().getName()).isEqualTo("Bilal");
        } finally {
            TenantContext.clear();
        }

        // Finish the scan; it must converge rather than revisit the same first row forever.
        DekReencryptor.Cursor cursor = batch.next();
        while (batch.hasMore()) {
            batch = reencryptor.reencryptBatch(tenant, 2, false, cursor, 1);
            cursor = batch.next();
        }

        assertThat(countAtGeneration(tenant, "patients", "name", 1)).isZero();
        assertThat(countAtGeneration(tenant, "encounters", "chief_complaint", 1)).isZero();
        assertThat(reencryptor.verify(tenant, 2, false).passed()).isTrue();
    }

    @Test
    @DisplayName("rotating the blind-index key rebuilds every name index so search still matches")
    void rebuildsNameIndexWhenTheBlindIndexKeyRotates() {
        UUID tenant = createTenant();
        UUID patientId = addPatient(tenant, "Asha", "9876500011");
        TenantContext.clear();

        String oldIndex = jdbc.queryForObject(
                "SELECT name_index FROM patients WHERE id = ?", String.class, patientId);
        assertThat(oldIndex).isNotBlank();

        DekRotationService.Result result = service()
                .rotate(tenant, "ops@example.test", backup(), true);

        assertThat(result.status()).isEqualTo(DekRotationService.Status.COMPLETED);
        assertThat(keyStore.biKeyGenerationOf(tenant)).contains(2);

        String newIndex = jdbc.queryForObject(
                "SELECT name_index FROM patients WHERE id = ?", String.class, patientId);
        assertThat(newIndex)
                .as("a rotated index key must produce a different digest")
                .isNotEqualTo(oldIndex);
        assertThat(newIndex).isEqualTo(
                BlindIndexUtil.indexName("Asha", holder.findBlindIndexKeyOrNull(tenant)));
        assertThat(reencryptor.verify(tenant, 2, true).passed()).isTrue();

        Boolean recorded = jdbc.queryForObject(
                "SELECT bi_key_rotated FROM key_rotations WHERE tenant_id = ?", Boolean.class, tenant);
        assertThat(recorded).isTrue();
    }

    @Test
    @DisplayName("verification reports every value still on the old generation, before anything is destroyed")
    void verificationDetectsUnrewrittenValues() {
        UUID tenant = createTenant();
        UUID patientId = addPatient(tenant, "Asha", "9876500011");
        addEncounter(tenant, patientId, "fever", "viral");
        TenantContext.clear();

        DekReencryptor.Verification before = reencryptor.verify(tenant, 2, false);
        // Two non-null patient values (name, phone) and four encounter values.
        assertThat(before.notAtTarget()).isEqualTo(6);
        assertThat(before.passed()).isFalse();
        assertThat(before.valuesChecked()).isEqualTo(6);

        DekRotationService.Result result = service()
                .rotate(tenant, "ops@example.test", backup(), false);
        assertThat(result.status()).isEqualTo(DekRotationService.Status.COMPLETED);
        assertThat(reencryptor.verify(tenant, 2, false).passed()).isTrue();
    }

    // ------------------------------------------------------------------ fixtures

    private DekRotationService service() {
        return new DekRotationService(TenantKeyResolverFactory.get(), rotations, reencryptor, 1);
    }

    private static DekRotationService.BackupAttestation backup() {
        return new DekRotationService.BackupAttestation(
                "pgdump://nightly/2026-10-24", "a".repeat(64), LocalDateTime.now().minusHours(1));
    }

    private UUID createTenant() {
        Tenant tenant = new Tenant();
        tenant.setName("Rotation Test " + UUID.randomUUID().toString().substring(0, 8));
        tenant.setType(Tenant.TenantType.HOSPITAL);
        tenant.setSlug("rotation-" + UUID.randomUUID().toString().substring(0, 8));
        tenant.setActive(true);
        UUID id = tenantRepository.saveAndFlush(tenant).getId();

        TenantContext.setTenantId(id);
        holder.ensureDekExists();
        holder.ensureBlindIndexKeyExists();
        return id;
    }

    private UUID addPatient(UUID tenantId, String name, String phone) {
        TenantContext.setTenantId(tenantId);
        Patient patient = new Patient();
        patient.setUhid("UHID" + UUID.randomUUID().toString().substring(0, 8));
        patient.setTenantId(tenantId);
        patient.setName(name);
        patient.setPhone(phone);
        patient.setNameIndex(BlindIndexUtil.indexName(
                name, holder.findBlindIndexKeyOrNull(tenantId)));
        patient.setDpdpConsent(true);
        patient.setDpdpConsentAt(LocalDateTime.now());
        patient.setCreatedAt(LocalDateTime.now());
        patient.setUpdatedAt(LocalDateTime.now());
        return patientRepository.saveAndFlush(patient).getId();
    }

    private UUID addEncounter(UUID tenantId, UUID patientId, String complaint, String diagnosis) {
        TenantContext.setTenantId(tenantId);
        Encounter encounter = new Encounter();
        encounter.setTenantId(tenantId);
        encounter.setPatientId(patientId);
        encounter.setDoctorId(UUID.randomUUID());
        encounter.setChiefComplaint(complaint);
        encounter.setDiagnosis(diagnosis);
        encounter.setClinicalNotes("notes-" + complaint);
        encounter.setAiNote("ai-" + complaint);
        return encounterRepository.saveAndFlush(encounter).getId();
    }

    private int countAtGeneration(UUID tenantId, String table, String column, int generation) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE tenant_id = ? AND " + column + " LIKE ?", Integer.class,
                tenantId, "kv1.d" + generation + ":%");
        return count == null ? 0 : count;
    }

    private String patientCiphertext(UUID tenantId) {
        return String.join("|", jdbc.queryForList(
                "SELECT name FROM patients WHERE tenant_id = ? ORDER BY id", String.class, tenantId));
    }

    /**
     * SHA-256 over every column of every keyring and PII row for one tenant, by position
     * so a new column is covered without editing this test.
     */
    private java.util.Map<String, String> tenantFingerprintParts(UUID tenantId) throws Exception {
        java.util.Map<String, String> parts = new java.util.LinkedHashMap<>();
        parts.put("tenant_keys", tableFingerprint(
                "SELECT * FROM tenant_keys WHERE tenant_id = ? ORDER BY tenant_id", tenantId));
        parts.put("tenant_dek_history", tableFingerprint(
                "SELECT * FROM tenant_dek_history WHERE tenant_id = ? ORDER BY tenant_id, dek_generation",
                tenantId));
        parts.put("patients", tableFingerprint(
                "SELECT * FROM patients WHERE tenant_id = ? ORDER BY id", tenantId));
        parts.put("encounters", tableFingerprint(
                "SELECT * FROM encounters WHERE tenant_id = ? ORDER BY id", tenantId));
        return parts;
    }

    private String tableFingerprint(String query, UUID tenantId) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        jdbc.query(query, rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                Object value = rs.getObject(i);
                if (value instanceof byte[] bytes) {
                    // Keyring columns are BYTEA: hash the contents, not Object.toString's
                    // identity hash, or every read of the same row looks different.
                    digest.update(bytes);
                } else {
                    digest.update((value == null ? "<null>" : value.toString())
                            .getBytes(StandardCharsets.UTF_8));
                }
                digest.update((byte) 0x1f);
            }
            digest.update((byte) 0x1e);
        }, tenantId);
        return HexFormat.of().formatHex(digest.digest());
    }
}
