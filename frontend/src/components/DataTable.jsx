/**
 * The Tier-A list-page table (§3.2 grammar parts 6–8).
 *
 * <p>Every list page renders its rows through this component so the dense-table
 * treatment, the failure/empty state discipline, and the "Showing X–Y of Z ·
 * page i of N" footer are owned in exactly one place. A page that hand-rolls a
 * table can drift; 27 modules cannot all drift identically.
 *
 * <p>States, in the precedence Patients.jsx established (design §5.8):
 * loading → nothing else renders;
 * {@code error} → a {@code role="alert"} row — a failed fetch is never an
 * empty result, and the two must not look alike;
 * {@code emptyMessage} → a specific empty state, never a bare "No data".
 *
 * @param {Array<{key, label, mono?, align?, render?}>} columns
 *   `render(row)` produces the cell content; the fallback is `row[key]` itself
 *   so a plain-strings column needs no renderer.
 * @param {Array} rows
 * @param {(row) => React.Key} rowKey — stable key per row.
 * @param {string} emptyMessage — the specific empty state for this module.
 * @param {string} error — a load-failure message; presence suppresses the
 *   empty state.
 * @param {boolean} loading, {string} loadingLabel
 * @param {{page, size, totalElements, totalPages}} pageMeta — PageResponse
 *   meta; when present, the pagination footer renders.
 * @param {(page: number) => void} onPageChange
 * @param {(row) => void} onRowClick — optional row activation.
 */

const cellAlign = (c) => (c.align ? ` cell-${c.align}` : '');

export default function DataTable({
  columns,
  rows,
  rowKey,
  onRowClick,
  emptyMessage = 'Nothing to show yet.',
  error = null,
  loading = false,
  loadingLabel = 'Loading…',
  pageMeta = null,
  onPageChange,
}) {
  if (loading) {
    return <div className="data-loading">{loadingLabel}</div>;
  }

  const colCount = columns.length;
  const hasRows = rows.length > 0;
  const showFooter = pageMeta && pageMeta.totalElements > 0;
  const from = pageMeta ? pageMeta.page * pageMeta.size + (hasRows ? 1 : 0) : 0;
  const to = pageMeta ? pageMeta.page * pageMeta.size + rows.length : 0;

  return (
    <>
      {hasRows || error ? (
        <table>
          <thead>
            <tr>
              {columns.map((c) => (
                <th key={c.key} className={cellAlign(c)}>{c.label}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {hasRows && rows.map((row) => (
              <tr
                key={rowKey(row)}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                className={onRowClick ? 'row-clickable' : undefined}
              >
                {columns.map((c) => (
                  <td key={c.key} className={`${c.mono ? 'cell-mono' : ''}${cellAlign(c)}`}>
                    {c.render ? c.render(row) : row[c.key]}
                  </td>
                ))}
              </tr>
            ))}
            {error && (
              <tr><td colSpan={colCount} className="list-error" role="alert">{error}</td></tr>
            )}
          </tbody>
        </table>
      ) : (
        <div className="list-empty" role="status">{emptyMessage}</div>
      )}

      {showFooter && (
        <div className="table-footer">
          <span className="table-footer-meta">
            Showing {from}–{to} of {pageMeta.totalElements}
            {pageMeta.totalPages > 1 && ` · page ${pageMeta.page + 1} of ${pageMeta.totalPages}`}
          </span>
          {pageMeta.totalPages > 1 && onPageChange && (
            <div className="table-footer-buttons">
              <button
                type="button"
                className="btn-ghost btn-sm"
                aria-label="Previous page"
                disabled={pageMeta.page === 0}
                onClick={() => onPageChange(pageMeta.page - 1)}
              >
                ‹ Prev
              </button>
              <button
                type="button"
                className="btn-ghost btn-sm"
                aria-label="Next page"
                disabled={pageMeta.page + 1 >= pageMeta.totalPages}
                onClick={() => onPageChange(pageMeta.page + 1)}
              >
                Next ›
              </button>
            </div>
          )}
        </div>
      )}
    </>
  );
}
