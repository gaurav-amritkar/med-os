# Screen 04 — Staff Management

**MedOS route:** `/staff` (maps to HR / duty rosters / attendance — see ADR-0010 parity) · **Status:** ✅ mocked

## Purpose
Keep every unit staffed: who is on shift now, where the next gap opens, and
which leave requests need a coverage plan.

## Layout regions
1. **Stat cards:** On duty now (of rostered) · Nurses on shift (coverage %) ·
   Doctors on shift (on call / on leave) · Open leave requests.
2. **Headcount by role** — horizontal bars: Nurses, Doctors, Technicians,
   Admin, Housekeeping.
3. **Shift coverage 7-day matrix** — per unit (rows) × day (columns):
   tag `Staffed/Required` colored Full (green) / Short (orange) / Gap (red).
4. **Today's roster** — person, role, unit, shift window, status
   (On duty / In later / On leave).
5. **Leave & coverage notes** — actionable conflicts for the next 7 days,
   each with a suggestion (float pool, re-plan).

## Data sources (mapped to MedOS)
- Employee directory + duty rosters + attendance (parity spec HR module)
- Leave ledger with pending approvals
- Coverage computation: rostered vs scheduled per unit/day/shift

## Interaction rules
- "Edit roster" opens the roster editor (drag-shift, swap, approving leave)
- Leave approval requires coverage confirmation when it creates a Short/Gap
- Payroll obligations (PF/ESI/TDS) live in the parity payroll wave — out of
  scope for this screen; link out to payroll reports

## States
- A Shift row with 0 staff: red Gap tag + blocking note on leave actions
