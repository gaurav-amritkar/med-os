# Service

## Overview
Backend Java component in `src/main/java/com/medos/modules/payment/service`.

## Interface
- `PaymentService`

## Invariants
- Transactional service operations are governed by the declared Spring `@Transactional` boundaries.

## Side Effects
- `PaymentService` persists payments and invoice changes, then publishes `PatientBalanceEvent` for asynchronous balance recalculation.

## Verification
- Run: `cd backend && mvn clean test`
