# Config

## Overview
Backend Java component in `src/main/java/com/medos/config`.

## Interface
- `AdminBootstrapRunner`
- `CacheConfig`
- `EncryptionConfig`
- `IdempotencyFilter`
- `IdempotencyResponseWrapper`
- `OpenApiConfig`
- `PatientNameIndexBackfillRunner`
- `RequestCorrelationFilter`
- `SecurityConfig`
- `TenantFilter`
- `TenantScopingStartupGuard`
- `TenantStatementInspector`
- `WebSocketConfig`

## Invariants
- Tenant-owned entities expose tenant ownership through `TenantOwned`.
- Transactional service operations are governed by the declared Spring `@Transactional` boundaries.

## Side Effects
- No external side effect is declared directly in this package.

## Verification
- Run: `cd backend && mvn clean test`
