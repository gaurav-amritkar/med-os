# Frontend Application Source

## Overview

React entry point, route composition, global styling, and feature packages for the MedOS browser application.

## Interface

- `App` in `App.jsx` composes routes and application-level UI.
- `main.jsx` mounts the application; `index.css` supplies global design tokens and styles.
- [API client](./api/README.md), [shared components](./components/README.md), [route pages](./pages/README.md), and [state stores](./store/README.md) define the feature boundaries.

## Invariants

- Routes and feature screens are composed through `App`; browser startup is owned by `main.jsx`.
- API and shared state behavior is defined in the linked package contracts.

## Side Effects

- The entry point mounts the React application into the browser document.

## Verification

- Run: `npm test`
