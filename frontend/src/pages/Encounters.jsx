import { useState, useEffect } from 'react';
import { encounterApi, patientApi, pharmacyApi } from '../api';
import useAuthStore from '../store/authStore';
import useToastStore from '../store/toastStore';

export default function Encounters() {
  const [patients, setPatients] = useState([]);
  const [selectedPatient, setSelectedPatient] = useState(null);
  const [encounters, setEncounters] = useState([]);
  const [activeEncounter, setActiveEncounter] = useState(null);
  const [vitals, setVitals] = useState({ bp: '', pulse: '', temp: '', spo2: '', weight: '' });
  const [diagnosis, setDiagnosis] = useState('');
  const [chiefComplaint, setChiefComplaint] = useState('');
  const [aiSuggestions, setAiSuggestions] = useState([]);
  const [suggestLoading, setSuggestLoading] = useState(false);
  const [prescriptions, setPrescriptions] = useState([]);
  const [medicines, setMedicines] = useState([]);
  const addToast = useToastStore((s) => s.addToast);

  useEffect(() => {
    patientApi.list().then(({ data }) => setPatients(data)).catch(() => {});
    pharmacyApi.listMedicines().then(({ data }) => setMedicines(data)).catch(() => {});
  }, []);

  const startEncounter = async (patient) => {
    setSelectedPatient(patient);
    setActiveEncounter(null);
    setDiagnosis('');
    setChiefComplaint('');
    setAiSuggestions([]);
    setVitals({ bp: '', pulse: '', temp: '', spo2: '', weight: '' });
    try {
      const { data } = await encounterApi.listByPatient(patient.id);
      setEncounters(data || []);
    } catch {}
  };

  const createEncounter = async () => {
    if (!selectedPatient) return;
    try {
      const { data } = await encounterApi.create({
        patientId: selectedPatient.id,
        chiefComplaint,
        diagnosis,
        vitals,
      });
      setActiveEncounter(data);
      setEncounters([data, ...encounters]);
      addToast('Encounter created', 'success');
    } catch (err) {
      addToast(err.response?.data?.message || 'Error', 'critical');
    }
  };

  const fetchSuggestions = async () => {
    if (!diagnosis && !chiefComplaint) return;
    setSuggestLoading(true);
    try {
      const { data } = await encounterApi.suggestMedicines({
        diseaseDescription: diagnosis,
        chiefComplaint,
      });
      setAiSuggestions(data || []);
    } catch {} finally { setSuggestLoading(false); }
  };

  const addPrescription = async (medicineId, dosage, frequency, duration) => {
    if (!activeEncounter || !selectedPatient) return;
    try {
      await encounterApi.addPrescription(activeEncounter.id, {
        encounterId: activeEncounter.id,
        patientId: selectedPatient.id,
        medicineId,
        dosage: dosage || 'As directed',
        frequency: frequency || 'As needed',
        duration: duration || '3 days',
      });
      addToast('Prescription added', 'success');
      fetchPrescriptions();
    } catch (err) {
      addToast(err.response?.data?.message || 'Error', 'critical');
    }
  };

  const fetchPrescriptions = async () => {
    if (!activeEncounter) return;
    try {
      const { data } = await encounterApi.listPrescriptions(activeEncounter.id);
      setPrescriptions(data || []);
    } catch {}
  };

  const signEncounter = async () => {
    if (!activeEncounter) return;
    try {
      const { data } = await encounterApi.sign(activeEncounter.id);
      setActiveEncounter(data);
      addToast('Encounter signed', 'success');
    } catch (err) {
      addToast(err.response?.data?.message || 'Error', 'critical');
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1>OPD / Encounters</h1>
        <p>Manage patient encounters, vitals, and prescriptions</p>
      </div>

      <div className="grid-2 encounter-layout" style={{ gridTemplateColumns: selectedPatient ? '320px 1fr' : '1fr' }}>
        <div className="card">
          <h3 style={{ marginBottom: 16, color: 'var(--text-white)' }}>Patients</h3>
          <input placeholder="Search patients..." style={{ marginBottom: 12 }}
            onChange={async (e) => {
              if (e.target.value.length > 2) {
                const { data } = await patientApi.list(e.target.value);
                setPatients(data);
              }
            }} />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4, maxHeight: 500, overflowY: 'auto' }}>
            {patients.map((p) => (
              <button key={p.id} className={`btn-ghost`}
                style={{
                  justifyContent: 'flex-start',
                  textAlign: 'left',
                  padding: '10px 14px',
                  background: selectedPatient?.id === p.id ? 'rgba(99,102,241,0.1)' : undefined,
                  border: selectedPatient?.id === p.id ? '1px solid rgba(99,102,241,0.3)' : undefined,
                }}
                onClick={() => startEncounter(p)}>
                <div style={{ fontWeight: 500, fontSize: '0.9rem' }}>{p.name}</div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>{p.uhid} • {p.gender}, {p.age}</div>
              </button>
            ))}
          </div>
        </div>

        {selectedPatient && (
          <div>
            {!activeEncounter ? (
              <div className="card">
                <h3 style={{ marginBottom: 16, color: 'var(--text-white)' }}>New Encounter: {selectedPatient.name}</h3>
                <div className="grid-2 vitals-grid" style={{ gridTemplateColumns: '1fr 1fr 1fr' }}>
                  {Object.entries({ bp: 'BP (mmHg)', pulse: 'Pulse (/min)', temp: 'Temp (°F)', spo2: 'SpO2 (%)', weight: 'Weight (kg)' }).map(([k, label]) => (
                    <div key={k} className="form-group">
                      <label>{label}</label>
                      <input value={vitals[k]} onChange={(e) => setVitals({...vitals, [k]: e.target.value})} />
                    </div>
                  ))}
                </div>
                <div className="form-group">
                  <label>Chief Complaint</label>
                  <textarea value={chiefComplaint} onChange={(e) => setChiefComplaint(e.target.value)} rows={2} />
                </div>
                <div className="form-group">
                  <label>Diagnosis</label>
                  <textarea value={diagnosis} onChange={(e) => setDiagnosis(e.target.value)} rows={2} />
                </div>
                <button className="btn-primary" onClick={createEncounter} style={{ width: '100%', justifyContent: 'center' }}>
                  Start Encounter →
                </button>
              </div>
            ) : (
              <div className="card">
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
                  <div>
                    <h3 style={{ color: 'var(--text-white)' }}>Active Encounter</h3>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>
                      Started: {new Date(activeEncounter.createdAt).toLocaleString()} • Status: <span className={`badge badge-${activeEncounter.status === 'open' ? 'warning' : 'success'}`}>{activeEncounter.status}</span>
                    </div>
                  </div>
                  {activeEncounter.status === 'open' && (
                    <button className="btn-success btn-sm" onClick={signEncounter}>Sign & Close</button>
                  )}
                </div>

                <div className="tabs">
                  {['Clinical', 'AI Assistant', 'Prescriptions'].map((t) => (
                    <button key={t} className="tab active">{t}</button>
                  ))}
                </div>

                <div style={{ marginBottom: 20 }}>
                  <h4 style={{ color: 'var(--text-white)', marginBottom: 8, fontSize: '0.95rem' }}>AI Medicine Advisor</h4>
                  <div style={{ display: 'flex', gap: 12 }} className="search-row">
                    <input placeholder="Describe condition for AI suggestions..." value={diagnosis}
                      onChange={(e) => setDiagnosis(e.target.value)} />
                    <button className="btn-primary btn-sm" onClick={fetchSuggestions} disabled={suggestLoading}>
                      {suggestLoading ? '...' : 'Suggest'}
                    </button>
                  </div>

                  {aiSuggestions.length > 0 && (
                    <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
                      {aiSuggestions.map((s) => (
                        <div key={s.medicineId} style={{
                          padding: '12px 16px',
                          border: '1px solid var(--border)',
                          borderRadius: 'var(--radius-sm)',
                          background: 'var(--surface)',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                        }} className="ai-suggestion-item">
                          <div>
                            <div style={{ fontWeight: 500, color: 'var(--text-white)', fontSize: '0.9rem' }}>{s.name}</div>
                            <div style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>
                              {s.genericName} • {s.dosage || ''} {s.frequency || ''} • ₹{s.unitPrice}
                            </div>
                            {s.rationale && <div style={{ fontSize: '0.75rem', color: 'var(--info)', marginTop: 2 }}>{s.rationale}</div>}
                          </div>
                          <button className="btn-primary btn-sm"
                            onClick={() => addPrescription(s.medicineId, s.dosage, s.frequency, '3 days')}>
                            + Prescribe
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <div>
                  <h4 style={{ color: 'var(--text-white)', marginBottom: 8, fontSize: '0.95rem' }}>Manual Prescription</h4>
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'flex-end' }} className="rx-manual-row">
                    <div style={{ flex: 2, minWidth: 180 }}>
                      <label>Medicine</label>
                      <select id="med-select">
                        <option value="">Select...</option>
                        {medicines.map((m) => (
                          <option key={m.id} value={m.id}>{m.name} - ₹{m.unitPrice}</option>
                        ))}
                      </select>
                    </div>
                    <div style={{ flex: 1, minWidth: 100 }}>
                      <label>Dosage</label>
                      <input id="dosage-input" placeholder="e.g. 500mg" />
                    </div>
                    <div style={{ flex: 1, minWidth: 100 }}>
                      <label>Frequency</label>
                      <input id="freq-input" placeholder="e.g. twice daily" />
                    </div>
                    <button className="btn-primary btn-sm" style={{ marginBottom: 6, height: 40 }}
                      onClick={() => {
                        const sel = document.getElementById('med-select');
                        const dose = document.getElementById('dosage-input');
                        const freq = document.getElementById('freq-input');
                        if (sel?.value) {
                          addPrescription(sel.value, dose?.value || 'As directed', freq?.value || 'As needed', '3 days');
                          sel.value = '';
                          if (dose) dose.value = '';
                          if (freq) freq.value = '';
                        }
                      }}>
                      + Add
                    </button>
                  </div>
                </div>

                {prescriptions.length > 0 && (
                  <div style={{ marginTop: 20 }}>
                    <h4 style={{ color: 'var(--text-white)', marginBottom: 8, fontSize: '0.95rem' }}>Current Prescriptions</h4>
                    <table>
                      <thead><tr><th>Medicine</th><th>Dosage</th><th>Frequency</th><th>Status</th></tr></thead>
                      <tbody>
                        {prescriptions.map((rx) => (
                          <tr key={rx.id}>
                            <td>{rx.medicineId}</td>
                            <td>{rx.dosage}</td>
                            <td>{rx.frequency}</td>
                            <td><span className={`badge badge-${rx.status === 'dispensed' ? 'success' : 'warning'}`}>{rx.status}</span></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
