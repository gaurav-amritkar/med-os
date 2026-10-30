# Frontend Components

## Overview

Reusable React interface elements for application layout, navigation, feedback, tabular data, dialogs, patient context, and access gating.

## Interface

- Components exported by their files include `Layout`, `Header`, `Sidebar`, `ProtectedRoute`, `ErrorBoundary`, `ToastContainer`, `DataTable`, `FilterBar`, `CounterRow`, `PatientBanner`, `Modal`, and `Icon`/`ICONS`.

## Invariants

- `ProtectedRoute` is the route-level access boundary; `ErrorBoundary` catches render errors within its React subtree.
- `PatientBanner` reads patient context from `usePatientBannerStore`; `ToastContainer` reads transient messages from `useToastStore`.

## Side Effects

- Components render route navigation and user feedback; `Modal` manages focus and document-level keyboard behavior as implemented in `Modal.jsx`.

## Verification

- Run: `npm test`
