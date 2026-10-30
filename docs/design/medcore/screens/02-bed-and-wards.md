# Screen 02 — Bed & Wards

**MedOS route:** `/beds` (maps to wards + admissions + housekeeping) · **Status:** ✅ mocked

## Purpose
The operational surface for bed allocation: who is where, what is free, what is
dirty, what is blocked — and what is about to block.

## Layout regions
1. **Summary stat cards:** Total beds · Occupied · Available now (with
   ready/cleaning split) · Blocked/private.
2. **Bed map** — one row per ward, one compact cell per bed:
   occupied (gray-blue) / available (dashed) / cleaning (orange) /
   isolation (red). Cell shows bed number; hover shows patient banner.
   Click free bed → assign flow (patient search + encounter link).
   Click occupied bed → patient banner + transfer/discharge actions.
3. **Side rail:** Housekeeping queue (beds to clean with ETAs) and
   **Transfers pending** (patients awaiting beds > X min).
4. **Ward table** — totals + occupancy progress bar + in-charge per ward.

## Data sources (mapped to MedOS)
- Bed master per ward with state machine: `available → occupied → cleaning → available`
- Admissions/encounters for occupant identity; transfers queue from admissions
- All identity fields hidden until hover/click reveal (PII discipline)

## Interaction rules
- Ward filter chips above bed map (All / per ward)
- Assigning a bed requires selecting an admitted patient — no free-text names
- Releasing/transfer writes to audit trail automatically

## States
- Longest-waiting transfer > 30 min: side-rail card turns critical-tagged
- Housekeeping overtime > 45 min: entry highlighted orange
