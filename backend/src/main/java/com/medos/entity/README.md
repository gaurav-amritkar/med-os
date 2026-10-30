# Entity

## Overview
Backend Java component in `src/main/java/com/medos/entity`.

## Interface
- `Admission`
- `Status`
- `Appointment`
- `Status`
- `Type`
- `AuditLog`
- `Charge`
- `ChargeType`
- `Status`
- `Consent`
- `DischargeSummary`
- `DischargeSummaryAddendum`
- `DiseaseMedicineMap`
- `Encounter`
- `Status`
- `Enquiry`
- `EnquiryStatus`
- `EnquiryType`
- `EnquiryStatusHistory`
- `Invoice`
- `Status`
- `KeyRotation`
- `LabOrder`
- `Priority`
- `Status`
- `MedicineBatch`
- `MedicineCatalog`
- `Notification`
- `Type`
- `OpdQueue`
- `Status`
- `Patient`
- `Payment`
- `PaymentMethod`
- `Status`
- `Prescription`
- `Status`
- `Room`
- `RoomType`
- `StockTransaction`
- `TransactionType`
- `Tenant`
- `TenantType`
- `TenantConfig`
- `TenantDekArchive`
- `Identity`
- `TenantEntityListener`
- `TenantKey`
- `TenantOwned`
- `TenantUser`
- `UserRole`
- `User`

## Invariants
- Tenant-owned entities expose tenant ownership through `TenantOwned`.

## Side Effects
- No external side effect is declared directly in this package.

## Verification
- Run: `cd backend && mvn clean test`
