package com.medos.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.InvalidClaimException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    @Value("${medos.security.jwt.secret}")
    private String jwtSecret;

    @Value("${medos.security.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${medos.security.jwt.issuer}")
    private String issuer;

    public enum TokenValidationResult {
        VALID,
        EXPIRED,
        INVALID_SIGNATURE,
        MALFORMED,
        WRONG_ISSUER,
        WRONG_KEY
    }

    @PostConstruct
    void validateConfig() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not configured. " +
                    "Set the JWT_SECRET environment variable (e.g. `openssl rand -base64 48`).");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (Exception e) {
            throw new IllegalStateException("JWT_SECRET must be a valid Base64-encoded value.", e);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes (HS256). " +
                    "Current length: " + keyBytes.length + " bytes. Generate one with: openssl rand -base64 48");
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(UUID userId, String username, String role, UUID tenantId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId.toString());
        claims.put("role", role);
        claims.put("uname", username);
        if (tenantId != null) {
            claims.put("tenantId", tenantId.toString());
        }

        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public TokenValidationResult validateToken(String token) {
        try {
            parseToken(token);
            return TokenValidationResult.VALID;
        } catch (ExpiredJwtException e) {
            return TokenValidationResult.EXPIRED;
        } catch (SignatureException e) {
            return TokenValidationResult.INVALID_SIGNATURE;
        } catch (MalformedJwtException e) {
            return TokenValidationResult.MALFORMED;
        } catch (InvalidClaimException e) {
            return TokenValidationResult.WRONG_ISSUER;
        } catch (IllegalArgumentException e) {
            // JJWT asserts on null/empty token arguments before any parsing occurs.
            return TokenValidationResult.MALFORMED;
        } catch (Exception e) {
            return TokenValidationResult.WRONG_KEY;
        }
    }

    public String getUsernameFromToken(String token) {
        return parseToken(token).getSubject();
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return UUID.fromString(claims.get("uid").toString());
    }

    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        Object role = claims.get("role");
        if (role == null) {
            throw new JwtException("Missing role claim in token");
        }
        return role.toString();
    }

}