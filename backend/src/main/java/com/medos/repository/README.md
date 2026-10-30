# Repository

## Overview
Backend Java component in `src/main/java/com/medos/repository`.

## Interface
- `AdmissionRepository`
- `AppointmentRepository`
- `AuditLogRepository`
- `ChargeRepository`
- `ConsentRepository`
- `DischargeSummaryAddendumRepository`
- `DischargeSummaryRepository`
- `DiseaseMedicineMapRepository`
- `EncounterRepository`
- `EnquiryRepository`
- `EnquiryStatusHistoryRepository`
- `InvoiceRepository`
- `KeyRotationRepository`
- `LabOrderRepository`
- `MedicineBatchRepository`
- `MedicineCatalogRepository`
- `NotificationRepository`
- `OpdQueueRepository`
- `PatientRepository`
- `PaymentRepository`
- `PrescriptionRepository`
- `RoomRepository`
- `StockTransactionRepository`
- `TenantConfigRepository`
- `TenantRepository`
- `TenantUserRepository`
- `UserRepository`

## Invariants
- No package-wide invariant is declared directly in these source files.

## Side Effects
- Spring Data repository interfaces expose persistence operations for their associated entities.

## Verification
- Run: `cd backend && mvn clean test`
