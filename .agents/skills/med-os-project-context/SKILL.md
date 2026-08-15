---
name: med-os-project-context
description: Fast, low-token project bootstrap for the MedOS repository. Use when starting any new MedOS task to understand structure, health, roadmap, and where to edit without broad repo scans.
---

# MedOS Project Context (Low-Token Bootstrap)

Use this skill at the start of MedOS work to get high-confidence context quickly.

## Primary objective
Build enough repo understanding for accurate edits **without expensive full-repo exploration**.

## Read order (minimal)
1. `README.md` (high-level architecture and domain model).
2. `NEXT_STEPS.md` (current production-readiness roadmap and priorities).
3. `PRODUCTION_READINESS_PLAN_gpt5.3codex.md` and `GITHUB_ISSUES_PRODUCTION_READINESS_gpt5.3codex.md` (existing planning artifacts).
4. `PROJECT_SNAPSHOT.md` (this skill’s compact structure and command map).

If task is backend-specific, then read only:
- `backend/pom.xml`
- targeted files under `backend/src/main/java/com/medos/<area>/...`
- related migrations under `backend/src/main/resources/db/migration/`

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
- Prefer the roadmap in `NEXT_STEPS.md` for prioritization and scope boundaries.
- Keep changes surgical; avoid unrelated refactors.

## Expected output style for MedOS tasks
When finishing work:
1. List changed files with one-line purpose.
2. State exact validation commands run and outcomes.
3. Call out production risk impact (security/reliability/compliance/perf) if relevant.

## Supporting file
Use `PROJECT_SNAPSHOT.md` in this skill directory for a compact map of folders, commands, and known hotspots.
