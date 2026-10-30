# Payment Module

## Overview

Groups payment recording and related invoice updates.

## Interface

- [Payment service](./service/README.md) records payments and updates invoice state.

## Invariants

- Payment and invoice changes are transactionally coordinated by `PaymentService`; see the service contract.

## Side Effects

- Service operations persist payment and invoice changes, then publish a patient-balance event.

## Verification

- Run from `backend/`: `mvn test`
