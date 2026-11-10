package com.medos.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Canonical fingerprint of an idempotency-protected request body.
 *
 * <p>An idempotency key must mean "this operation, once" — not "any operation
 * under this key". Without binding the key to the request body, a client could
 * send a payment for 100 under key {@code K}, receive the success, then send a
 * payment for 10000 under the same {@code K} and be told it had already
 * succeeded (#221).
 *
 * <p>The body is cached in a request attribute (see
 * {@link com.medos.config.CachedBodyHttpServletRequest}) so it can be read once
 * for the fingerprint and again by the controller. Reads are limited to
 * {@link #MAX_BODY_BYTES}; a body larger than that yields {@code null}, and a
 * caller that cannot fingerprint the body must not bind a key to it.
 */
@Component
public class RequestFingerprint {

    /** 64 KiB is far above any legitimate payment/invoice/dispense body. */
    static final int MAX_BODY_BYTES = 64 * 1024;

    /**
     * Computes a SHA-256 digest over the request body, or {@code null} if the
     * body cannot be read.
     */
    public String fingerprint(HttpServletRequest request) {
        byte[] body;
        try {
            body = com.medos.config.CachedBodyHttpServletRequest.read(request, MAX_BODY_BYTES);
        } catch (Exception e) {
            return null;
        }
        if (body == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(body);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every supported JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
