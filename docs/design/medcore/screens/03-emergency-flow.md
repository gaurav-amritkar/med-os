# Screen 03 — Emergency Flow

**MedOS route:** `/emergency` (maps to ER triage + ambulance intake) · **Status:** ✅ mocked

## Purpose
Run the ER queue in real time: acuity funnel, wait-time compliance, ambulance
bay holds, and where the flow is currently stuck.

## Layout regions
1. **Timer stat cards:** Door-to-triage · Door-to-doctor · Waiting in ER
   (with red/orange patients) · Ambulances in bay (longest hold).
2. **Triage by acuity** — four funnel bands (Red / Orange / Yellow / Green)
   with counts and the SLA clock each band must meet
   (Resus ≤ 5 min · Emerg ≤ 10 · Urgent ≤ 60 · Standard ≤ 2 h).
3. **Last 24 h inflow** — declining bar chart of new ER arrivals per hour.
4. **Ambulance bay** — incoming units with case#, complaint, ETA/hold time.
5. **Bottlenecks** — watch/OK feed for imaging queue, ICU transfer waits, etc.
6. **Live case table** — case#, patient + age + complaint, acuity tag, arrival,
   wait, status. Sorted by acuity then arrival.

## Data sources (mapped to MedOS)
- ER encounters with triage acuity + arrival timestamps
- SLA computation from arrival → first clinician contact
- Ambulance intake queue (external handoff events)

## Interaction rules
- Colour of acuity bands is paired with the name (never colour-only — 1.4.1)
- A Red patient waiting > SLA makes the band pulse + escalate to Critical Alerts
- "Open board" switches to column view (board per bay/room) at large sizes

## States
- Empty queue: "No active ER cases" empty state, timers show season averages
