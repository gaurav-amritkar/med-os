# Service

## Overview
Backend Java component in `src/main/java/com/medos/modules/billing/service`.

## Interface
- `BillingService`
- `PatientBalanceService`

## Invariants
- Transactional service operations are governed by the declared Spring `@Transactional` boundaries.

## Side Effects
- `BillingService` persists invoices and charge status changes, then publishes `PatientBalanceEvent`. The after-commit listener recalculates the patient balance asynchronously; see the [event contract](../event/README.md).

## Verification
- Run: `cd backend && mvn clean test`
