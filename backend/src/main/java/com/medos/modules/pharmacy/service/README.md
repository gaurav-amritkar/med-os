# Service

## Overview
Backend Java component in `src/main/java/com/medos/modules/pharmacy/service`.

## Interface
- `DispenseService`
- `InventoryService`
- `MedicineCatalogService`
- `PrescriptionService`

## Invariants
- Transactional service operations are governed by the declared Spring `@Transactional` boundaries.

## Side Effects
- `InventoryService`, `PrescriptionService`, and `DispenseService` persist catalog, batch, ledger, prescription, and charge changes. Dispensing consumes locked batches in FEFO order and publishes `PatientBalanceEvent`.

## Verification
- Run: `cd backend && mvn clean test`
