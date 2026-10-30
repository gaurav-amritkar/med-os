# EGM Drift Report — 2026-10-30

## README.md

- The `Database Schema (25 Tables)` heading is stale: the current Flyway history contains 30 `CREATE TABLE` statements across `database/migrations/V*.sql` (23 in V1 and seven in later migrations).
- The architecture tree lists `billing`, `clinical`, `pharmacy`, and `payment` under `backend/.../modules`; the source also contains `appointment` and `enquiry` modules.

## backend/src/main/java/com/medos/README.md

- The `Interface` section lists only `MedOsApplication` and does not link the direct package contracts for `config`, `controller`, `dto`, `entity`, `exception`, `mapper`, `modules`, `repository`, `security`, `service`, and `util`.

## backend/src/main/java/com/medos/modules/README.md

- The child index links only `billing`, `clinical`, and `pharmacy`; `appointment`, `enquiry`, and `payment` are also source subtrees. Lower-level service contracts exist for those omitted subtrees, but their intermediate directories have no README contract.

## backend/src/main/java/com/medos/config/README.md

- `Side Effects` says no external side effect is declared, but `AdminBootstrapRunner` writes users, tenants, and memberships through repositories, and `PatientNameIndexBackfillRunner` persists patient name-index backfills.

## backend/src/main/java/com/medos/security/README.md

- `Side Effects` describes Spring Data repository interfaces, but this package instead contains key-management persistence through `TenantKeyStoreJdbc`/`JdbcDekReencryptor`, rotation records through `KeyRewrapService` and `DekRotationService`, and Redis or process-local rate-limit state through `LoginRateLimiter`.

## backend/src/main/java/com/medos/service/README.md

- `Side Effects` says no external side effect is declared, but services persist through repositories (`PatientService`, `EncounterService`, `TenantService`, `UserService`, and others); `IdempotencyService` also reads and writes Redis.

## backend/src/main/java/com/medos/util/README.md

- `Side Effects` says no external side effect is declared, but `AuditLogger.log` saves audit records through `AuditLogRepository`.

## backend/src/main/java/com/medos/modules/appointment/service/README.md

- `Side Effects` says no external side effect is declared, but `AppointmentService` saves appointments through `AppointmentRepository` when creating and updating them.

## backend/src/main/java/com/medos/modules/billing/service/README.md

- `Side Effects` says no external side effect is declared, but `BillingService` saves invoices and publishes `PatientBalanceEvent` after billing.

## backend/src/main/java/com/medos/modules/billing/event/README.md

- `Side Effects` says no external side effect is declared, but `PatientBalanceEventListener.handleBalanceUpdate` runs asynchronously after commit and invokes `PatientBalanceService.recalculateBalance`, which updates `patients.outstanding` in PostgreSQL.

## backend/src/main/java/com/medos/modules/clinical/service/README.md

- `Side Effects` says no external side effect is declared, but `AdmissionService` persists admissions, room occupancy, discharge summaries, addenda, and charges, then publishes a balance-update event.

## backend/src/main/java/com/medos/modules/enquiry/service/README.md

- `Side Effects` says no external side effect is declared, but `EnquiryService` saves enquiry records and status-history rows when creating or updating enquiries.

## backend/src/main/java/com/medos/modules/payment/service/README.md

- `Side Effects` says no external side effect is declared, but `PaymentService` saves payments and invoice changes, then publishes `PatientBalanceEvent`.

## backend/src/main/java/com/medos/modules/pharmacy/service/README.md

- `Side Effects` says no external side effect is declared, but pharmacy services persist catalog items, batches, stock transactions, prescriptions, and charges, and dispensing publishes `PatientBalanceEvent`.

## frontend/README.md

- The Tests section says `e2e/full.spec.js` currently fails because `/api/v1/users/me` returns a password hash. The repository root README records that #106 was fixed in #108 and says the assertion became green; the page now returns `CurrentUserDTO` rather than the JPA entity.

