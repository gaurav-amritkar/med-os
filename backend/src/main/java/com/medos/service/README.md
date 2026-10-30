# Service

## Overview
Backend Java component in `src/main/java/com/medos/service`.

## Interface
- `AiMedicineService`
- `AuthService`
- `DashboardService`
- `EncounterService`
- `IdempotencyService`
- `CachedResponse`
- `PatientOnboardingService`
- `PatientService`
- `HubSummary`
- `TenantService`
- `UserService`

## Invariants
- Tenant-owned entities expose tenant ownership through `TenantOwned`.
- Transactional service operations are governed by the declared Spring `@Transactional` boundaries.

## Side Effects
- Services persist patients, consents, encounters, prescriptions, tenants, memberships, users, and audit records through repositories. `IdempotencyService` reads, claims, caches, and releases keys in Redis.

## Verification
- Run: `cd backend && mvn clean test`
