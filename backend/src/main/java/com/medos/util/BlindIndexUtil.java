package com.medos.util;

import com.medos.security.TenantContext;
import com.medos.security.TenantKeyHolder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Keyed blind index over the patient name, so search can work on an encrypted column.
 *
 * <p>{@code patients.name} holds AES-256-GCM ciphertext, which is randomised per
 * value, so {@code WHERE name LIKE '%anita%'} can never match. Without this index a
 * receptionist who knew only a name could not find an existing patient, which turns
 * every repeat visit into a duplicate registration. The index is a deterministic
 * HMAC-SHA256 of the normalised name, matched by equality; {@code uhid} remains
 * unencrypted and supports partial matching directly.
 *
 * <p><b>The key has its own lifecycle, independent of the data key.</b> It is derived
 * from a tenant's blind-index key, which is stored wrapped and separately generated
 * (ADR-0009). It previously shared the PII secret, so rotating that secret silently
 * invalidated every stored index and search returned nothing — with no error, which
 * presented as broken search rather than a key problem. Search can now be held steady
 * across a data-key rotation, or the index key rotated on its own if it is suspected,
 * without re-encrypting a single row.
 *
 * <p>The index is as sensitive as the plaintext it protects: a holder of the database
 * but not the key cannot read names, but can confirm a guessed name by comparing
 * digests.
 */
@Slf4j
@Component
public class BlindIndexUtil {

    private static final String HMAC = "HmacSHA256";

    /**
     * Computes the index for the acting tenant, or null when none can be resolved.
     *
     * <p>Never falls back to a shared or derived key. With no tenant in scope the only
     * alternative would be a process-wide key, which would make names comparable
     * across tenants and reintroduce the coupling this class exists to remove.
     */
    public String indexPatientName(String name) {
        String normalised = normaliseName(name);
        if (normalised == null || normalised.isEmpty()) {
            return null;
        }
        UUID tenant = TenantContext.getTenantId().orElse(null);
        if (tenant == null || !TenantKeyHolder.isInitialised()) {
            return null;
        }
        byte[] indexKey;
        try {
            indexKey = TenantKeyHolder.get().findBlindIndexKeyOrNull(tenant);
        } catch (RuntimeException e) {
            // Never let a key failure break a write: the row is simply not findable by
            // name, which is preferable to losing the patient.
            log.warn("Could not resolve the patient name blind index key; name search will "
                    + "match on UHID only for this request");
            return null;
        }
        if (indexKey == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(indexKey, HMAC));
            return HexFormat.of().formatHex(mac.doFinal(normalised.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.warn("Could not compute patient name blind index");
            return null;
        }
    }

    /**
     * Normalise a name the way the index requires: trimmed, internal whitespace
     * collapsed, case folded. Applied identically on write and on search, so the two
     * can never drift.
     */
    public static String normaliseName(String name) {
        if (name == null) {
            return null;
        }
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    @PostConstruct
    void reportAvailability() {
        if (TenantKeyHolder.isInitialised()) {
            log.info("Patient name blind index is tenant-scoped");
        } else {
            log.warn("Tenant key holder is not initialised; patient name blind index is "
                    + "unavailable and search will match on UHID only.");
        }
    }

    /** Test hook: whether an index can currently be produced for the acting tenant. */
    public int derivedKeyLength() {
        UUID tenant = TenantContext.getTenantId().orElse(null);
        if (tenant == null || !TenantKeyHolder.isInitialised()) {
            return 0;
        }
        byte[] key = TenantKeyHolder.get().findBlindIndexKeyOrNull(tenant);
        return key == null ? 0 : key.length;
    }
}
