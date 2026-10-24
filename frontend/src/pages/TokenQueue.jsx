/**
 * Token Queue (Wave 1 reception module).
 *
 * Per-day queue: tokens are issued at check-in (not booking), reset each
 * morning via the V8 `uq_appointments_tenant_day_token` partial-unique index.
 *
 * The board answers three questions from one screen: who is being served
 * right now, who waits next (in token order), and how a token came to be —
 * the header says it in words so the desk never has to remember the rule.
 */

import { useEffect, useRef, useState } from 'react';
import DataTable from '../components/DataTable.jsx';
import CounterRow from '../components/CounterRow.jsx';
import useToastStore from '../store/toastStore';
import useAuthStore from '../store/authStore';
import { appointmentApi } from '../api';

const STATUS_LABELS = {
  checked_in: 'Checked-in',
  in_consultation: 'In consultation',
};

const STATUS_TONES = {
  checked_in: 'warning',
  in_consultation: 'info',
};

// Only a doctor (or the admin standing in) may move a consultation forward;
// the query page is read-only for everyone else, matching the controller.
const MAY_MOVE = ['doctor', 'admin'];

const TYPE_LABELS = {
  first_visit: 'First visit',
  follow_up: 'Follow-up',
};

export default function TokenQueue() {
  const [rows, setRows] = useState([]);
  const [pageMeta, setPageMeta] = useState(null);
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const role = useAuthStore((s) => s.role);
  const addToast = useToastStore((s) => s.addToast);
  const timer = useRef(null);

  async function load() {
    setLoading(true);
    try {
      const [queueRes, sumRes] = await Promise.all([
        appointmentApi.queue(undefined, 0),
        appointmentApi.summary(),
      ]);
      setRows(queueRes.data || []);
      setPageMeta(queueRes.meta);
      setSummary(sumRes.data);
    } catch (e) {
      addToast(e?.response?.data?.message || 'Failed to load the queue', 'critical');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    timer.current = setInterval(load, 20000);
    return () => clearInterval(timer.current);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function act(row, next) {
    await appointmentApi.updateStatus(row.id, { status: next });
    addToast(`${row.patientName} marked ${next === 'in_consultation' ? 'in consultation' : 'completed'}`, 'success');
    load();
  }

  const inConsultation = rows
    .filter((r) => r.status === 'in_consultation')
    .sort((a, b) => b.tokenNo - a.tokenNo)[0];

  const counters = summary ? [
    { label: 'Checked-in', value: summary.checkedIn, tone: 'warning' },
    { label: 'In consultation', value: summary.inConsultation, tone: 'info' },
    { label: 'Completed today', value: summary.completed, tone: 'success' },
  ] : [];

  const columns = [
    {
      key: 'tokenNo', label: 'Token',
      render: (r) => <span className="queue-token">{`Token ${r.tokenNo}`}</span>,
    },
    { key: 'patientName', label: 'Patient' },
    { key: 'doctorName', label: 'Doctor' },
    { key: 'apptRef', label: 'Appt #', mono: true },
    {
      key: 'appointmentType', label: 'Type',
      // queue DTO ships `type`; keep the `appointmentType` shape honoured too.
      render: (r) => TYPE_LABELS[r.appointmentType ?? r.type] ?? r.appointmentType ?? r.type ?? '',
    },
    {
      key: 'status', label: 'Status',
      render: (r) => (
        <span className={`badge badge-${STATUS_TONES[r.status] || 'info'}`}>
          {STATUS_LABELS[r.status] || r.status}
        </span>
      ),
    },
    {
      key: 'actions', label: 'Actions',
      render: (r) => {
        if (!MAY_MOVE.includes(role)) {
          return <span className="badge badge-neutral">Waiting</span>;
        }
        return r.status === 'checked_in' ? (
          <button type="button" className="btn btn-primary btn-sm"
            onClick={() => act(r, 'in_consultation')}>
            Start
          </button>
        ) : (
          <button type="button" className="btn btn-success btn-sm"
            onClick={() => act(r, 'completed')}>
            Complete
          </button>
        );
      },
    },
  ];

  return (
    <div className="token-queue-page">
      <div className="page-header">
        <div>
          <h2>Token Queue</h2>
          <p>Tokens are issued at check-in — one per appointment, reset each morning.</p>
        </div>
        <button type="button" className="btn btn-secondary" onClick={load}>Refresh</button>
      </div>

      <CounterRow counters={counters} />

      <div className="queue-now" role="status">
        {inConsultation ? (
          <div className="queue-now-servng">
            <span className="queue-now-kicker">Now serving</span>
            <span className="queue-now-token">{`Token ${inConsultation.tokenNo}`}</span>
            <span className="queue-now-patient">{inConsultation.patientName}</span>
            <span className="queue-now-doctor">{inConsultation.doctorName}</span>
          </div>
        ) : (
          <div className="queue-now-servng">
            <span className="queue-now-kicker">Now serving</span>
            <span className="queue-now-idle">Nobody yet — check in the next patient to start the queue.</span>
          </div>
        )}
      </div>

      <DataTable
        columns={columns}
        rows={rows
          .map((r) => ({ ...r }))
          .sort((a, b) => a.tokenNo - b.tokenNo)}
        rowKey={(r) => r.id}
        pageMeta={pageMeta}
        loading={loading}
        loadingLabel="Loading the queue…"
        emptyMessage="No tokens today. A token appears when a patient is checked in."
      />
    </div>
  );
}
