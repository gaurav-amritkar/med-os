# Util

## Overview
Backend Java component in `src/main/java/com/medos/util`.

## Interface
- `AuditLogger`
- `BlindIndexUtil`
- `EncryptionUtil`
- `MoneyUtil`
- `PerTenantSerial`
- `PiiCiphertextFormat`
- `DekResolver`
- `UnknownGenerationException`

## Invariants
- No package-wide invariant is declared directly in these source files.

## Side Effects
- `AuditLogger.log` reads user/request context and persists audit rows through `AuditLogRepository`; `logAsync` schedules that work asynchronously.

## Verification
- Run: `cd backend && mvn clean test`
