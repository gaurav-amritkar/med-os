/**
 * Appointments board (Wave 1 reception module).
 *
 * MedWorks grammar, verbatim: counters Total / Scheduled / Checked-in /
 * In-consultation / Completed / Cancelled / No-show; columns Appt #, Token,
 * Patient, Doctor, Time, Type, Status, Actions; filters status + type;
 * `+ Book Appointment`.
 *
 * Booking is the board's missing surface, restored: the day-token a patient
 * receives is issued by check-in (not booking) and the desk confirms it in the
 * toast, so the "how do tokens work?" question is answered where it happens.
 * Status moves follow the server's own role map — check-in belongs to the
 * front desk; start/complete belong to the doctor — and the UI only ever
 * offers a move the server would accept.
 *
 * Every list-page element comes from the Wave-0 shared components
 * (DataTable / CounterRow / FilterBar); a module that hand-rolls its table
 * drifts from the design grammar, so this module composes them.
 */

import { useEffect, useState } from 'react';
import DataTable from '../components/DataTable.jsx';
import Modal from '../components/Modal.jsx';
import CounterRow from '../components/CounterRow.jsx';
import FilterBar from '../components/FilterBar.jsx';
import useToastStore from '../store/toastStore';
import useAuthStore from '../store/authStore';
import { appointmentApi, patientApi, userApi } from '../api';

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

// Who may act on a row, mirroring the @PreAuthorize map on
// AppointmentController: a move the UI offers is a move the server accepts.
const MAY_CHECK_IN = ['doctor', 'admin', 'receptionist'];
const MAY_MOVE = ['doctor', 'admin'];

