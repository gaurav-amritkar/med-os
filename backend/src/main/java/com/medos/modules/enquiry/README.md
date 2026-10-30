# Enquiry Module

## Overview

Groups the tenant-scoped enquiry desk workflow.

## Interface

- [Enquiry service](./service/README.md) records enquiries, serves the board, and updates status history.

## Invariants

- Enquiry numbering and status-history guarantees are enforced in `EnquiryService` and the V7 migration; see the service and database contracts.

## Side Effects

- Service operations persist enquiry and status-history rows and write audit records.

## Verification

- Run from `backend/`: `mvn test`
