/**
 * Lab orders: order a test from an encounter; a lab role updates status through
 * the lifecycle and records results. Routes are role-gated on the server.
 *
 * Columns: patient, test, priority, status, result, actions.
 * Status actions are gated by the user's role — a doctor can only create;
 * a lab role can collect, start, complete, cancel, and record results.
 */

import { useEffect, useState } from 'react';
import DataTable from '../components/DataTable.jsx';
import Modal from '../components/Modal.jsx';
import useToastStore from '../store/toastStore';
import { labOrderApi } from '../api';
import { authApi } from '../api';

const STATUS_LABELS = {
  ordered: 'Ordered',
  collected: 'Collected',
  in_progress: 'In Progress',
  completed: 'Completed',
  cancelled: 'Cancelled',
};

const STATUS_TONES = {
  ordered: 'info',
  collected: 'warning',
  in_progress: 'warning',
  completed: 'success',
  cancelled: 'danger',
};

const PRIORITY_LABELS = {
  normal: 'Normal',
  urgent: 'Urgent',
  stat: 'STAT',
};

const PRIORITY_TONES = {
  normal: 'neutral',
  urgent: 'warning',
  stat: 'danger',
};

// A doctor can order; a lab role can move status and record results.
// The server enforces this — the UI only offers actions the role may see.
const NEXT_ACTIONS = {
  ordered: ['collected', 'cancelled'],
  collected: ['in_progress', 'completed'],
  in_progress: ['completed'],
  completed: [],
  cancelled: [],
};

