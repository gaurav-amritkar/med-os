# Frontend State Stores

## Overview

Zustand stores hold session, global request loading, patient banner, and toast state.

## Interface

- `useAuthStore` exposes login/logout, token/session access, tenant state, and `initAuth`.
- `useLoadingStore` exposes `startLoading`, `stopLoading`, and `resetLoading`.
- `usePatientBannerStore` exposes `setPatient` and `clearPatient`.
- `useToastStore` exposes `addToast` and `removeToast`.

## Invariants

- Auth token and user data are restored only when the stored token and expiry are valid (`authStore.js`).
- Loading state is derived from a nonnegative `loadingCount`; `stopLoading` clamps the count at zero.

## Side Effects

- `useAuthStore` reads and writes browser `sessionStorage` for session fields.
- `useToastStore.addToast` schedules removal of the toast after four seconds.

## Verification

- Run: `npm test`
