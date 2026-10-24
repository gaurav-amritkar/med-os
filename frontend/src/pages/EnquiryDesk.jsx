/**
 * Front-desk pre-patient contacts: the Enquiry Desk.
 *
 * MedWorks grammar, verbatim: counters Total / New / Follow-up / Converted /
 * Cancelled; columns Enquiry #, Contact, Reason/Department, Status, Actions;
 * filters status + type; `+ Log Enquiry`.
 *
 * Every list page element here comes from the Wave-0 shared components
 * (DataTable / CounterRow / FilterBar) — a module that hand-rolls its table can
 * drift from the design grammar; this module composes them instead.
 */

import { useEffect, useState } from 'react';
import DataTable from '../components/DataTable.jsx';
import Modal from '../components/Modal.jsx';
import CounterRow from '../components/CounterRow.jsx';
import FilterBar from '../components/FilterBar.jsx';
import useToastStore from '../store/toastStore';
import { enquiryApi } from '../api';

const STATUS_LABELS = {
  new_enquiry: 'New',
  follow_up: 'Follow-up',
  converted: 'Converted',
  cancelled: 'Cancelled',
};

const STATUS_TONES = {
  new_enquiry: 'info',
  follow_up: 'warning',
  converted: 'success',
  cancelled: 'danger',
};

const TYPE_LABELS = {
  general: 'General',
  appointment: 'Appointment',
  admission: 'Admission',
  emergency: 'Emergency',
  pharmacy: 'Pharmacy',
  other: 'Other',
};

// A doctor conversion walks the patient to a card, not a dead enquiry board.
const NEXT_ACTIONS = {
  new_enquiry: ['follow_up', 'converted', 'cancelled'],
  follow_up: ['converted', 'cancelled'],
  converted: [],
  cancelled: [],
};