// The lifecycle, one leg at a time. Terminal rows offer delete rather than
// another status, mirroring the server's deleteTerminal-only-for-terminal
// rule rather than inventing a UI-level exception.
const ACTIONS = {
  scheduled: [
    { label: 'Check in', status: 'checked_in', via: 'checkIn', roles: MAY_CHECK_IN, tone: 'btn-success', token: true },
    { label: 'Cancel', status: 'cancelled', via: 'updateStatus', roles: MAY_MOVE, tone: 'btn-danger' },
  ],
  checked_in: [
    // 'Start' not 'Start consultation': row actions must fit beside the other
    // verbs inside the Actions column at desk widths; the queue board's
    // now-serving panel carries the context for what is starting.
    { label: 'Start', status: 'in_consultation', via: 'updateStatus', roles: MAY_MOVE, tone: 'btn-primary' },
    { label: 'No-show', status: 'no_show', via: 'updateStatus', roles: MAY_MOVE, tone: 'btn-danger' },
  ],
  in_consultation: [
    { label: 'Complete', status: 'completed', via: 'updateStatus', roles: MAY_MOVE, tone: 'btn-success' },
  ],
  completed: [{ label: 'Delete', role: MAY_MOVE, via: 'delete', tone: 'btn-ghost' }],
  cancelled: [{ label: 'Delete', role: MAY_MOVE, via: 'delete', tone: 'btn-ghost' }],
  no_show: [{ label: 'Delete', role: MAY_MOVE, via: 'delete', tone: 'btn-ghost' }],
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
  const [bookModalOpen, setBookModalOpen] = useState(false);
  const [refreshTick, setRefreshTick] = useState(0); // bumped after a write so the board refetches
  const role = useAuthStore((s) => s.role);
  const addToast = useToastStore((s) => s.addToast);

  async function load(nextPage = page) {
    setLoading(true);
    setLoadError(null);
    try {
      const [listRes, sumRes] = await Promise.all([
        appointmentApi.list(statusFilter, '', typeFilter, nextPage),
        appointmentApi.summary(),
      ]);
      setRows(listRes.data || []);
      setPageMeta(listRes.meta);
      setSummary(sumRes.data);
    } catch (e) {
      setLoadError(e?.response?.data?.message || 'Failed to load appointments');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(page); // eslint-disable-line react-hooks/exhaustive-deps -- mirrors EnquiryDesk's fetch-on-mount
  }, [statusFilter, typeFilter, page, refreshTick]);

  /**
   * One action per row per state — the lifecycle never branches. The handler
   * is not awaited by its onClick: it owns its own failure, so a rejected
   * call (a 409 from a race, a dropped session) is announced as a toast and
   * never escapes as an unhandled rejection.
   */
  async function act(row, action) {
    try {
      if (action.via === 'delete') {
        if (!window.confirm(`Delete ${row.apptRef} (${row.patientName})? This cannot be undone.`)) return;
        await appointmentApi.delete(row.id);
        addToast('Appointment deleted', 'success');
        return load(page);
      }
      // checkIn and delete take no body on the server; only status moves do.
      const res = (action.via === 'updateStatus'
        ? await appointmentApi.updateStatus(row.id, { status: action.status })
        : await appointmentApi[action.via](row.id));
      const updated = res?.data;
      // The token toast is the story of how tokens are given, so it only
      // speaks when it has the number to name.
      if (action.token && updated?.tokenNo != null) {
        addToast(`Token ${updated.tokenNo} issued to ${row.patientName}`, 'success');
      } else {
        addToast(`${row.patientName} marked ${STATUS_LABELS[action.status].toLowerCase()}`, 'success');
      }
      return load(page);
    } catch (err) {
      addToast(err?.response?.data?.message || `Could not ${action.label.toLowerCase()} ${row.patientName}`, 'critical');
      load(page);
    }
  }

  const counters = summary ? [
    { label: 'Total', value: summary.total, tone: 'neutral' },
    { label: 'Scheduled', value: summary.scheduled, tone: 'info' },
    { label: 'Checked-in', value: summary.checkedIn, tone: 'warning' },
    { label: 'In consultation', value: summary.inConsultation, tone: 'info' },
    { label: 'Completed', value: summary.completed, tone: 'success' },
    { label: 'Cancelled', value: summary.cancelled, tone: summary.cancelled > 0 ? 'danger' : 'neutral' },
    { label: 'No-show', value: summary.noShow, tone: summary.noShow > 0 ? 'danger' : 'neutral' },
  ] : [];

  const columns = [
    { key: 'apptRef', label: 'Appt #', mono: true, render: (r) => r.apptRef || '—' },
    {
      key: 'tokenNo', label: 'Token',
      // The token is an ordinal the queue hands out, not a word — mono face.
      render: (r) => (r.tokenNo ? <span className="queue-token">{`Token ${r.tokenNo}`}</span> : '—'),
    },
    {
      key: 'patientName', label: 'Patient',
      render: (r) => `${r.patientName} · ${r.patientUhid}`.replace(' · undefined', ''),
    },
    { key: 'doctorName', label: 'Doctor' },
    { key: 'time', label: 'Time' },
    {
      key: 'appointmentType', label: 'Type',
      // list DTO ships `type`; keep the fixture-shot `appointmentType` honoured.
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
        const actions = (ACTIONS[r.status] || []).filter((a) => !a.roles || a.roles.includes(role));
        if (actions.length === 0) {
          return <span className="badge badge-neutral">Closed</span>;
        }
        return (
          <span className="badges-row">
            {actions.map((a) => (
              <button
                key={a.label}
                type="button"
                className={`btn ${a.tone} btn-sm`}
                onClick={() => act(r, a)}
              >
                {a.label}
              </button>
            ))}
          </span>
        );
      },
    },
  ];

  return (
    <div className="appointments-page">
      <div className="page-header">
        <div>
          <h2>Appointments</h2>
          <p>Today&apos;s board — book from the desk, check in to issue the day-token.</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setBookModalOpen(true)}>
          + Book Appointment
        </button>
      </div>
      <CounterRow counters={counters} />
      <FilterBar
        onClear={() => {
          setStatusFilter('');
          setTypeFilter('');
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
      {loadError && <div className="error-banner">{loadError}</div>}
      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        pageMeta={pageMeta}
        onPageChange={setPage}
        loading={loading}
        loadingLabel="Loading appointments…"
        emptyMessage="Nothing booked for today yet — add the first appointment."
      />
      {bookModalOpen && (
        <BookAppointmentModal
          onClose={() => setBookModalOpen(false)}
          onBooked={() => {
            setBookModalOpen(false);
            setRefreshTick((n) => n + 1);
          }}
        />
      )}
    </div>
  );
}

/**
 * The booking form. A booking is four decisions the desk already knows how to
 * make: which patient, which doctor, when, and what for. Each decision names
 * itself when missing, so the form refuses to submit half a booking.
 */
