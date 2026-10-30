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
- `TenantKeyStoreJdbc` and `JdbcDekReencryptor` read and update tenant key material and encrypted rows in PostgreSQL. `KeyRewrapService`, `DekRotationService`, and `PiiKeyRotationRunner` persist rotation audit state. `LoginRateLimiter` updates Redis counters when available and falls back to process-local state.

## Verification
- Run: `cd backend && mvn clean test`
