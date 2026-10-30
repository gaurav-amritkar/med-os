# Service

## Overview
Backend Java component in `src/main/java/com/medos/modules/clinical/service`.

## Interface
- `AdmissionService`
- `DischargeSummaryAssembler`

## Invariants
- Transactional service operations are governed by the declared Spring `@Transactional` boundaries.

## Side Effects
- `AdmissionService` persists admissions, room occupancy, discharge summaries, addenda, and charges, then publishes `PatientBalanceEvent` after discharge billing.

## Verification
- Run: `cd backend && mvn clean test`