function BookAppointmentModal({ onClose, onBooked }) {
  const addToast = useToastStore((s) => s.addToast);
  const [patient, setPatient] = useState(null);
  const [patientQuery, setPatientQuery] = useState('');
  const [patientResults, setPatientResults] = useState(null);
  const [doctors, setDoctors] = useState(null);
  const [doctorId, setDoctorId] = useState('');
  const [when, setWhen] = useState(defaultTime());
  const [type, setType] = useState('first_visit');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState(null);
  const [saving, setSaving] = useState(false);

  // One search per pause in typing, not one request per keystroke (front-desk
  // performance review). The same debounce the board's other pickers use.
  useEffect(() => {
    if (!patientQuery.trim()) { setPatientResults(null); return undefined; }
    const t = setTimeout(() => {
      patientApi.list({ search: patientQuery.trim() })
        .then((res) => setPatientResults(res.data || []))
        .catch(() => setPatientResults([]));
    }, 250);
    return () => clearTimeout(t);
  }, [patientQuery]);

  useEffect(() => {
    userApi.list()
      .then((res) => setDoctors((res.data || []).filter((u) => u.role === 'doctor' && u.active)))
      .catch(() => setDoctors([]));
  }, []);

  async function submit(e) {
    e.preventDefault();
    const next = {};
    if (!patient) next.patient = 'Choose a patient';
    if (!doctorId) next.doctor = 'Choose a doctor';
    if (!when) next.when = 'Pick a date and time';
    if (!reason.trim()) next.reason = 'Write the reason';
    else if (reason.trim().length > 2000) next.reason = 'Reason must be 2000 characters or fewer';
    if (Object.keys(next).length > 0) { setErrors(next); return; }
    setSaving(true);
    try {
      await appointmentApi.book({
        patientId: patient.id,
        doctorId,
        appointmentDate: when,
        type,
        reason: reason.trim(),
      });
      addToast(`Booked ${patient.name} · ${apptTimeLabel(when)}`, 'success');
      onBooked();
    } catch (err) {
      setSaving(false);
      addToast(err?.response?.data?.message || 'Could not book the appointment', 'critical');
    }
  }

  return (
    <Modal title="Book Appointment" onClose={onClose}>
        <div className="form-group">
          <label htmlFor="appt-patient">Patient</label>
          {patient ? (
            <div className="appt-patient-chip">
              <span>
                {patient.name} · {patient.uhid}
              </span>
              <button type="button" className="btn btn-ghost btn-sm"
                onClick={() => { setPatient(null); setPatientQuery(''); }}>
                Change
              </button>
            </div>
          ) : (
            <>
              <input
                id="appt-patient"
                name="appt-patient"
                autoComplete="off"
                placeholder="Search by name or UHID…"
                value={patientQuery}
                onChange={(e) => setPatientQuery(e.target.value)}
              />
              {errors?.patient ? <div className="field-error">{errors.patient}</div> : null}
              {patientResults ? (
                patientResults.length > 0 ? (
                  <div className="appt-patient-results">
                    {patientResults.slice(0, 6).map((p) => (
                      <button type="button" key={p.id}
                        className="appt-patient-option"
                        onClick={() => { setPatient(p); setPatientResults(null); setPatientQuery(''); }}>
                        {p.name} · {p.uhid}
                      </button>
                    ))}
                  </div>
                ) : (
                  <div className="field-error">No patient matches that search. Register them first.</div>
                )
              ) : null}
            </>
          )}
        </div>

        <div className="form-group">
          <label htmlFor="appt-doctor">Doctor</label>
          <select id="appt-doctor" value={doctorId} onChange={(e) => setDoctorId(e.target.value)}>
            <option value="">Choose a doctor…</option>
            {(doctors || []).map((d) => (
              <option key={d.id} value={d.id}>
                {d.fullName}{d.specialization ? ` · ${d.specialization}` : ''}
              </option>
            ))}
          </select>
          {errors?.doctor ? <div className="field-error">{errors.doctor}</div> : null}
        </div>

        <div className="form-group">
          <label htmlFor="appt-date">Date and time</label>
          <input id="appt-date" type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} />
          {errors?.when ? <div className="field-error">{errors.when}</div> : null}
        </div>

        <div className="form-group">
          <label>Visit type</label>
          <div className="appt-type-radios">
            {Object.entries(TYPE_LABELS).map(([v, l]) => (
              <label key={v} className="appt-type-radio">
                <input type="radio" name="appt-type" value={v}
                  checked={type === v} onChange={() => setType(v)} />
                {l}
              </label>
            ))}
          </div>
        </div>

        <div className="form-group">
          <label htmlFor="appt-reason">Reason</label>
          <textarea id="appt-reason" placeholder="Why the patient is being seen…"
            value={reason} onChange={(e) => setReason(e.target.value)} />
          {errors?.reason ? <div className="field-error">{errors.reason}</div> : null}
        </div>

        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={saving} onClick={submit}>
            {saving ? 'Booking…' : 'Book'}
          </button>
        </div>
    </Modal>
  );
}

function defaultTime() {
  const now = new Date();
  now.setMinutes(now.getMinutes() + 10 - (now.getMinutes() % 10), 0, 0);
  return localInput(now);
}

function localInput(d) {
  const pad = (n) => String(n).padStart(2, '0');
  // datetime-local never gains a seconds suffix from the value setter the
  // server validates against: yyyy-MM-dd'T'HH:mm.
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function apptTimeLabel(inputValue) {
  const [date, time] = String(inputValue || '').split('T');
  return time ? `${time} on ${date}` : inputValue;
}
