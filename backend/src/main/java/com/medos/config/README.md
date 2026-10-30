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
- `AdminBootstrapRunner` conditionally persists bootstrap users, tenants, and memberships. `PatientNameIndexBackfillRunner` writes missing patient name indexes. Request filters also add response headers and manage request-scoped context.

## Verification
- Run: `cd backend && mvn clean test`
