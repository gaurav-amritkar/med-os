/**
 * The §3.2 filter row — search input, then module-specific selects/radio
 * groups, then the action buttons.
 *
 * <p>Grammar #5: one search box, 1–4 filters, then optional Filter/Clear.
 * Modules with live search pass nothing but `search`; pages that batch
 * filters behind an explicit submit pass `onApply`; `onClear` renders the
 * reset button whichever pattern the page uses.
 *
 * @param {import('react').ReactNode} children — filter controls (selects,
 *   radio groups). Rendered between the search input and the buttons.
 */
export default function FilterBar({
  search = null,
  onClear,
  onApply,
  children = null,
}) {
  const hasSearch = Boolean(search && (search.value !== undefined || search.onChange));
  // A Clear button without a handler would be inert decoration; render it
  // only when the page says what clearing means (reset local state + refetch).
  const showClear = typeof onClear === 'function';
  const showApply = typeof onApply === 'function';

  return (
    <div className="filter-row">
      {hasSearch && (
        <input
          type="search"
          aria-label={search.label || search.placeholder || 'Search'}
          placeholder={search.placeholder || 'Search'}
          value={search.value}
          onChange={(e) => search.onChange(e.target.value)}
          className="filter-search"
        />
      )}
      {children}
      {(showApply || showClear) && (
        <div className="filter-actions">
          {showClear && (
            <button
              type="button"
              className="btn-ghost btn-sm"
              onClick={() => onClear?.()}
            >
              Clear
            </button>
          )}
          {showApply && (
            <button
              type="button"
              className="btn-primary btn-sm"
              onClick={() => onApply()}
            >
              Filter
            </button>
          )}
        </div>
      )}
    </div>
  );
}
