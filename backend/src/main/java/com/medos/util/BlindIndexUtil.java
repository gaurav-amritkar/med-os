package com.medos.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Keyed blind index for searching an encrypted column.
 *
 * <p>Patient names are encrypted with AES-GCM at rest, which is randomised per
 * value and therefore cannot be matched by a SQL {@code LIKE}. Without an index
 * the only way to find a patient is to know their UHID, and reception staff
 * routinely know only a name. This produces a deterministic, keyed digest of a
 * normalised name so the column can be matched by equality.
 *
 * <p>Properties, deliberately:
 * <ul>
 *   <li><b>Deterministic</b> — the same name always produces the same index, so
 *       lookups work. That is the trade: an attacker with the database but
 *       without the key cannot read names, but can confirm a guessed name by
 *       index equality. The key is therefore as sensitive as the plaintext.</li>
 *   <li><b>Domain separated</b> — the index key is derived from the PII
 *       encryption key via HMAC over a fixed label, so the index key is never
 *       the encryption key and a digest from one purpose cannot be used in the
 *       other.</li>
 *   <li><b>Equality only</b> — a blind index cannot support substring matching.
 *       Search matches the whole normalised name, or the UHID, which is not
 *       encrypted and supports partial matching directly.</li>
 * </ul>
 *
 * <p>Rotation of the PII key requires recomputing every index; see
 * NEXT_STEPS.md item T6.
 */
@Slf4j
@Component
public class BlindIndexUtil {

    /** Fixed label so the derived key is only ever an index key. */
    private static final String DOMAIN = "medos:blind-index:patient-name:v1";

    private byte[] indexKey;

    @Value("${medos.security.pii-encryption-key:}")
    private String piiKey;

    @PostConstruct
    void initialise() {
        if (piiKey == null || piiKey.isBlank()) {
            // Leave the key unset; lookups degrade to "no match" rather than
            // failing startup, and a clear warning is logged.
            log.warn("PII encryption key not set; patient name blind index is unavailable and "
                    + "search will match on UHID only.");
            return;
        }
        byte[] keyBytes = Base64.getDecoder().decode(piiKey);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("PII encryption key must be 32 bytes (256 bits) after Base64 decode");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            this.indexKey = mac.doFinal(DOMAIN.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive the patient name blind index key", e);
        }
        log.info("Patient name blind index ready");
    }

    /**
     * Normalise a name the way the index requires: trimmed, internal whitespace
     * collapsed, case folded. Applied identically on write and on search, so the
     * two can never drift.
     */
    public static String normaliseName(String name) {
        if (name == null) {
            return null;
        }
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * @return lowercase hex HMAC-SHA256 of the normalised name, or null when the
     *         index is unavailable or the name is blank.
     */
    public String indexPatientName(String name) {
        String normalised = normaliseName(name);
        if (normalised == null || normalised.isEmpty() || indexKey == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(indexKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalised.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // Never let a crypto failure break a write; the row is simply not
            // findable by name, which is preferable to losing the patient.
            log.warn("Could not compute patient name blind index", e);
            return null;
        }
    }

    /** Test hook: inject a known key without a Spring context. */
    public void setPiiKeyForTesting(String key) {
        this.piiKey = key;
        initialise();
    }

    /** Test hook: direct access to the derived key length. */
    public int derivedKeyLength() {
        return indexKey == null ? 0 : indexKey.length;
    }
}
