# Frontend Scripts

## Overview

Node scripts support UI snapshot generation, flow probing, and design-token contrast checks.

## Interface

- `contrast-check.mjs` reads `src/index.css`, checks configured foreground/background pairs, and exits nonzero for missing or failing pairs.
- `capture-ui-snapshots.mjs` drives the UI with Playwright and writes snapshots and a manifest under `docs/ui-snapshots/`.
- `generate-snapshot-index.mjs` reads the snapshot manifest and writes `docs/ui-snapshots/INDEX.md`.
- `probe-flows.mjs` calls the configured MedOS API for end-to-end workflow probes; `journey.mjs` drives a browser journey.

## Invariants

- Contrast values are read from `src/index.css`, the script's documented token source of truth.
- Snapshot-index content is derived from the capture manifest.

## Side Effects

- Snapshot capture writes image and manifest files under `docs/ui-snapshots/` and uses a browser and API.
- Snapshot-index generation writes `docs/ui-snapshots/INDEX.md`.
- Flow scripts issue HTTP requests and may create application records; `journey.mjs` writes screenshots under `/tmp/journey`.

## Verification

- Run: `npm run check:contrast`
