import { useState, useEffect } from 'react';
import { patientApi, encounterApi, billingApi } from '../api';
import useToastStore from '../store/toastStore';
import { Link } from 'react-router-dom';

export default function Patients() {
  const [patients, setPatients] = useState([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [showRegister, setShowRegister] = useState(false);
  const [selectedPatient, setSelectedPatient] = useState(null);
  const [patientEncounters, setPatientEncounters] = useState([]);
  const [patientInvoices, setPatientInvoices] = useState([]);
  const [profileTab, setProfileTab] = useState('encounters');
  const addToast = useToastStore((s) => s.addToast);

  const [form, setForm] = useState({
    name: '', age: '', gender: 'male', phone: '', email: '',
    address: '', bloodGroup: '', dpdpConsent: false, consentPurpose: 'Treatment and billing',
  });

  useEffect(() => { fetchPatients(); }, []);

  const fetchPatients = async (q) => {
    setLoading(true);
    try {
      const { data } = await patientApi.list(q || undefined);
      setPatients(data);
    } catch {} finally { setLoading(false); }
  };

  const handleSearch = (e) => {
    setSearch(e.target.value);
    if (e.target.value.length > 2) fetchPatients(e.target.value);
    else if (!e.target.value) fetchPatients();
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    try {
      const { data } = await patientApi.register({
        ...form,
        age: parseInt(form.age),
      });
      addToast(`Patient ${data.name} registered. UHID: ${data.uhid}`, 'success');
      setShowRegister(false);
      setForm({ name: '', age: '', gender: 'male', phone: '', email: '', address: '', bloodGroup: '', dpdpConsent: false, consentPurpose: 'Treatment and billing' });
      fetchPatients();
    } catch (err) {
      addToast(err.response?.data?.message || 'Registration failed', 'critical');
    }
  };

  const openPatientProfile = async (p) => {
    setSelectedPatient(p);
    setProfileTab('encounters');
    try {
      const [encRes, invRes] = await Promise.all([
        encounterApi.listByPatient(p.id),
        billingApi.getInvoices(p.id),
      ]);
      setPatientEncounters(encRes.data || []);
      setPatientInvoices(invRes.data || []);
    } catch {}
  };

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div>
          <h1>Patients</h1>
          <p>Search, register, and manage patients</p>
        </div>
        <button className="btn-primary" onClick={() => setShowRegister(true)}>+ Register Patient</button>
      </div>

      <div className="card" style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', gap: 12 }} className="search-row">
          <input
            placeholder="Search patients by name..."
            value={search}
            onChange={handleSearch}
            style={{ maxWidth: 400 }}
          />
          <button className="btn-ghost btn-sm" onClick={() => { setSearch(''); fetchPatients(); }}>Clear</button>
        </div>
      </div>

      {selectedPatient ? (
        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20 }} className="profile-header">
            <div>
              <h3 style={{ color: 'var(--text-white)', fontSize: '1.2rem', marginBottom: 4 }}>
                {selectedPatient.name}
                <span style={{ color: 'var(--text-dim)', fontWeight: 400, marginLeft: 8 }}>
                  ({selectedPatient.gender}, {selectedPatient.age})
                </span>
              </h3>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-dim)' }}>
                {selectedPatient.uhid} • {selectedPatient.phone} • {selectedPatient.email}
              </div>
              <div style={{ marginTop: 6, display: 'flex', gap: 8, alignItems: 'center' }} className="badges-row">
                <span className={`badge ${selectedPatient.dpdpConsent ? 'badge-success' : 'badge-danger'}`}>
                  DPDP: {selectedPatient.dpdpConsent ? 'Consented' : 'Not Consented'}
                </span>
                <span className="badge badge-info">
                  Outstanding: ₹{selectedPatient.outstanding || 0}
                </span>
              </div>
            </div>
            <button className="btn-ghost btn-sm" onClick={() => setSelectedPatient(null)}>← Back</button>
          </div>

          <div className="tabs">
            {['encounters', 'invoices', 'history'].map((tab) => (
              <button key={tab} className={`tab ${profileTab === tab ? 'active' : ''}`}
                onClick={() => setProfileTab(tab)}>
                {tab.charAt(0).toUpperCase() + tab.slice(1)}
              </button>
            ))}
          </div>

          {profileTab === 'encounters' && (
            <table>
              <thead><tr><th>Date</th><th>Doctor</th><th>Complaint</th><th>Status</th></tr></thead>
              <tbody>
                {patientEncounters.map((e) => (
                  <tr key={e.id}>
                    <td>{new Date(e.createdAt).toLocaleDateString()}</td>
                    <td>{e.doctorId}</td>
                    <td>{e.chiefComplaint || '-'}</td>
                    <td><span className={`badge badge-${e.status === 'signed' ? 'success' : e.status === 'open' ? 'warning' : 'default'}`}>{e.status}</span></td>
                  </tr>
                ))}
                {patientEncounters.length === 0 && <tr><td colSpan={4} style={{ textAlign: 'center', color: 'var(--text-dim)' }}>No encounters</td></tr>}
              </tbody>
            </table>
          )}

          {profileTab === 'invoices' && (
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
                {patientInvoices.length === 0 && <tr><td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-dim)' }}>No invoices</td></tr>}
              </tbody>
            </table>
          )}

          {profileTab === 'history' && (
            <div style={{ padding: 20, color: 'var(--text-dim)' }}>Full medical history view coming soon.</div>
          )}
        </div>
      ) : (
        <div className="card">
          {loading ? <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-dim)' }}>Loading patients...</div> : (
            <table>
              <thead><tr><th>UHID</th><th>Name</th><th>Age/Gender</th><th>Phone</th><th>Blood</th><th>DPDP</th><th>Outstanding</th><th></th></tr></thead>
              <tbody>
                {patients.map((p) => (
                  <tr key={p.id} style={{ cursor: 'pointer' }} onClick={() => openPatientProfile(p)}>
                    <td style={{ fontFamily: 'monospace', color: 'var(--primary)' }}>{p.uhid}</td>
                    <td style={{ fontWeight: 500 }}>{p.name}</td>
                    <td>{p.age}/{p.gender?.[0]?.toUpperCase()}</td>
                    <td>{p.phone}</td>
                    <td>{p.bloodGroup || '-'}</td>
                    <td><span className={`badge ${p.dpdpConsent ? 'badge-success' : 'badge-danger'}`}>{p.dpdpConsent ? 'Yes' : 'No'}</span></td>
                    <td>₹{p.outstanding || 0}</td>
                    <td>
                      <Link to={`/patients/${p.id}`} className="btn-ghost btn-sm" style={{ textDecoration: 'none' }}
                        onClick={(e) => e.stopPropagation()}>View</Link>
                    </td>
                  </tr>
                ))}
                {patients.length === 0 && <tr><td colSpan={8} style={{ textAlign: 'center', padding: 40, color: 'var(--text-dim)' }}>No patients found. Register a new patient.</td></tr>}
              </tbody>
            </table>
          )}
        </div>
      )}

      {showRegister && (
        <div className="modal-overlay" onClick={() => setShowRegister(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Register New Patient</h2>
              <button className="btn-ghost btn-sm" onClick={() => setShowRegister(false)}>✕</button>
            </div>
            <form onSubmit={handleRegister}>
              <div className="form-group">
                <label>Full Name *</label>
                <input value={form.name} onChange={(e) => setForm({...form, name: e.target.value})} required />
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label>Age *</label>
                  <input type="number" min={0} max={150} value={form.age} onChange={(e) => setForm({...form, age: e.target.value})} required />
                </div>
                <div className="form-group">
                  <label>Gender</label>
                  <select value={form.gender} onChange={(e) => setForm({...form, gender: e.target.value})}>
                    <option value="male">Male</option>
                    <option value="female">Female</option>
                    <option value="other">Other</option>
                  </select>
                </div>
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label>Phone *</label>
                  <input value={form.phone} onChange={(e) => setForm({...form, phone: e.target.value})} required />
                </div>
                <div className="form-group">
                  <label>Email</label>
                  <input type="email" value={form.email} onChange={(e) => setForm({...form, email: e.target.value})} />
                </div>
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label>Blood Group</label>
                  <select value={form.bloodGroup} onChange={(e) => setForm({...form, bloodGroup: e.target.value})}>
                    <option value="">Select</option>
                    <option value="A+">A+</option><option value="A-">A-</option><option value="B+">B+</option>
                    <option value="B-">B-</option><option value="AB+">AB+</option><option value="AB-">AB-</option>
                    <option value="O+">O+</option><option value="O-">O-</option>
                  </select>
                </div>
                <div className="form-group">
                  <label>Address</label>
                  <input value={form.address} onChange={(e) => setForm({...form, address: e.target.value})} />
                </div>
              </div>
              <div className="form-group">
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
                  <input type="checkbox" style={{ width: 'auto' }} checked={form.dpdpConsent}
                    onChange={(e) => setForm({...form, dpdpConsent: e.target.checked})} />
                  DPDP Consent Granted *
                </label>
              </div>
              {form.dpdpConsent && (
                <div className="form-group">
                  <label>Consent Purpose</label>
                  <input value={form.consentPurpose} onChange={(e) => setForm({...form, consentPurpose: e.target.value})} />
                </div>
              )}
              <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end', marginTop: 20 }}>
                <button type="button" className="btn-ghost" onClick={() => setShowRegister(false)}>Cancel</button>
                <button type="submit" className="btn-primary">Register Patient</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
