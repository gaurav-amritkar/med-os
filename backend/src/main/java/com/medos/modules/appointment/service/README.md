# Service

## Overview
Backend Java component in `src/main/java/com/medos/modules/appointment/service`.

## Interface
- `AppointmentService`
- `BoardSummary`

## Invariants
- Transactional service operations are governed by the declared Spring `@Transactional` boundaries.

## Side Effects
- `AppointmentService` persists booked appointments, check-in transitions, status changes, and notes through `AppointmentRepository`.

## Verification
- Run: `cd backend && mvn clean test`
