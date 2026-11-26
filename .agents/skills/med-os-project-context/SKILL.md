---
name: med-os-project-context
description: Fast, low-token project bootstrap for the MedOS repository. Use when starting any new MedOS task to understand structure, health, roadmap, and where to edit without broad repo scans.
---

# MedOS Project Context (Low-Token Bootstrap)

Use this skill at the start of MedOS work to get high-confidence context quickly.

## Primary objective
Build enough repo understanding for accurate edits **without expensive full-repo exploration**.

## Read order (minimal)
1. `CLAUDE.md` — full architecture, invariants, module map, open gaps, ADR summary. **Read this first.**
2. `AGENTS.md` — working rules, validation commands, do-not-do list.
3. `docs/roadmap.md` — current priorities and production-readiness status.
4. `PROJECT_SNAPSHOT.md` (this skill's compact structure and command map).

Task-dependent extras:
- `docs/superpowers/specs/` — design specs for discharge, partial dispensing, MedWorks parity
- `docs/adr/0010-product-module-scope.md` — module scope decision (27-module parity)
- `docs/requirements/MedOS_Business_Requirements_Document_v4.md` — BRD v4 (do NOT read v3)

If task is backend-specific, then read only:
- `backend/pom.xml`
- targeted files under `backend/src/main/java/com/medos/<area>/...`
- related migrations — they live in **`database/migrations/`** (see below)

If task is frontend-specific, then read only:
- `frontend/package.json`
- targeted files under `frontend/src/<area>/...`

## Fast discovery rules
- Prefer `find_path` for locating exact files when path is uncertain.
- Prefer `grep` for symbols in scoped folders (`backend/**` or `frontend/**`), not whole repo scans.
- Avoid reading generated/vendor dirs (`node_modules`, `dist`, `target`) unless explicitly needed.

## Validation defaults
Run **smallest relevant checks first**:
- Backend: `mvn test` (or focused test class), then `mvn -DskipTests package` if packaging-impacting changes.
- Frontend: `npm run lint`, then `npm test` for test-impacting changes, then `npm run build`.
- For infra/docker changes: validate compose model and affected service build only.

## Repo-specific cautions
- This repo has active production-hardening work; do not assume old status from memory.
- Prefer the roadmap in `docs/roadmap.md` for prioritization and scope boundaries.
- Keep changes surgical; avoid unrelated refactors.

## Expected output style for MedOS tasks
When finishing work:
1. List changed files with one-line purpose.
2. State exact validation commands run and outcomes.
3. Call out production risk impact (security/reliability/compliance/perf) if relevant.

## Supporting file
Use `PROJECT_SNAPSHOT.md` in this skill directory for a compact map of folders, commands, and known hotspots.
