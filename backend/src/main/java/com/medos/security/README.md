# Security

## Overview
Backend Java component in `src/main/java/com/medos/security`.

## Interface
- `BlindIndexKeyProvider`
- `CurrentUserProvider`
- `CustomUserDetailsService`
- `DekReencryptor`
- `DekRotationService`
- `BackupAttestation`
- `Result`
- `Status`
- `EncryptedPiiTables`
- `Table`
- `FeatureFlags`
- `JdbcDekReencryptor`
- `JwtAuthenticationFilter`
- `JwtTokenProvider`
- `TokenValidationResult`
- `KeyRewrapService`
- `Plan`
- `Result`
- `Status`
- `LoginRateLimiter`
- `PiiKeyRotationRunner`
- `TenantContext`
- `TenantKeyHolder`
- `TenantKeyResolver`
- `TenantKeyResolverFactory`
- `TenantKeyStore`
- `TenantKeyStoreJdbc`

## Invariants
- Transactional service operations are governed by the declared Spring `@Transactional` boundaries.

## Side Effects
- Spring Data repository interfaces expose persistence operations for their associated entities.

## Verification
- Run: `cd backend && mvn clean test`
