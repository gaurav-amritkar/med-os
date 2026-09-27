/**
 * Icon set — inline SVG, 1.5px stroke on a 24px grid.
 *
 * Replaces geometric Unicode glyphs (◉ ◈ ◎ ▣ ⬡ ₿) which were announced by
 * screen readers as arbitrary punctuation and had inconsistent visual weight.
 *
 * Every icon is decorative: the adjacent text label carries the meaning, so all
 * of them are aria-hidden and focusable="false".
 *
 * Path data is hand-authored and cannot be verified by reading it — see
 * pages/IconGallery.jsx, which renders every icon for visual inspection.
 * In dev only; the route is gated on import.meta.env.DEV in App.jsx.
 */
const ICONS = {
  dashboard: ['M3 3h7v7H3z', 'M14 3h7v7h-7z', 'M3 14h7v7H3z', 'M14 14h7v7h-7z'],
  patients: ['M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2', 'M12 11a4 4 0 1 1 0-8 4 4 0 0 1 0 8'],
  encounters: ['M9 4h6v3H9z', 'M15 5h3a2 2 0 0 1 2 2v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a2 2 0 0 1 2-2h3', 'M8 12h8', 'M8 16h5'],
  admissions: ['M2 17v-5h20v5', 'M2 17v3', 'M22 17v3', 'M6 12V8h5a4 4 0 0 1 4 4'],
  pharmacy: ['M6.5 3.5h11a4 4 0 0 1 0 8h-11a4 4 0 0 1 0-8z', 'M11 3.5v8'],
  billing: ['M5 3h14v18l-3-2-2 2-2-2-2 2-2-2-3 2z', 'M9 8h6', 'M9 12h6'],
  plus: ['M12 5v14', 'M5 12h14'],
  search: ['M11 19a8 8 0 1 1 0-16 8 8 0 0 1 0 16z', 'M21 21l-4.3-4.3'],
  menu: ['M3 6h18', 'M3 12h18', 'M3 18h18'],
  close: ['M6 6l12 12', 'M18 6L6 18'],
  logout: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'M16 17l5-5-5-5', 'M21 12H9'],
  alert: ['M12 3L2 20h20z', 'M12 9v5', 'M12 17.5v.5'],
  check: ['M4 12l5 5L20 6'],
  clock: ['M12 21a9 9 0 1 1 0-18 9 9 0 0 1 0 18z', 'M12 7v5l3 2'],
  chevron: ['M6 9l6 6 6-6'],
  user: ['M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2', 'M12 11a4 4 0 1 1 0-8 4 4 0 0 1 0 8'],
};

export { ICONS };

export default function Icon({ name, size = 20, className = '' }) {
  const paths = ICONS[name];
  if (!paths) return null;

  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {paths.map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  );
}
