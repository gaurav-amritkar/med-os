# Screen 07 — Operational Reports

**MedOS route:** `/reports` (maps to analytics + audit export) · **Status:** ✅ mocked

## Purpose
Library of operational reports with schedules and history. Managers run,
schedule, and download; compliance extracts are signed.

## Layout regions
1. **Category filter chips + "New report"** — All / Clinical / Finance /
   Compliance.
2. **Report library (card grid)** — icon tile, title, meta
   (category · schedule · formats), actions (Run now · Schedule).
   Sample cards: Daily bed occupancy · ER throughput & wait times ·
   Revenue by department · Audit trail extract · Critical alert SLA review ·
   Patient movement census.
3. **Recent generations table** — report, range, generated at, by
   (person or scheduler), format, status (Ready / Running / Failed with
   retry affordance).

## Data sources (mapped to MedOS)
- Parameterised aggregates over encounters, admissions, billing, alerts
- Scheduled jobs (nightly 06:00, weekly, monthly)
- Signed immutable export for audit extracts (compliance requirement)

## Interaction rules
- Running a report with a range > 1 year requires confirmation (cost guard)
- Failed runs show error cause inline with a Retry action
- Access filtered by role: finance reports only for finance/admin roles

## States
- Empty library: "No reports in this category" empty state with create CTA
- Long-running job: progress bar + cancel
