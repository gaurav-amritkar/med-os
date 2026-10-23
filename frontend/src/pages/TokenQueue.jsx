/**
 * Token Queue (Wave 1 reception module).
 *
 * Per-day queue: tokens are issued at check-in (not booking), reset each
 * morning via the V8 uq_appointments_tenant_day_token partial-unique index.
 * Columns: Token #, Patient, Doctor, Appt #, Time, Type, Status.
 */

import { useEffect, useState } from 'react';
import DataTable from '../components/DataTable.jsx';
import CounterRow from '../components/CounterRow.jsx';
import FilterBar from '../components/FilterBar.jsx';
import { appointmentApi } from '../api';

const STATUS_LABELS = {
  checked_in: 'Checked-in',
  in_consultation: 'In consultation',
};

export default function TokenQueue() {
  const [rows, setRows] = useState([]);
  const [pageMeta, setPageMeta] = useState(null);
  const [summary, setSummary] = useState(null);
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);

  async function load() {
    setLoading(true);
    setLoadError(null);
    try {
      const [queueRes] = await Promise.all([
        appointmentApi.queue(statusFilter || undefined, page),
      ]);
      setRows(queueRes.data || []);
      setPageMeta(queueRes.meta);
      setSummary({ total: queueRes.meta?.totalElements ?? 0, tokens: queueRes.data?.length ?? 0 });
    } catch (e) {
      setLoadError(e?.message || 'Failed to load queue');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, [statusFilter, page]);

  const counters = summary ? [
    { label: 'In Queue', value: summary.tokens, tone: 'warning' },
    { label: 'Total Today', value: summary.total, tone: 'neutral' },
  ] : [];

  const columns = [
    { key: 'tokenNo', label: 'Token No.' },
    { key: 'patientName', label: 'Patient' },
    { key: 'doctorName', label: 'Doctor' },
    { key: 'appointmentNo', label: 'Appt #' },
    { key: 'appointmentDate', label: 'Time' },
    { key: 'appointmentType', label: 'Type', render: (r) => r.appointmentType === 'first_visit' ? 'First visit' : r.appointmentType === 'follow_up' ? 'Follow-up' : r.appointmentType },
    { key: 'status', label: 'Status', render: (r) => STATUS_LABELS[r.status] || r.status },
  ];

  const filters = [
    { key: 'status', label: 'Status', value: statusFilter, onChange: setStatusFilter,
      options: Object.entries(STATUS_LABELS).map(([v, l]) => ({ value: v, label: l })) },
  ];

  return (
    <div className="page token-queue-page">
      <header className="page-header">
        <h1>Token Queue</h1>
      </header>
      <CounterRow counters={counters} />
      <FilterBar
        onClear={() => setStatusFilter('')}
      >
        <select
          aria-label="All Statuses"
          className="filter-select"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
        >
          <option value="">All Statuses</option>
          {Object.entries(STATUS_LABELS).map(([v, l]) => (
            <option key={v} value={v}>{l}</option>
          ))}
        </select>
      </FilterBar>

      {loadError && <div className="error-banner">{loadError}</div>}
      <DataTable
        columns={columns}
        rows={rows}
        pageMeta={pageMeta}
        onPageChange={setPage}
        loading={loading}
        emptyMessage="No tokens in queue. Check-ins open after the doctor starts the consultation."
      />
    </div>
  );
}
