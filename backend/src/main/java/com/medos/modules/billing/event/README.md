# Event

## Overview
Backend Java component in `src/main/java/com/medos/modules/billing/event`.

## Interface
- `PatientBalanceEvent`
- `PatientBalanceEventListener`

## Invariants
- Transactional service operations are governed by the declared Spring `@Transactional` boundaries.

## Side Effects
- `PatientBalanceEventListener.handleBalanceUpdate` receives events after the publishing transaction commits, runs asynchronously, and invokes `PatientBalanceService.recalculateBalance`, which updates `patients.outstanding` in a `REQUIRES_NEW` transaction.

## Verification
- Run: `cd backend && mvn clean test`
