# Frontend Pages

## Overview

Route-level screens for authentication, tenant onboarding, patient and clinical workflows, scheduling, pharmacy, admissions, billing, and staff administration.

## Interface

- Default-exported page components: `Login`, `Onboarding`, `Dashboard`, `Patients`, `Encounters`, `Appointments`, `TokenQueue`, `EnquiryDesk`, `Pharmacy`, `Admissions`, `Billing`, `Users`, and `IconGallery`.

## Invariants

- Page data access goes through domain APIs in `../api/`.
- Pages use shared auth and UI state stores in `../store/` where session, loading, toast, or patient-banner state is needed.

## Side Effects

- Pages issue backend requests through the API client and may update shared Zustand stores as part of user actions.

## Verification

- Run: `npm test`
