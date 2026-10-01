# MedOS frontend

React 19 + Vite 8 + React Router, with Zustand for session and toast state.
Axios is the HTTP client.

## Running it

The normal path is Docker, which needs nothing installed locally:

```bash
docker compose up -d --build      # from the repository root
```

The dev frontend is published on `FRONTEND_EXTERNAL_PORT` (8080 by default) and
proxies `/api` to the backend, so http://localhost:8080 is the whole app. Port 80
is production only.

### Running Vite directly

Useful for fast HMR while working on the UI. The backend still needs to be
running — start it with Docker first, or point `VITE_API_URL` at one.

```bash
npm install
npm run dev
```

Vite is configured for port 5173 and prints the URL it bound to. Note this is
**not** the same origin as the
Docker path, so add the Vite origin to `CORS_ORIGINS` in `.env` if the backend
rejects requests.

## Commands

| Command | What it does |
|---------|--------------|
| `npm run dev` | Dev server with HMR |
| `npm run build` | Production build into `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run lint` | Oxlint |
| `npm test` | Vitest + Testing Library |
| `npm run probe:flows` | End-to-end flow probe (needs the stack up) |

## Tests

```bash
npm test                                        # unit
npm run lint && npm run build                   # the other two CI checks
```

End-to-end specs live in `e2e/` and need a running stack plus a password:

```bash
cd frontend
E2E_PASSWORD='<admin password>' npm run probe:flows
E2E_BASE_URL="http://localhost:8080" E2E_PASSWORD='<admin password>' npx playwright test e2e/smoke.spec.js
E2E_BASE_URL="http://localhost:8080" E2E_PASSWORD='<admin password>' npx playwright test e2e/full.spec.js
```

`E2E_PASSWORD` is required — the specs skip rather than fail without it. Use the
password from your `.env`, or `password` after running `./tools/seed-dev.sh`.

`e2e/full.spec.js` contains one assertion that **currently fails on purpose**:
`/api/v1/users/me` must not return a password hash, and it does until
[#106](https://github.com/gaurav-amritkar/med-os/issues/106) is fixed. Do not
weaken it to make the suite green.

## Configuration

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_API_URL` | `/api/v1` | API base path, same-origin via the proxy |
| `FRONTEND_EXTERNAL_PORT` | `8080` | Host port, read by Docker Compose (not by Vite) |

Vite inlines `VITE_`-prefixed variables at build time, so changing one requires a
rebuild.

## Layout

```
src/
  api/         Axios client and per-domain API modules
  components/  Layout, Sidebar, Header, Toast
  pages/       Login, Dashboard, Patients, Encounters, Pharmacy,
               Admissions, Billing
  store/       Zustand (auth, toast)
  test/        Test setup and helpers
  assets/
e2e/           Playwright specs: smoke, full journey, login
```
