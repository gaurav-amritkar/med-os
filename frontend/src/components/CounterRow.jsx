/**
 * The §3.2 counter chip row — 3–6 label + value chips at the top of a module
 * page, values colour-coded by semantic state.
 *
 * <p>Values should be unit-bearing where the reference is: "Available Beds
 * 0/1", "Present Today 6 checked-in". A zero is neutral unless the caller
 * marks it dangerous (e.g. a zero of anything that should not be zero reads as
 * Critical via `tone: 'danger'`, never via a value of 0 alone).
 *
 * @param {Array<{label, value, tone?, hint?}>} counters
 *   tone ∈ 'neutral' (default) | 'success' | 'danger' | 'warning' | 'info'.
 */
export default function CounterRow({ counters }) {
  if (!counters || counters.length === 0) return null;

  return (
    <ul className="counter-row">
      {counters.map((c) => (
        <li key={c.label} className="counter-chip" data-tone={c.tone || 'neutral'}>
          <span className="counter-label">{c.label}</span>
          <span className="counter-value">{c.value}</span>
          {c.hint != null && <span className="counter-hint">{c.hint}</span>}
        </li>
      ))}
    </ul>
  );
}
