import { useState, useEffect, useLayoutEffect, useRef } from 'react';
import { patientApi, encounterApi, billingApi } from '../api';
import Modal from '../components/Modal';
import useAuthStore from '../store/authStore';
import usePatientBannerStore from '../store/patientBannerStore';
import useToastStore from '../store/toastStore';
import { Link, useNavigate, useParams } from 'react-router-dom';
import DataTable from '../components/DataTable.jsx';
import CounterRow from '../components/CounterRow.jsx';
import FilterBar from '../components/FilterBar.jsx';

export default function Patients() {
  const { id } = useParams();
  const tenant = useAuthStore(s => s.tenantId);
  const user = useAuthStore(s => s.user?.username);
  return <PatientWorkspace key={JSON.stringify([tenant, user, id])} id={id} />;
}

function PatientWorkspace({ id }) {
  const navigate = useNavigate();
  const [profileError, setProfileError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [historyError, setHistoryError] = useState(false);

  const [patients, setPatients] = useState([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [registering, setRegistering] = useState(false);
  const [showRegister, setShowRegister] = useState(false);
  const [selectedPatient, setSelectedPatient] = useState(null);
  const [patientEncounters, setPatientEncounters] = useState(null);
  const [patientInvoices, setPatientInvoices] = useState(null);
  const [profileTab, setProfileTab] = useState('encounters');
  const [page, setPage] = useState(0);
  const listRequest = useRef(0);
  const [pageMeta, setPageMeta] = useState(null);
  // Hub state: the counter rail and the parity-spec cohort/blood filters.
  const [summary, setSummary] = useState(null);
  const [typeFilter, setTypeFilter] = useState('');
  const [bloodFilter, setBloodFilter] = useState('');
  // A failed request is not an empty result. Held separately from `patients` so the table
  // can say so, rather than reporting a server fault as "this hospital has no patients".
  const [loadError, setLoadError] = useState(null);
  // Per-field validation errors from the API (issue 117) — mapped onto controls.
  const [formErrors, setFormErrors] = useState({});
  const addToast = useToastStore((s) => s.addToast);
  const role = useAuthStore((s) => s.user?.role?.toLowerCase());
  // Matches the API's role gate: consent is captured at the front desk.
  const canManageConsent = role === 'receptionist' || role === 'admin';
  const [consentSaving, setConsentSaving] = useState(false);
  const [withdrawingConsent, setWithdrawingConsent] = useState(false);
  const [withdrawReason, setWithdrawReason] = useState('');

  const [form, setForm] = useState({
    name: '', age: '', gender: 'male', phone: '', email: '',
    address: '', bloodGroup: '', consent: { treatment: false, billing: false, research: false, purpose: { treatment: '', billing: '', research: '' } },
  });

  useEffect(() => { if (!id) fetchSummary(); }, [id]);

  // Filter changes are a different result set — back to page 0. This effect
  // also performs the initial load, so mount fetches the list exactly once.
  useEffect(() => { if (!id) fetchPatients(currentSearchTerm(), 0); }, [id, typeFilter, bloodFilter]);

  const fetchSummary = async () => {
    try {
      const { data } = await patientApi.summary();
      setSummary(data);
    } catch { /* the rail is additive; a failed count must not hide the list */ }
  };

  // Newest first, so a patient registered a moment ago is on page 0 rather than
  // on a page the UI never shows. Every list call returns to page 0 unless it is
  // an explicit page change, because a search or a new registration is a
  // different result set.
  const fetchPatients = async (q, nextPage = 0) => {
    const request = ++listRequest.current;
    setLoading(true);
    try {
      const { data, meta } = await patientApi.list({
        search: q || undefined,
        type: typeFilter || undefined,
        bloodGroup: bloodFilter || undefined,
        page: nextPage,
      });
      if (request !== listRequest.current) return;
      setPatients(data);
      setPageMeta(meta);
      setPage(nextPage);
      setLoadError(null);
    } catch (err) {
      if (request !== listRequest.current) return;
      // Reporting this as "No patients found" tells a clinician the record does not exist,
      // when in fact nothing was known: the server failed. Keep the two apart.
      setPatients([]);
      setPageMeta(null);
      setLoadError(
        err.response?.data?.message
          ? `Could not load patients: ${err.response.data.message}`
          : 'Could not load patients. The server did not respond as expected.'
      );
    } finally { if (request === listRequest.current) setLoading(false); }
  };

  const currentSearchTerm = () => (search.length > 2 ? search : undefined);

  // A name fragment plus a filter is refused by the backend (an encrypted name
  // cannot ride a SQL filter), so the box says what the combination allows.
  const filterActive = typeFilter !== '' || bloodFilter !== '';

  const TYPE_RADIOS = [
    { value: '', label: 'All' },
    { value: 'active', label: 'Active' },
    { value: 'ipd', label: 'Admitted (IPD)' },
    { value: 'discharged', label: 'Discharged' },
  ];

  const counters = summary ? [
    { label: 'Total Records', value: summary.total, tone: 'neutral' },
    { label: 'New Today', value: summary.newToday, tone: 'info' },
    { label: 'Active OPD', value: summary.activeOpd, tone: 'warning' },
    { label: 'Inpatient (IPD)', value: summary.ipd, tone: 'info' },
    { label: 'Discharged', value: summary.discharged, tone: 'success' },
  ] : [];

  // Serial numbers continue across pages (page-size-relative), matching the
  // parity spec's S.No. column.
  const snOffset = ((pageMeta?.page ?? page) * (pageMeta?.size ?? 20)) + 1;
  const serialOf = new Map(patients.map((r, i) => [r.id, snOffset + i]));

  const columns = [
    {
      key: 'sno', label: 'S.No.', align: 'right',
      render: (p) => serialOf.get(p.id) ?? '',
    },
    { key: 'uhid', label: 'UHID / Reg No.', mono: true },
    { key: 'name', label: 'Patient Name' },
    {
      key: 'age', label: 'Age/Gender',
      render: (p) => `${p.age}/${p.gender ? p.gender[0].toUpperCase() : '-'}`,
    },
    { key: 'bloodGroup', label: 'Blood Group', render: (p) => p.bloodGroup || '-' },
    { key: 'phone', label: 'Phone' },
    {
      key: 'dpdpConsent', label: 'DPDP',
      render: (p) => (
        <span className={`badge ${p.dpdpConsent ? 'badge-success' : 'badge-danger'}`}>
          {p.dpdpConsent ? 'Consented' : 'Not Consented'}
        </span>
      ),
    },
    { key: 'outstanding', label: 'Outstanding', align: 'right', render: (p) => `₹${p.outstanding || 0}` },
    {
      key: 'actions', label: '',
      render: (p) => role !== 'billing' && (
        <Link onClick={event => event.stopPropagation()} to={`/patients/${p.id}`} className="btn-ghost btn-sm" style={{ textDecoration: 'none' }}>
          View
        </Link>
      ),
    },
  ];

  const handleSearch = (e) => {
    const term = e.target.value;
    setSearch(term);
    // Refetch for a real term, for an empty box, AND when the term shrinks back
    // under the minimum length. The last case previously left the previous
    // result set on screen, so deleting characters showed rows that no longer
    // matched what was typed.
    fetchPatients(term.length > 2 ? term : undefined, 0);
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    if (registering) return;
    setRegistering(true);
    try {
      const { consent, ...patient } = form;
      const purposes = ['Treatment', 'Billing', 'Research']
        .filter(label => consent[label.toLowerCase()])
        .map(label => {
          const explanation = consent.purpose[label.toLowerCase()].trim();
          return explanation ? `${label}: ${explanation}` : label;
        });
      const { data } = await patientApi.register({
        ...patient,
        age: Number(form.age),
        dpdpConsent: purposes.length > 0,
        consentPurpose: purposes.length ? purposes.join('; ') : undefined,
      });
      setFormErrors({});
      addToast(`Patient ${data.name} registered. UHID: ${data.uhid}`, 'success');
      setShowRegister(false);
      setForm({ name: '', age: '', gender: 'male', phone: '', email: '', address: '', bloodGroup: '', consent: { treatment: false, billing: false, research: false, purpose: { treatment: '', billing: '', research: '' } } });
      fetchPatients();
      fetchSummary();
    } catch (err) {
      const valErrs = err?.response?.data?.validationErrors || {};
      if (Object.keys(valErrs).length > 0) {
        setFormErrors(valErrs);
        addToast(err.response?.data?.message || 'Please fix the highlighted fields.', 'warning');
      } else {
        setFormErrors({});
        addToast(err.response?.data?.message || 'Registration failed', 'critical');
      }
    } finally {
      setRegistering(false);
    }
  };

  useEffect(() => {
    if (!id) return;
    let current = true;
    setProfileError('');
    patientApi.get(id).then(({ data }) => {
      if (current) setSelectedPatient(data);
    }).catch(err => {
      const status = err.response?.status;
      if (current) setProfileError(status === 404 ? 'Patient not found.' :
        status === 403 ? 'Access to this patient was denied.' : 'Patient profile unavailable.');
    });
    return () => { current = false; };
  }, [id, attempt]);

  useLayoutEffect(() => {
    if (!selectedPatient) return;
    const { setPatient, clearPatient } = usePatientBannerStore.getState();
    setPatient({ ...selectedPatient, sex: selectedPatient.gender });
    return clearPatient;
  }, [selectedPatient]);

  const selectedId = selectedPatient?.id;
  const [historyAttempt, setHistoryAttempt] = useState(0);
  useEffect(() => {
    if (!selectedId || profileTab === 'history') return;
    let current = true;
    const encounters = profileTab === 'encounters';
    const setRows = encounters ? setPatientEncounters : setPatientInvoices;
    setRows(null);
    setHistoryError(false);
    const request = encounters ? encounterApi.listByPatient(selectedId) : billingApi.getInvoices(selectedId);
    request.then(({ data }) => {
      if (current) setRows(data);
    }).catch(() => {
      if (current) setHistoryError(true);
    });
    return () => { current = false; };
  }, [selectedId, profileTab, historyAttempt]);

  // A consent change is a new event, not a field edit: the API appends a row and
  // returns the patient with its cache updated. Replacing the row in the list
  // keeps the hidden profile and the table's DPDP badge in step.
  const applyConsentResult = (updated) => {
    setSelectedPatient(updated);
    setPatients((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
  };

  const recordConsent = async () => {
    setConsentSaving(true);
    try {
      const { data } = await patientApi.recordConsent(selectedPatient.id, {
        granted: true,
        purpose: 'Treatment and billing',
      });
      applyConsentResult(data);
      addToast('Consent recorded', 'success');
    } catch (err) {
      addToast(err.response?.data?.message || 'Could not record consent', 'critical');
    } finally {
      setConsentSaving(false);
    }
  };

  const withdrawConsent = async () => {
    setConsentSaving(true);
    try {
      const { data } = await patientApi.withdrawConsent(selectedPatient.id, withdrawReason.trim());
      applyConsentResult(data);
      setWithdrawingConsent(false);
      setWithdrawReason('');
      addToast('Consent withdrawn', 'success');
    } catch (err) {
      addToast(err.response?.data?.message || 'Could not withdraw consent', 'critical');
    } finally {
      setConsentSaving(false);
    }
  };

  if (id && !selectedPatient) {
    return (
      <section aria-label="Patient profile">
        <p role={profileError ? 'alert' : 'status'}>{profileError || 'Loading patient profile…'}</p>
        {profileError && <button className="btn-secondary" onClick={() => setAttempt(n => n + 1)}>Retry profile</button>}
        <Link to="/patients">Back to patients</Link>
      </section>
    );
  }

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div>
          <h1>Patients</h1>
          <p>Search, register, and manage patients</p>
        </div>
        {!id && canManageConsent && <button className="btn-primary" onClick={() => setShowRegister(true)}>+ Register Patient</button>}
      </div>

      {!id && <>
      <FilterBar
        search={{
          label: filterActive ? 'Search by UHID' : 'Search patients',
          placeholder: filterActive
            ? 'Search by UHID…'
            : 'Search by name or UHID…',
          value: search,
          onChange: handleSearch,
        }}
        onClear={() => {
          setSearch('');
          if (typeFilter || bloodFilter) {
            // The filter effect refetches on any change from a non-empty value.
            setTypeFilter('');
            setBloodFilter('');
          } else {
            fetchPatients(undefined, 0);
          }
        }}
      >
        <div role="radiogroup" aria-label="Patient type" style={{ display: 'flex', gap: 'var(--s2)', alignItems: 'center' }}>
          {TYPE_RADIOS.map(({ value, label }) => (
            <label key={value} style={{ display: 'flex', gap: 6, alignItems: 'center', cursor: 'pointer' }}>
              <input
                type="radio"
                name="patient-type"
                checked={typeFilter === value}
                onChange={() => setTypeFilter(value)}
              />
              {label}
            </label>
          ))}
        </div>
        <select
          aria-label="All blood groups"
          value={bloodFilter}
          onChange={(e) => setBloodFilter(e.target.value)}
        >
          <option value="">All blood groups</option>
          {['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'].map((b) => (
            <option key={b} value={b}>{b}</option>
          ))}
        </select>
      </FilterBar>

      <CounterRow counters={counters} />
      </>}

      {selectedPatient ? (
        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20 }} className="profile-header">
            <div>
              <h3 style={{ color: 'var(--ink)', fontSize: '1.2rem', marginBottom: 4 }}>
                {selectedPatient.name}
                <span style={{ color: 'var(--ink-faint)', fontWeight: 400, marginLeft: 8 }}>
                  ({selectedPatient.gender}, {selectedPatient.age})
                </span>
              </h3>
              <div style={{ fontSize: '0.85rem', color: 'var(--ink-faint)' }}>
                {selectedPatient.uhid} • {selectedPatient.phone} • {selectedPatient.email}
              </div>
              <div style={{ marginTop: 6, display: 'flex', gap: 8, alignItems: 'center' }} className="badges-row">
                <span className={`badge ${selectedPatient.dpdpConsent ? 'badge-success' : 'badge-danger'}`}>
                  DPDP: {selectedPatient.dpdpConsent ? 'Consented' : 'Not Consented'}
                </span>
                <span className="badge badge-info">
                  Outstanding: ₹{selectedPatient.outstanding || 0}
                </span>
                {canManageConsent && (selectedPatient.dpdpConsent ? (
                  <button type="button" className="btn-ghost btn-sm"
                    onClick={() => setWithdrawingConsent(true)}>
                    Withdraw consent
                  </button>
                ) : (
                  <button type="button" className="btn-secondary btn-sm"
                    onClick={recordConsent} disabled={consentSaving}>
                    {consentSaving ? 'Recording…' : 'Record consent'}
                  </button>
                ))}
              </div>
            </div>
            <button className="btn-ghost btn-sm" onClick={() => navigate('/patients')}>← Back</button>
          </div>

          <div className="tabs">
            {['encounters', 'invoices', 'history'].map((tab) => (
              <button key={tab} className={`tab ${profileTab === tab ? 'active' : ''}`}
                onClick={() => setProfileTab(tab)}>
                {tab.charAt(0).toUpperCase() + tab.slice(1)}
              </button>
            ))}
          </div>

          {profileTab !== 'history' && historyError && (
            <div role="alert"><span>{profileTab === 'encounters' ? 'Encounters unavailable.' : 'Invoices unavailable.'}</span>
              <button className="btn-secondary" onClick={() => setHistoryAttempt(n => n + 1)}>Retry</button>
            </div>
          )}
          {!historyError && ((profileTab === 'encounters' && !patientEncounters) || (profileTab === 'invoices' && !patientInvoices)) &&
            <p role="status">Loading records…</p>}
          {profileTab === 'encounters' && patientEncounters && (
            <table>
              <thead><tr><th>Date</th><th>Doctor</th><th>Complaint</th><th>Status</th></tr></thead>
              <tbody>
                {patientEncounters.map((e) => (
                  <tr key={e.id}>
                    <td>{new Date(e.createdAt).toLocaleDateString()}</td>
                    <td>{e.doctorName ?? 'Unknown clinician'}</td>
                    <td>{e.chiefComplaint || '-'}</td>
                    <td><span className={`badge badge-${e.status === 'signed' ? 'success' : e.status === 'open' ? 'warning' : 'default'}`}>{e.status}</span></td>
                  </tr>
                ))}
                {patientEncounters.length === 0 && <tr><td colSpan={4} style={{ textAlign: 'center', color: 'var(--ink-faint)' }}>No encounters</td></tr>}
              </tbody>
            </table>
          )}

          {profileTab === 'invoices' && patientInvoices && (
            <table>
              <thead><tr><th>Invoice #</th><th>Date</th><th>Total</th><th>Paid</th><th>Status</th></tr></thead>
              <tbody>
                {patientInvoices.map((inv) => (
                  <tr key={inv.id}>
                    <td>{inv.invoiceNumber}</td>
                    <td>{new Date(inv.invoiceDate).toLocaleDateString()}</td>
                    <td>₹{inv.totalAmount}</td>
                    <td>₹{inv.paidAmount}</td>
                    <td><span className={`badge badge-${inv.status === 'paid' ? 'success' : inv.status === 'issued' ? 'warning' : inv.status === 'partially_paid' ? 'info' : 'default'}`}>{inv.status}</span></td>
                  </tr>
                ))}
                {patientInvoices.length === 0 && <tr><td colSpan={5} style={{ textAlign: 'center', color: 'var(--ink-faint)' }}>No invoices</td></tr>}
              </tbody>
            </table>
          )}

          {profileTab === 'history' && (
            <div style={{ padding: 20, color: 'var(--ink-faint)' }}>Full medical history view coming soon.</div>
          )}
        </div>
      ) : (
        <div className="card">
          {loading ? (
            <div style={{ padding: 40, textAlign: 'center', color: 'var(--ink-faint)' }}>Loading patients...</div>
          ) : (
            <DataTable
              columns={columns}
              rows={patients}
              rowKey={(p) => p.id}
              onRowClick={role === 'billing' ? undefined : p => navigate(`/patients/${p.id}`)}
              emptyMessage="No patients found. Register a new patient."
              error={loadError}
              pageMeta={pageMeta}
              onPageChange={(p) => fetchPatients(currentSearchTerm(), p)}
            />
          )}
        </div>
      )}

      {withdrawingConsent && selectedPatient && (
        <Modal title="Withdraw consent" onClose={() => setWithdrawingConsent(false)} size="narrow">
          <p style={{ marginBottom: 16, color: 'var(--ink-muted)', fontSize: '0.9rem' }}>
            This records a withdrawal for {selectedPatient.name}. It does not delete the
            earlier grant: the consent history keeps both events, so the record shows what
            was agreed to and when it was withdrawn.
          </p>
          <div className="form-group">
            <label htmlFor="consent-withdraw-reason">Reason (optional)</label>
            <input id="consent-withdraw-reason" value={withdrawReason}
              onChange={(e) => setWithdrawReason(e.target.value)}
              placeholder="What the patient said" />
          </div>
          <div className="form-actions">
            <button type="button" className="btn-ghost" onClick={() => setWithdrawingConsent(false)}>
              Cancel
            </button>
            <button type="button" className="btn-danger" onClick={withdrawConsent} disabled={consentSaving}>
              {consentSaving ? 'Withdrawing…' : 'Withdraw consent'}
            </button>
          </div>
        </Modal>
      )}

      {showRegister && (
        <Modal title="Register New Patient" onClose={() => setShowRegister(false)}>
            <form onSubmit={handleRegister}>
              <div className="form-group">
                <label htmlFor="register-name">Full Name *</label>
                <input id="register-name"
                  aria-invalid={!!formErrors.name}
                  aria-describedby={formErrors.name ? 'err-name' : undefined}
                  className={formErrors.name ? 'input-error' : ''}
                  value={form.name} onChange={(e) => { setForm({...form, name: e.target.value}); setFormErrors(p => ({...p, name: undefined})); }} required />
                {formErrors.name && <span id="err-name" className="form-error" role="alert">{formErrors.name}</span>}
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label htmlFor="register-age">Age *</label>
                  <input id="register-age" type="number" min={0} max={150} aria-invalid={!!formErrors.age} aria-describedby={formErrors.age ? 'err-age' : undefined} className={formErrors.age ? 'input-error' : ''} value={form.age} onChange={(e) => { setForm({...form, age: e.target.value}); setFormErrors(p => ({...p, age: undefined})); }} required />
                  {formErrors.age && <span id="err-age" className="form-error" role="alert">{formErrors.age}</span>}
                </div>
                <div className="form-group">
                  <label htmlFor="register-gender">Gender</label>
                  <select id="register-gender" value={form.gender} onChange={(e) => setForm({...form, gender: e.target.value})}>
                    <option value="male">Male</option>
                    <option value="female">Female</option>
                    <option value="other">Other</option>
                  </select>
                </div>
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label htmlFor="register-phone">Phone *</label>
                  <input id="register-phone" aria-invalid={!!formErrors.phone} aria-describedby={formErrors.phone ? 'err-phone' : undefined} className={formErrors.phone ? 'input-error' : ''} value={form.phone} onChange={(e) => { setForm({...form, phone: e.target.value}); setFormErrors(p => ({...p, phone: undefined})); }} required />
                  {formErrors.phone && <span id="err-phone" className="form-error" role="alert">{formErrors.phone}</span>}
                </div>
                <div className="form-group">
                  <label htmlFor="register-email">Email</label>
                  <input id="register-email" type="email" aria-invalid={!!formErrors.email} aria-describedby={formErrors.email ? 'err-email' : undefined} className={formErrors.email ? 'input-error' : ''} value={form.email} onChange={(e) => { setForm({...form, email: e.target.value}); setFormErrors(p => ({...p, email: undefined})); }} />
                  {formErrors.email && <span id="err-email" className="form-error" role="alert">{formErrors.email}</span>}
                </div>
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label htmlFor="register-blood-group">Blood Group</label>
                  <select id="register-blood-group" value={form.bloodGroup} onChange={(e) => setForm({...form, bloodGroup: e.target.value})}>
                    <option value="">Select</option>
                    <option value="A+">A+</option><option value="A-">A-</option><option value="B+">B+</option>
                    <option value="B-">B-</option><option value="AB+">AB+</option><option value="AB-">AB-</option>
                    <option value="O+">O+</option><option value="O-">O-</option>
                  </select>
                </div>
                <div className="form-group">
                  <label htmlFor="register-address">Address</label>
                  <input id="register-address" value={form.address} onChange={(e) => setForm({...form, address: e.target.value})} />
                </div>
              </div>
              <div className="form-group consent">
                {['Treatment', 'Billing', 'Research'].map(label => {
                  const key = label.toLowerCase();
                  return (
                    <div key={key}>
                      <label className="consent__row">
                        <input type="checkbox" checked={form.consent[key]} onChange={event => {
                          const checked = event.target.checked;
                          setForm(prev => ({ ...prev, consent: { ...prev.consent, [key]: checked } }));
                        }} />
                        {label}
                      </label>
                      {form.consent[key] && (
                        <div className="form-group">
                          <label htmlFor={`register-purpose-${key}`}>Purpose ({key})</label>
                          <input id={`register-purpose-${key}`} value={form.consent.purpose[key]} onChange={event => {
                            const value = event.target.value;
                            setForm(prev => ({
                              ...prev,
                              consent: { ...prev.consent, purpose: { ...prev.consent.purpose, [key]: value } },
                            }));
                          }} />
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
              <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end', marginTop: 20 }}>
                <button type="button" className="btn-ghost" onClick={() => setShowRegister(false)}>Cancel</button>
                <button type="submit" disabled={registering} className="btn-primary">{registering ? 'Registering…' : 'Register Patient'}</button>
              </div>
            </form>
        </Modal>
      )}
    </div>
  );
}