export default function EnquiryDesk() {
  const [rows, setRows] = useState([]);
  const [pageMeta, setPageMeta] = useState(null);
  const [summary, setSummary] = useState(null);
  const [statusFilter, setStatusFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [statusModal, setStatusModal] = useState(null); // {id, next}
  const [saving, setSaving] = useState(false);
  const addToast = useToastStore((s) => s.addToast);

  const fetchEnquiries = (nextPage = 0, status = statusFilter, type = typeFilter) => {
    setLoading(true);
    setLoadError(null);
    Promise.all([
      enquiryApi.list(status || undefined, type || undefined, nextPage),
      enquiryApi.summary(),
    ])
      .then(([list, sum]) => {
        setRows(list.data);
        setPageMeta(list.meta);
        setSummary(sum.data);
        setPage(nextPage);
      })
      .catch((err) => {
        setLoadError(
          err.response?.data?.message || 'Could not load the enquiry board. Check your connection and retry.',
        );
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchEnquiries(0); // eslint-disable-line react-hooks/exhaustive-deps
  }, []);

  const counters = summary ? [
    { label: 'Total', value: summary.total, tone: 'neutral', hint: 'logged' },
    { label: 'New', value: summary.newEnquiries, tone: 'info' },
    { label: 'Follow-up', value: summary.followUp, tone: 'warning' },
    { label: 'Converted', value: summary.converted, tone: 'success' },
    { label: 'Cancelled', value: summary.cancelled, tone: summary.cancelled > 0 ? 'danger' : 'neutral' },
  ] : [];

  const logEnquiry = async (formData) => {
    setSaving(true);
    try {
      await enquiryApi.create(formData);
      addToast('Enquiry logged', 'success');
      setModalOpen(false);
      fetchEnquiries(0);
    } catch (err) {
      addToast(err.response?.data?.message || 'Could not log the enquiry', 'critical');
    } finally {
      setSaving(false);
    }
  };

  const moveStatus = async ({ next, note }) => {
    setSaving(true);
    try {
      await enquiryApi.updateStatus(statusModal.id, { status: next, note: note || undefined });
      addToast(`Enquiry marked ${STATUS_LABELS[next].toLowerCase()}`, 'success');
      setStatusModal(null);
      fetchEnquiries(page);
    } catch (err) {
      addToast(err.response?.data?.message || 'Could not update the enquiry', 'critical');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    { key: 'enquiryRef', label: 'Enquiry #', mono: true },
    {
      key: 'contactName', label: 'Contact',
      render: (r) => (`${r.contactName}${r.contactPhone ? ` · ${r.contactPhone}` : ''}`),
    },
    {
      key: 'reason', label: 'Reason / Department',
      render: (r) => (
        <span>
          {r.reason}
          {r.department
            ? <><br /><span className="badge badge-info">{r.department}</span></>
            : null}
        </span>
      ),
    },
    {
      key: 'type', label: 'Type',
      render: (r) => TYPE_LABELS[r.type] || r.type,
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
        const next = NEXT_ACTIONS[r.status] || [];
        if (next.length === 0) return <span className="badge badge-neutral">Closed</span>;
        return (
          <span>
            {next.includes('follow_up') && (
              <button type="button" className="btn-ghost btn-sm enquiry-action" aria-label={`Follow up ENQ-${r.enquiryNo}`}
                onClick={(e) => { e.stopPropagation(); setStatusModal({ id: r.id, next: 'follow_up' }); }}>
                Follow up
              </button>
            )}
            {next.includes('converted') && (
              <button type="button" className="btn-success btn-sm enquiry-action" aria-label={`Convert ENQ-${r.enquiryNo}`}
                onClick={(e) => { e.stopPropagation(); setStatusModal({ id: r.id, next: 'converted' }); }}>
                Convert
              </button>
            )}
            {next.includes('cancelled') && (
              <button type="button" className="btn-danger btn-sm enquiry-action" aria-label={`Cancel ENQ-${r.enquiryNo}`}
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
          <h2>Enquiry Desk</h2>
          <p className="page-subtitle">Front-desk contacts before they become patients — Promise to Call-back trail</p>
        </div>
        <button type="button" className="btn-primary enquiry-new" onClick={() => setModalOpen(true)}>+ Log Enquiry</button>
      </div>
      <CounterRow counters={counters} />

      <FilterBar
        search={null}
        onApply={() => fetchEnquiries(0, statusFilter, typeFilter)}
        onClear={() => {
          setStatusFilter('');
          setTypeFilter('');
          fetchEnquiries(0, '', '');
        }}
      >
        <select aria-label="All Statuses" value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">All Statuses</option>
          {Object.entries(STATUS_LABELS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
        <select aria-label="All Types" value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)}>
          <option value="">All Types</option>
          {Object.entries(TYPE_LABELS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
      </FilterBar>

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        emptyMessage="No enquiries on the desk yet — log the first one."
        error={loadError}
        loading={loading}
        loadingLabel="Loading enquiries…"
        pageMeta={pageMeta}
        onPageChange={(p) => fetchEnquiries(p, statusFilter, typeFilter)}
      />

      {modalOpen && <LogEnquiryModal saving={saving} onClose={() => setModalOpen(false)} onSubmit={logEnquiry} />}
      {statusModal && (
        <StatusModal next={statusModal.next} saving={saving}
          onClose={() => setStatusModal(null)} onSubmit={moveStatus} />
      )}
    </div>
  );
}

function LogEnquiryModal({ saving, onClose, onSubmit }) {
  const [form, setForm] = useState({
    contactName: '', contactPhone: '', contactEmail: '', type: 'general', department: '', reason: '', notes: '',
  });
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  return (
    <Modal title="Log Enquiry" onClose={onClose}>
        <div className="form-group">
          <label htmlFor="enq-name">Contact name</label>
          <input id="enq-name" value={form.contactName} onChange={set('contactName')} />
        </div>
        <div className="form-group">
          <label htmlFor="enq-phone">Phone</label>
          <input id="enq-phone" value={form.contactPhone} onChange={set('contactPhone')} />
        </div>
        <div className="form-group">
          <label htmlFor="enq-type">Type</label>
          <select id="enq-type" value={form.type} onChange={set('type')}>
            {Object.entries(TYPE_LABELS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </div>
        <div className="form-group">
          <label htmlFor="enq-dept">Department</label>
          <input id="enq-dept" value={form.department} onChange={set('department')} />
        </div>
        <div className="form-group">
          <label htmlFor="enq-reason">Reason</label>
          <input id="enq-reason" value={form.reason} onChange={set('reason')} />
        </div>
        <div className="form-group">
          <label htmlFor="enq-notes">Notes</label>
          <input id="enq-notes" value={form.notes} onChange={set('notes')} />
        </div>
        <div className="modal-actions">
          <button type="button" className="btn-ghost" onClick={onClose}>Cancel</button>
          <button type="button" className="btn-primary" disabled={saving}
            onClick={() => onSubmit({ ...form, reason: form.reason || '' })}>Log Enquiry</button>
        </div>
    </Modal>
  );
}

function StatusModal({ next, saving, onClose, onSubmit }) {
  const [note, setNote] = useState('');
  return (
    <Modal title={`Mark ${STATUS_LABELS[next].toLowerCase()}`} onClose={onClose}>
        <div className="form-group">
          <label htmlFor="enq-note">Note</label>
          <input id="enq-note" data-testid="enq-note"
            placeholder={next === 'follow_up' ? 'What was promised, and when' : 'Why'}
            value={note} onChange={(e) => setNote(e.target.value)} />
        </div>
        <div className="modal-actions">
          <button type="button" className="btn-ghost" onClick={onClose}>Cancel</button>
          <button type="button" className="btn-primary" disabled={saving}
            onClick={() => onSubmit({ next, note })}>{STATUS_LABELS[next]}</button>
        </div>
    </Modal>
  );
}
