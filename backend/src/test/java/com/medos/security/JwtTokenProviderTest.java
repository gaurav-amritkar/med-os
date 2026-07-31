package com.medos.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    // A valid base64-encoded 256-bit (32 byte) signing key.
    private static final String SECRET = "ZmFlZmFlZmFlZmFlZmFlZmFlZmFlZmFlZmFlZmFlZmFlZmFlZmFlZmFlZQ==";

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationMs", 3600000L);
        ReflectionTestUtils.setField(tokenProvider, "issuer", "medos-test");
    }

    @Test
    void generateToken_emitsValidSignedTokenWithClaims() {
        UUID userId = UUID.randomUUID();

        String token = tokenProvider.generateToken(userId, "doctor", "doctor");

        assertNotNull(token);
        Claims claims = tokenProvider.parseToken(token);
        assertEquals("doctor", claims.getSubject());
        assertEquals("medos-test", claims.getIssuer());
        assertEquals(userId.toString(), claims.get("uid"));
        assertEquals("doctor", claims.get("role"));
        assertEquals("doctor", claims.get("uname"));
    }

    @Test
    void validateToken_acceptsValidToken() {
        String token = tokenProvider.generateToken(UUID.randomUUID(), "admin", "admin");
        assertTrue(tokenProvider.validateToken(token));
    }

    @Test
    void validateToken_rejectsTamperedToken() {
        String token = tokenProvider.generateToken(UUID.randomUUID(), "admin", "admin");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertFalse(tokenProvider.validateToken(tampered));
    }

    @Test
    void validateToken_rejectsGarbage() {
        assertFalse(tokenProvider.validateToken("not-a-jwt"));
    }

    @Test
    void validateToken_rejectsEmptyAndNull() {
        assertFalse(tokenProvider.validateToken(""));
        // validateToken wraps parse in try/catch and returns false on any failure, including null.
        assertFalse(tokenProvider.validateToken(null));
    }

    @Test
    void getUsernameFromToken_returnsSubject() {
        String token = tokenProvider.generateToken(UUID.randomUUID(), "nurse", "nurse");
        assertEquals("nurse", tokenProvider.getUsernameFromToken(token));
    }

    @Test
    void getUserIdFromToken_parsesUidClaim() {
        UUID userId = UUID.randomUUID();
        String token = tokenProvider.generateToken(userId, "billing", "billing");
        assertEquals(userId, tokenProvider.getUserIdFromToken(token));
    }

    @Test
    void getRoleFromToken_returnsRoleClaim() {
        String token = tokenProvider.generateToken(UUID.randomUUID(), "pharmacy", "pharmacist");
        assertEquals("pharmacist", tokenProvider.getRoleFromToken(token));
    }

    @Test
    void parseToken_withDifferentSecret_fails() {
        JwtTokenProvider other = new JwtTokenProvider();
        ReflectionTestUtils.setField(other, "jwtSecret", "ZmFlZmFlZmFlZmFlZmFlZmFlZmFlZmFlZmFlZmFlZmFlZmFlZmFlZmFlZA==");
        ReflectionTestUtils.setField(other, "jwtExpirationMs", 3600000L);
        ReflectionTestUtils.setField(other, "issuer", "medos-test");
        String token = other.generateToken(UUID.randomUUID(), "admin", "admin");

        // A token signed with a different key fails signature verification (SignatureException is a JwtException).
        assertThrows(JwtException.class, () -> tokenProvider.parseToken(token));
    }
}
