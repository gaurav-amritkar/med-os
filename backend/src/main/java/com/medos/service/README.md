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
- No external side effect is declared directly in this package.

## Verification
- Run: `cd backend && mvn clean test`
