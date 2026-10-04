package com.medos.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.medos.entity.Tenant;
import com.medos.repository.KeyRotationRepository;
import com.medos.repository.TenantRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSetMetaData;
import java.util.HexFormat;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The whole point of re-wrapping instead of rotating is that patient data is never
 * rewritten. A DEK rotation re-encrypts every patient row and needs a maintenance
 * window; a KEK re-wrap moves only the wrapper in tenant_keys and can run online.
 *
 * <p>That claim is worth nothing unless it is measured, so this test takes a checksum
 * of every column of every patient row before and after a real rotation against real
 * tables, and then re-reads the names to prove the ciphertext is still decryptable.
 */
@SpringBootTest
@ActiveProfiles("test")
class KeyRewrapLeavesPatientDataUntouchedTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantKeyStore keyStore;

    @Autowired
    private KeyRotationRepository rotations;

    @Autowired
    private com.medos.repository.PatientRepository patientRepository;

    @Value("${medos.security.pii-encryption-key}")
    private String currentKekBase64;

    private JdbcTemplate jdbc;
    private KeyRewrapService service;
    private com.medos.util.EncryptionUtil converter = new com.medos.util.EncryptionUtil();

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        service = new KeyRewrapService(keyStore, rotations);
        jdbc.update("DELETE FROM patients");
        TenantContext.clear();
    }

    @Test
    @DisplayName("re-wrapping every tenant leaves every patient row byte-identical")
    void patientRowsAreUntouched() throws Exception {
        UUID tenantA = createTenantWithPatient("Asha", "9876500011");
        UUID tenantB = createTenantWithPatient("Bilal", "9876500022");
        TenantContext.clear();

        String before = patientsChecksum();
        byte[] dekA = keyStore.wrappedDekOf(tenantA).isPresent()
                ? KeyWrapCipher.unwrap(keyStore.wrappedDekOf(tenantA).orElseThrow(), currentKek())
                : null;
        assertThat(dekA).as("tenant A must have a DEK for this test to mean anything").isNotNull();

        byte[] newKek = new byte[32];
        RANDOM.nextBytes(newKek);
        KeyRewrapService.Result result =
                service.rotate(currentKek(), 2, newKek, "operator@example.test");

        assertThat(result.status()).isEqualTo(KeyRewrapService.Status.COMPLETED);
        assertThat(result.rowsRewrapped()).isGreaterThanOrEqualTo(2);

        // The criterion #88 asks for: not one patient byte changed.
        assertThat(patientsChecksum())
                .as("a KEK re-wrap must not touch a single patient column")
                .isEqualTo(before);

        // And the ciphertext is still readable, which the checksum alone would not prove.
        assertThat(KeyWrapCipher.unwrap(keyStore.wrappedDekOf(tenantA).orElseThrow(), newKek))
                .as("the same DEK must come back under the new KEK")
                .isEqualTo(dekA);
        assertThat(nameOf(tenantA)).isEqualTo("Asha");
        assertThat(nameOf(tenantB)).isEqualTo("Bilal");
    }

    @Test
    @DisplayName("the checksum would notice a single changed patient byte")
    void checksumIsSensitive() throws Exception {
        UUID tenant = createTenantWithPatient("Asha", "9876500011");
        TenantContext.clear();
        String before = patientsChecksum();

        // Age is not PII and a rotation must not touch it either. Changing one value proves
        // the checksum in the test above is actually reading the rows, rather than comparing
        // two empty results that happen to be equal.
        jdbc.update("UPDATE patients SET age = 1 WHERE tenant_id = ?", tenant);

        assertThat(patientsChecksum()).isNotEqualTo(before);
    }

    private byte[] currentKek() {
        return java.util.Base64.getDecoder().decode(currentKekBase64);
    }

    private static final String CONSENT_CAPTURED_AT = "2026-01-01T00:00:00";

    private UUID createTenantWithPatient(String name, String phone) {
        Tenant tenant = new Tenant();
        tenant.setName("Rewrap Hospital " + UUID.randomUUID().toString().substring(0, 8));
        tenant.setType(Tenant.TenantType.HOSPITAL);
        tenant.setSlug("rewrap-" + UUID.randomUUID().toString().substring(0, 8));
        tenant.setActive(true);
        Tenant saved = tenantRepository.saveAndFlush(tenant);

        TenantContext.setTenantId(saved.getId());
        TenantKeyHolder.get().ensureDekExists();
        TenantKeyHolder.get().ensureBlindIndexKeyExists();

        // Saved through the entity, so every NOT NULL column and the PII converter behave
        // exactly as they do in production.
        com.medos.entity.Patient patient = new com.medos.entity.Patient();
        patient.setUhid("UHID" + UUID.randomUUID().toString().substring(0, 8));
        patient.setTenantId(saved.getId());
        patient.setName(name);
        patient.setPhone(phone);
        patient.setDpdpConsent(true);
        patient.setDpdpConsentAt(java.time.LocalDateTime.parse(CONSENT_CAPTURED_AT));
        patient.setCreatedAt(java.time.LocalDateTime.now());
        patient.setUpdatedAt(java.time.LocalDateTime.now());
        patientRepository.saveAndFlush(patient);
        return saved.getId();
    }

    /** Decrypts a patient's name with that tenant bound, exactly as a request would. */
    private String nameOf(UUID tenantId) {
        TenantContext.setTenantId(tenantId);
        try {
            return decrypt(jdbc.queryForObject(
                    "SELECT name FROM patients WHERE tenant_id = ?", String.class, tenantId));
        } finally {
            TenantContext.clear();
        }
    }

    private String decrypt(String ciphertext) {
        return converter.convertToEntityAttribute(ciphertext);
    }

    /**
     * SHA-256 over every column of every patient row, by position rather than by name so
     * a new column is covered without touching this test.
     */
    private String patientsChecksum() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        jdbc.query("SELECT * FROM patients ORDER BY id", rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                Object value = rs.getObject(i);
                digest.update((value == null ? "<null>" : value.toString()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0x1f);
            }
            digest.update((byte) 0x1e);
        });
        return HexFormat.of().formatHex(digest.digest());
    }
}