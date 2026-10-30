# Screen 06 — Capacity Forecasts

**MedOS route:** `/capacity` · **Status:** ✅ mocked (recreation of the Dribbble shot) · [Dribbble reference](../reference/medcore-dribbble-1.jpg)

## Purpose
Predictive monitoring over four scarce resources — beds, ICU, oxygen/equipment,
staff — with explicit Critical/Warning/Normal threshold bands so teams act
before capacity breaches, not after.

## Layout regions (2 × 2 card grid)
Each card = one resource × one chart, with a **Next: 7 / 14 / 30 days**
segmented control in the header (default 7).

1. **Bed Occupancy** — orange series from history into the forecast;
   right-edge forecast rail (red) with dotted threshold lines labelled
   Critical (250) / Warning (220) / Normal (195) — crossing warning at D+6
   in sample data.
2. **ICU Demand** — same pattern, thresholds 40 / 35 / 30 beds.
3. **Oxygen & Equipment** — declining planned-draw bar chart
   (80 → 66 → 48 → 34 → 19 → 14 → 6 cylinders) with diagonal-hatch bars
   and value captions.
4. **Staff Demand** — six smooth series (Bed, Staff, Nurses, Doctor,
   ER Care, Techs) with a dot legend on top.

## Data sources (mapped to MedOS)
- Bed occupancy aggregate: occupancy history + admission/discharge model
- ICU demand: level-of-care census + transfer queue
- Oxygen: cylinder stock + per-patient consumption rates
- Staff demand: visitor/occupancy ratio per role (reuses HR rosters)

## Interaction rules
- Segment control re-renders the forecast horizon for that card only
  (independent per card, as in the shot)
- Thresholds configurable per hospital (Settings → Forecast thresholds)
- Crossing the critical threshold inside the horizon promotes a warning
  banner into Critical Alerts

## States
- Loading: skeleton lines; No forecast (insufficient history): dashed band
  with explanatory copy; Stale model: grey "stale > 24 h" badge on the card
