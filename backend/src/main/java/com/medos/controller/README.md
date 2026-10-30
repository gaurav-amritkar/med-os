# Controller

## Overview
Backend Java component in `src/main/java/com/medos/controller`.

## Interface
- `AdmissionController`
- `AppointmentController`
- `AuthController`
- `BillingController`
- `DashboardController`
- `EncounterController`
- `EnquiryController`
- `OnboardingController`
- `PatientController`
- `PatientOnboardingController`
- `PharmacyController`
- `TenantController`
- `UserController`
- `RoleRequest`
- `ActiveRequest`
- `PermissionsRequest`

## Invariants
- No package-wide invariant is declared directly in these source files.

## Side Effects
- Spring MVC controllers handle HTTP requests and return response data.

## Verification
- Run: `cd backend && mvn clean test`
