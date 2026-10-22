/**
 * Appointments board (Wave 1 reception module).
 *
 * Counters: Total / Scheduled / Checked-in / In-consultation / Completed /
 * Cancelled / No-show. Columns: Appt #, Patient, Doctor, Time, Type, Status,
 * Actions. Filters: status + type. `+ Book Appointment`.
 */

import { useEffect, useState } from 'react';
import DataTable from '../components/DataTable.jsx';
import CounterRow from '../components/CounterRow.jsx';
import FilterBar from '../components/FilterBar.jsx';
import useToastStore from '../store/toastStore';
import { appointmentApi } from '../api';

const STATUS_LABELS = {
  scheduled: 'Scheduled',
  checked_in: 'Checked-in',
  in_consultation: 'In consultation',
  completed: 'Completed',
  cancelled: 'Cancelled',
  no_show: 'No-show',
};

const STATUS_TONES = {
  scheduled: 'info',
  checked_in: 'warning',
  in_consultation: 'info',
  completed: 'success',
  cancelled: 'danger',
  no_show: 'danger',
};

const TYPE_LABELS = {
  first_visit: 'First visit',
  follow_up: 'Follow-up',
};

export default function Appointments() {
  const [rows, setRows] = useState([]);
  const [pageMeta, setPageMeta] = useState(null);
  const [summary, setSummary] = useState(null);
  const [statusFilter, setStatusFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const toast = useToastStore();

  async function load() {
    setLoading(true);
    setLoadError(null);
    try {
      const [listRes, sumRes] = await Promise.all([
        appointmentApi.list(statusFilter, '', typeFilter, page),
        appointmentApi.summary(),
      ]);
      setRows(listRes.content || []);
      setPageMeta(listRes);
      setSummary(sumRes);
    } catch (e) {
      setLoadError(e?.message || 'Failed to load appointments');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, [statusFilter, typeFilter, page]);

  const counters = summary ? [
    { label: 'Total', value: summary.total, tone: 'neutral' },
    { label: 'Scheduled', value: summary.scheduled, tone: 'info' },
    { label: 'Checked-in', value: summary.checkedIn, tone: 'warning' },
    { label: 'In consultation', value: summary.inConsultation, tone: 'info' },
    { label: 'Completed', value: summary.completed, tone: 'success' },
    { label: 'Cancelled', value: summary.cancelled, tone: 'danger' },
    { label: 'No-show', value: summary.noShow, tone: 'danger' },
  ] : [];

  const columns = [
    { key: 'appointmentNo', label: 'Appt #' },
    { key: 'patientName', label: 'Patient' },
    { key: 'doctorName', label: 'Doctor' },
    { key: 'appointmentDate', label: 'Time' },
    { key: 'appointmentType', label: 'Type', render: (r) => TYPE_LABELS[r.appointmentType] || r.appointmentType },
    { key: 'status', label: 'Status', render: (r) => STATUS_LABELS[r.status] || r.status },
  ];

  const filters = [
    { key: 'status', label: 'Status', value: statusFilter, onChange: setStatusFilter,
      options: Object.entries(STATUS_LABELS).map(([v, l]) => ({ value: v, label: l })) },
    { key: 'type', label: 'Type', value: typeFilter, onChange: setTypeFilter,
      options: Object.entries(TYPE_LABELS).map(([v, l]) => ({ value: v, label: l })) },
  ];

  return (
    <div className="page appointments-page">
      <header className="page-header">
        <h1>Appointments</h1>
      </header>
      <CounterRow counters={counters} />
      <FilterBar filters={filters} />
      {loadError && <div className="error-banner">{loadError}</div>}
      <DataTable
        columns={columns}
        rows={rows}
        pageMeta={pageMeta}
        onPageChange={setPage}
        loading={loading}
        emptyMessage="No appointments today."
      />
    </div>
  );
}