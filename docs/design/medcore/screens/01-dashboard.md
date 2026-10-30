# Screen 01 — Dashboard

**MedOS route:** `/` (operator/management role) · **Medcore status:** ✅ mocked

## Purpose
Landing screen for managers and HODs. Answers "what needs attention right now"
in one glance, and links into every other area. Clinical work happens elsewhere;
this is a monitoring surface.

## Layout regions
1. **KPI row (4 stat cards):** Active patients · Bed occupancy % · ER waiting
   (patients) · Critical alerts open (red when > 0 unacknowledged).
2. **Admissions & discharges** — dual-line trend (last 7 days), legend on header.
3. **Department load** — horizontal occupancy bars per department, live badge.
4. **Today's activity** — timestamped event feed with typed tags
   (Admission / Critical / Discharge / ER / Staffing). "View all" → audit view.
5. **Ward status** — per-ward occupancy progress bars, colored by band
   (green < 70, orange 70–85, red > 85).

## Data sources (mapped to MedOS)
- KPIs: encounters, admissions, ER queue, alerts endpoints (aggregates)
- Feed: audit trail / event stream, newest first
- Ward status: bed occupancy aggregates per ward

## States
- Empty: zero-data illustration + "connect data" hint (fresh tenant)
- Loading: skeleton cards, never blanket spinner
- Unacknowledged critical alerts > 0: KPI card red + activity feed surfaces them
