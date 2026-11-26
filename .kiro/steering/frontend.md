---
inclusion: fileMatch
fileMatchPattern: "frontend/src/**,frontend/e2e/**,frontend/vite.config.js,frontend/package.json"
---

# MedOS Frontend — Steering Rules

Applies whenever touching React/Vite code, E2E tests, or frontend config.

## Stack

React 19, Vite 8, React Router 7, Zustand 5, Axios 1, Recharts 3.
Lint: oxlint. Tests: Vitest + Testing Library. E2E: Playwright.

## Directory layout

```
frontend/src/
  api/          Axios client + per-domain modules (auth, patients, encounters, pharmacy, billing, admissions, notifications, lab, staff, appointments)
  components/   Layout, Sidebar, Header, Modal, Toast — shared only
  pages/        One file per route: Login, Dashboard, Patients, Encounters,
                Pharmacy, Admissions, Billing, Appointments, Lab, Staff
  store/        Zustand: authStore (JWT + user), toastStore
  test/         Vitest unit tests
```

## API proxy

Vite dev server and nginx (production) both proxy `/api` to the backend.
Never hardcode backend URLs. Always use relative `/api/v1/...` paths.

## Known open gaps — do not regress these further

| Gap | Location | Fix needed |
|---|---|---|
| G37 | 4+ search pages | 300 ms debounce + AbortController on every search `useEffect` |
| G38 | 7+ hand-rolled modals | Extract `<Modal>` with `role="dialog"`, `aria-modal`, focus trap, Escape key |
| G39 | `Admissions` page | Add try/catch + error state on all API calls |
| G40 | `Dashboard` page | Distinguish API failure from zero-count; show error banner, not zeros |
| G41 | `loadingStore.js` | Dead code — not consumed anywhere |
| G42 | `index.css` | Add z-index design tokens (`--z-sidebar`, `--z-modal`, `--z-toast`) |

## Rules

- **Error handling**: every API call needs a catch block that sets visible error state. Never `console.error` and leave the UI in a broken-looking zero state.
- **Search inputs**: debounce ≥ 300 ms + cancel the in-flight request on a new keystroke (`AbortController`).
- **Modals**: always `role="dialog"` + `aria-modal="true"` + `aria-labelledby` + Escape key dismiss + focus trap. Do not add another hand-rolled copy.
- **Loading state**: use the existing `loadingStore` or per-component `useState`. Do not create a third pattern.
- **No password in `.env`**: `VITE_*` env vars are bundled into the JS and readable by anyone. Never put secrets there.
- **Accessibility**: WCAG 2.1 AA is a stated requirement. Check colour contrast (`npm run check:contrast`) and keyboard navigation on any new interactive element.

## Validation commands

```bash
cd frontend
npm run lint          # oxlint
npm test              # Vitest unit tests
npm run build         # production build (catches type/import errors)
npm run check:contrast  # contrast ratio gate
```

## E2E tests

All Playwright specs require `E2E_PASSWORD` env var. Specs skip (not fail) without it.

```bash
cd frontend
E2E_PASSWORD='<password>' npx playwright test e2e/smoke.spec.js    # ~30 s
E2E_PASSWORD='<password>' npx playwright test e2e/full.spec.js     # full flows
E2E_PASSWORD='<password>' npx playwright test e2e/staff.spec.js    # staff management
E2E_PASSWORD='<password>' npx playwright test e2e/appointments.spec.js
```

The `full.spec.js` asserts `/api/v1/users/me` does not return a password hash. Do not weaken this assertion.
