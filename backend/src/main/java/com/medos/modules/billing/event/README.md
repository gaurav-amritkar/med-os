# Event

## Overview
Backend Java component in `src/main/java/com/medos/modules/billing/event`.

## Interface
- `PatientBalanceEvent`
- `PatientBalanceEventListener`

## Invariants
- Transactional service operations are governed by the declared Spring `@Transactional` boundaries.

## Side Effects
- No external side effect is declared directly in this package.

## Verification
- Run: `cd backend && mvn clean test`