export default function LabOrders() {
  const [rows, setRows] = useState([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [userRole, setUserRole] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [statusModal, setStatusModal] = useState(null); // {id, next}
  const [resultModal, setResultModal] = useState(null); // id
  const [saving, setSaving] = useState(false);
  const addToast = useToastStore((s) => s.addToast);

  useEffect(() => {
    authApi.getMe()
      .then(({ data }) => {
        setUserRole((data?.user?.role || '').toUpperCase());
        fetchOrders();
      })
      .catch(() => { fetchOrders(); });
  }, []);

  const fetchOrders = () => {
    setLoading(true);
    setLoadError(null);
    labOrderApi.list({ status: statusFilter || undefined })
      .then(({ data }) => setRows(data))
      .catch((err) => {
        setLoadError(err.response?.data?.message || 'Could not load lab orders.');
      })
      .finally(() => setLoading(false));
  };

  const canUpdateStatus = () => userRole === 'LAB' || userRole === 'ADMIN';
  const canCreate = () => userRole === 'DOCTOR' || userRole === 'ADMIN';

  const createOrder = async (formData) => {
    setSaving(true);
    try {
      await labOrderApi.create(formData);
      addToast('Lab order created', 'success');
      setModalOpen(false);
      fetchOrders();
    } catch (err) {
      addToast(err.response?.data?.message || 'Could not create the lab order', 'critical');
    } finally {
      setSaving(false);
    }
  };

  const moveStatus = async ({ next }) => {
    setSaving(true);
    try {
      await labOrderApi.updateStatus(statusModal.id, { status: next });
      addToast(`Order marked ${STATUS_LABELS[next].toLowerCase()}`, 'success');
      setStatusModal(null);
      fetchOrders();
    } catch (err) {
      addToast(err.response?.data?.message || 'Could not update the order', 'critical');
    } finally {
      setSaving(false);
    }
  };

  const recordResult = async (formData) => {
    setSaving(true);
    try {
      await labOrderApi.recordResult(resultModal.id, { result: formData.result });
      addToast('Result recorded', 'success');
      setResultModal(null);
      fetchOrders();
    } catch (err) {
      addToast(err.response?.data?.message || 'Could not record the result', 'critical');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      key: 'patientId', label: 'Patient',
      render: (r) => `Patient ${String(r.patientId).slice(0, 8)}`,
    },
    { key: 'testName', label: 'Test' },
    { key: 'testCode', label: 'Code' },
    {
      key: 'priority', label: 'Priority',
      render: (r) => (
        <span className={`badge badge-${PRIORITY_TONES[r.priority] || 'info'}`}>
          {PRIORITY_LABELS[r.priority] || r.priority}
        </span>
      ),
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
      key: 'result', label: 'Result',
      render: (r) => r.result || <span className="text-muted">—</span>,
    },
    {
      key: 'actions', label: 'Actions',
      render: (r) => {
        const isDoctorOrAdmin = userRole === 'DOCTOR' || userRole === 'ADMIN';
        const isLabOrAdmin = userRole === 'LAB' || userRole === 'ADMIN';
        const next = NEXT_ACTIONS[r.status] || [];
        const terminal = next.length === 0;

        // Doctor/admin: can order on this encounter (link to encounter detail)
        // Lab/admin: can move status and record results
        if (terminal) {
          return <span className="badge badge-neutral">Closed</span>;
        }

        return (
          <span>
            {isLabOrAdmin && next.includes('collected') && (
              <button type="button" className="btn-ghost btn-sm" aria-label={`Collect ${r.testName}`}
                onClick={(e) => { e.stopPropagation(); setStatusModal({ id: r.id, next: 'collected' }); }}>
                Collect
              </button>
            )}
            {isLabOrAdmin && next.includes('in_progress') && (
              <button type="button" className="btn-ghost btn-sm" aria-label={`Start ${r.testName}`}
                onClick={(e) => { e.stopPropagation(); setStatusModal({ id: r.id, next: 'in_progress' }); }}>
                Start
              </button>
            )}
            {isLabOrAdmin && next.includes('completed') && (
              <button type="button" className="btn-success btn-sm" aria-label={`Complete ${r.testName}`}
                onClick={(e) => { e.stopPropagation(); setStatusModal({ id: r.id, next: 'completed' }); }}>
                Complete
              </button>
            )}
            {isLabOrAdmin && next.includes('cancelled') && (
              <button type="button" className="btn-danger btn-sm" aria-label={`Cancel ${r.testName}`}
                onClick={(e) => { e.stopPropagation(); setStatusModal({ id: r.id, next: 'cancelled' }); }}>
                Cancel
              </button>
            )}
            {isLabOrAdmin && (r.status === 'collected' || r.status === 'in_progress') && (
              <button type="button" className="btn-info btn-sm" aria-label={`Record result for ${r.testName}`}
                onClick={(e) => { e.stopPropagation(); setResultModal(r.id); }}>
                Record Result
              </button>
            )}
            {isDoctorOrAdmin && r.status === 'ordered' && (
              <button type="button" className="btn-ghost btn-sm" aria-label={`Cancel ${r.testName}`}
                onClick={(e) => { e.stopPropagation(); setStatusModal({ id: r.id, next: 'cancelled' }); }}>
                Cancel
              </button>
            )}
          </span>
        );
      },
    },
  ];

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Lab Orders</h2>
          <p className="page-subtitle">Order tests from encounters; collect, process, and record results</p>
        </div>
        {canCreate() && (
          <button type="button" className="btn-primary" onClick={() => setModalOpen(true)}>+ Order Test</button>
        )}
      </div>

      <FilterBarPlaceholder
        onApply={() => fetchOrders()}
        onClear={() => { setStatusFilter(''); fetchOrders(); }}
      >
        <select aria-label="Status" value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">All Statuses</option>
          {Object.entries(STATUS_LABELS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
      </FilterBarPlaceholder>

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        emptyMessage="No lab orders yet — order a test from an encounter."
        error={loadError}
        loading={loading}
        loadingLabel="Loading lab orders…"
      />

      {modalOpen && canCreate() && (
        <OrderModal saving={saving} onClose={() => setModalOpen(false)} onSubmit={createOrder} />
      )}
      {statusModal && canUpdateStatus() && (
        <StatusModal next={statusModal.next} saving={saving} onClose={() => setStatusModal(null)} onSubmit={moveStatus} />
      )}
      {resultModal && canUpdateStatus() && (
        <ResultModal saving={saving} onClose={() => setResultModal(null)} onSubmit={recordResult} />
      )}
    </div>
  );
}

function FilterBarPlaceholder({ children, onApply, onClear }) {
  return (
    <div className="filter-bar filter-bar-inline">
      {children}
      <button type="button" className="btn-ghost btn-sm" onClick={onClear}>Clear</button>
      <button type="button" className="btn-primary btn-sm" onClick={onApply}>Apply</button>
    </div>
  );
}

function OrderModal({ saving, onClose, onSubmit }) {
  const [form, setForm] = useState({
    encounterId: '', patientId: '', testName: '', testCode: '', priority: 'normal',
  });
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  return (
    <Modal title="Order Lab Test" onClose={onClose}>
      <div className="form-group">
        <label htmlFor="order-patient">Patient ID</label>
        <input id="order-patient" value={form.patientId} onChange={set('patientId')} required />
      </div>
      <div className="form-group">
        <label htmlFor="order-encounter">Encounter ID</label>
        <input id="order-encounter" value={form.encounterId} onChange={set('encounterId')} required />
      </div>
      <div className="form-group">
        <label htmlFor="order-test">Test name</label>
        <input id="order-test" value={form.testName} onChange={set('testName')} required />
      </div>
      <div className="form-group">
        <label htmlFor="order-code">Test code</label>
        <input id="order-code" value={form.testCode} onChange={set('testCode')} />
      </div>
      <div className="form-group">
        <label htmlFor="order-priority">Priority</label>
        <select id="order-priority" value={form.priority} onChange={set('priority')}>
          <option value="normal">Normal</option>
          <option value="urgent">Urgent</option>
          <option value="stat">STAT</option>
        </select>
      </div>
      <div className="modal-actions">
        <button type="button" className="btn-ghost" onClick={onClose}>Cancel</button>
        <button type="button" className="btn-primary" disabled={saving}
          onClick={() => onSubmit({
            encounterId: form.encounterId,
            patientId: form.patientId,
            testName: form.testName,
            testCode: form.testCode || undefined,
            priority: form.priority,
          })}>Order Test</button>
      </div>
    </Modal>
  );
}

function StatusModal({ next, saving, onClose, onSubmit }) {
  return (
    <Modal title={`Mark ${STATUS_LABELS[next].toLowerCase()}`} onClose={onClose}>
      <div className="modal-actions">
        <button type="button" className="btn-ghost" onClick={onClose}>Cancel</button>
        <button type="button" className="btn-primary" disabled={saving}
          onClick={() => onSubmit({ next })}>{STATUS_LABELS[next]}</button>
      </div>
    </Modal>
  );
}

function ResultModal({ saving, onClose, onSubmit }) {
  const [result, setResult] = useState('');
  return (
    <Modal title="Record Result" onClose={onClose}>
      <div className="form-group">
        <label htmlFor="result-text">Result</label>
        <textarea id="result-text" value={result} onChange={(e) => setResult(e.target.value)} required />
      </div>
      <div className="modal-actions">
        <button type="button" className="btn-ghost" onClick={onClose}>Cancel</button>
        <button type="button" className="btn-primary" disabled={saving || !result.trim()}
          onClick={() => onSubmit({ result: result.trim() })}>Record</button>
      </div>
    </Modal>
  );
}
