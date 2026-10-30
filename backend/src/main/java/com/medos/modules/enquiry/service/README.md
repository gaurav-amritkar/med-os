# Service

## Overview
Backend Java component in `src/main/java/com/medos/modules/enquiry/service`.

## Interface
- `EnquiryService`
- `DeskSummary`

## Invariants
- Transactional service operations are governed by the declared Spring `@Transactional` boundaries.

## Side Effects
- `EnquiryService` persists enquiry records and append-only status-history rows, and calls `AuditLogger` for desk actions. Per-tenant numbering retries unique-index conflicts.

## Verification
- Run: `cd backend && mvn clean test`
