# Frontend API Client

## Overview

Axios transport and domain request functions used by the React interface.

## Interface

- `client` from `client.js` configures the API base URL and request/response interceptors.
- `authApi`, `userApi`, `onboardingApi`, `patientApi`, `encounterApi`, `pharmacyApi`, `admissionApi`, `billingApi`, `enquiryApi`, `dashboardApi`, and `appointmentApi` in `index.js` expose domain calls.
- `newIdempotencyKey` and `withIdempotencyKey` in `idempotency.js` prepare idempotent request headers.

## Invariants

- `unwrapPage` normalizes paged responses to `{ data, meta }` and accepts bare arrays.
- Requests attach the current bearer token and tenant ID when present.
- A 401 response redirects to `/login` only when a session exists and the request is not a login attempt.
- Dispense, invoice creation, and payment requests attach an `Idempotency-Key` through `withIdempotencyKey`.

## Side Effects

- Axios interceptors update `useLoadingStore`; authenticated 401 responses call `useAuthStore.logout()` and change `window.location.href`.
- API functions perform HTTP requests against the configured backend.

## Verification

- Run: `npm test`
