# Appointment Module

## Overview

Groups appointment scheduling and queue workflows.

## Interface

- [Appointment service](./service/README.md) exposes booking, board, check-in, status, and note operations.

## Invariants

- Appointment state transitions and booking constraints are enforced by `AppointmentService`; see the service contract.

## Side Effects

- Service operations persist appointment changes through `AppointmentRepository`.

## Verification

- Run from `backend/`: `mvn test`
