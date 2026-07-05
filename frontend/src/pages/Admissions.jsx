import { useState, useEffect } from 'react';
import { admissionApi, patientApi } from '../api';
import useToastStore from '../store/toastStore';

export default function Admissions() {
  const [rooms, setRooms] = useState([]);
  const [activeAdmissions, setActiveAdmissions] = useState([]);
  const [patients, setPatients] = useState([]);
  const [showAdmit, setShowAdmit] = useState(false);
  const [selectedRoom, setSelectedRoom] = useState(null);
  const [admitForm, setAdmitForm] = useState({ patientId: '', roomId: '' });
  const [discharging, setDischarging] = useState(null);
  const [dischargeDiagnosis, setDischargeDiagnosis] = useState('');
  const addToast = useToastStore((s) => s.addToast);

  useEffect(() => {
    Promise.all([
      admissionApi.rooms(),
      admissionApi.active(),
      patientApi.list(),
    ]).then(([roomsRes, admRes, patRes]) => {
      setRooms(roomsRes.data || []);
      setActiveAdmissions(admRes.data || []);
      setPatients(patRes.data || []);
    });
  }, []);

  const openAdmit = (room) => {
    setSelectedRoom(room);
    setAdmitForm({ patientId: '', roomId: room.id });
    setShowAdmit(true);
  };

  const handleAdmit = async (e) => {
    e.preventDefault();
    try {
      await admissionApi.admit({
        patientId: admitForm.patientId,
        roomId: admitForm.roomId,
      });
      addToast('Patient admitted', 'success');
      setShowAdmit(false);
      refresh();
    } catch (err) {
      addToast(err.response?.data?.message || 'Admission failed', 'critical');
    }
  };

  const handleDischarge = async () => {
    if (!discharging) return;
    try {
      await admissionApi.discharge(discharging.id, { dischargeDiagnosis });
      addToast('Patient discharged', 'success');
      setDischarging(null);
      setDischargeDiagnosis('');
      refresh();
    } catch (err) {
      addToast(err.response?.data?.message || 'Discharge failed', 'critical');
    }
  };

  const refresh = async () => {
    const [roomsRes, admRes] = await Promise.all([
      admissionApi.rooms(),
      admissionApi.active(),
    ]);
    setRooms(roomsRes.data || []);
    setActiveAdmissions(admRes.data || []);
  };

  const getAdmissionPatient = (roomId) => activeAdmissions.find((a) => a.roomId === roomId);
  const getPatientName = (patId) => patients.find((p) => p.id === patId)?.name || patId?.slice(0, 8);

  const roomColor = (type) => {
    const colors = {
      general: { bg: 'rgba(59,130,246,0.1)', border: 'rgba(59,130,246,0.3)' },
      semi_private: { bg: 'rgba(16,185,129,0.1)', border: 'rgba(16,185,129,0.3)' },
      private: { bg: 'rgba(245,158,11,0.1)', border: 'rgba(245,158,11,0.3)' },
      icu: { bg: 'rgba(239,68,68,0.1)', border: 'rgba(239,68,68,0.3)' },
      nicu: { bg: 'rgba(168,85,247,0.1)', border: 'rgba(168,85,247,0.3)' },
      operation: { bg: 'rgba(236,72,153,0.1)', border: 'rgba(236,72,153,0.3)' },
    };
    return colors[type] || { bg: 'rgba(255,255,255,0.05)', border: 'var(--border)' };
  };

  return (
    <div>
      <div className="page-header">
        <h1>IPD / Wards</h1>
        <p>Bed allocation, occupancy map, and discharge management</p>
      </div>

      <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
        <div className="stat-card" style={{ flex: 1 }}>
          <div className="stat-label">Total Beds</div>
          <div className="stat-value">{rooms.length}</div>
        </div>
        <div className="stat-card" style={{ flex: 1 }}>
          <div className="stat-label">Occupied</div>
          <div className="stat-value" style={{ color: 'var(--danger)' }}>{rooms.filter(r => r.occupied).length}</div>
        </div>
        <div className="stat-card" style={{ flex: 1 }}>
          <div className="stat-label">Available</div>
          <div className="stat-value" style={{ color: 'var(--success)' }}>{rooms.filter(r => !r.occupied).length}</div>
        </div>
        <div className="stat-card" style={{ flex: 1 }}>
          <div className="stat-label">Active IPD</div>
          <div className="stat-value" style={{ color: 'var(--info)' }}>{activeAdmissions.length}</div>
        </div>
      </div>

      <div className="card">
        <h3 style={{ color: 'var(--text-white)', marginBottom: 16 }}>Room Occupancy Map</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: 12 }}>
          {rooms.map((room) => {
            const admission = getAdmissionPatient(room.id);
            const colors = roomColor(room.roomType);
            return (
              <div key={room.id} style={{
                padding: 16,
                borderRadius: 'var(--radius-sm)',
                border: `1px solid ${room.occupied ? colors.border : 'var(--border)'}`,
                background: room.occupied ? colors.bg : 'var(--surface)',
                cursor: room.occupied ? 'default' : 'pointer',
                transition: 'all 0.2s',
              }}
              onClick={() => !room.occupied && openAdmit(room)}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                  <span style={{ fontWeight: 600, color: 'var(--text-white)', fontSize: '0.9rem' }}>{room.roomNumber}</span>
                  <span className={`badge ${room.occupied ? 'badge-danger' : 'badge-success'}`}>
                    {room.occupied ? 'Occupied' : 'Available'}
                  </span>
                </div>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>
                  {room.ward} • {room.roomType.replace('_', ' ')}
                </div>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>
                  ₹{room.dailyRate}/day
                </div>
                {admission && (
                  <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid var(--border)' }}>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-white)', fontWeight: 500 }}>
                      {getPatientName(admission.patientId)}
                    </div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                      Since: {new Date(admission.admissionDate).toLocaleDateString()}
                    </div>
                    <button className="btn-ghost btn-sm" style={{ marginTop: 6, width: '100%', justifyContent: 'center', fontSize: '0.75rem' }}
                      onClick={(e) => { e.stopPropagation(); setDischarging(admission); }}>
                      Discharge
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {showAdmit && selectedRoom && (
        <div className="modal-overlay" onClick={() => setShowAdmit(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Admit Patient: {selectedRoom.roomNumber}</h2>
              <button className="btn-ghost btn-sm" onClick={() => setShowAdmit(false)}>✕</button>
            </div>
            <form onSubmit={handleAdmit}>
              <div className="form-group">
                <label>Room</label>
                <input value={`${selectedRoom.roomNumber} - ${selectedRoom.roomType} (₹${selectedRoom.dailyRate}/day)`} disabled />
              </div>
              <div className="form-group">
                <label>Select Patient *</label>
                <select value={admitForm.patientId} onChange={(e) => setAdmitForm({...admitForm, patientId: e.target.value})} required>
                  <option value="">Select patient...</option>
                  {patients.filter(p => !activeAdmissions.find(a => a.patientId === p.id)).map((p) => (
                    <option key={p.id} value={p.id}>{p.name} ({p.uhid})</option>
                  ))}
                </select>
              </div>
              <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end', marginTop: 20 }}>
                <button type="button" className="btn-ghost" onClick={() => setShowAdmit(false)}>Cancel</button>
                <button type="submit" className="btn-primary">Admit Patient</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {discharging && (
        <div className="modal-overlay" onClick={() => setDischarging(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Discharge Patient</h2>
              <button className="btn-ghost btn-sm" onClick={() => setDischarging(null)}>✕</button>
            </div>
            <div style={{ marginBottom: 16 }}>
              <div style={{ color: 'var(--text-white)', fontWeight: 500 }}>Patient: {getPatientName(discharging.patientId)}</div>
              <div style={{ color: 'var(--text-dim)', fontSize: '0.85rem' }}>
                Room: {rooms.find(r => r.id === discharging.roomId)?.roomNumber}
              </div>
              <div style={{ color: 'var(--text-dim)', fontSize: '0.85rem' }}>
                Admitted: {new Date(discharging.admissionDate).toLocaleDateString()}
              </div>
            </div>
            <div className="form-group">
              <label>Discharge Diagnosis *</label>
              <textarea value={dischargeDiagnosis} onChange={(e) => setDischargeDiagnosis(e.target.value)}
                rows={3} placeholder="Enter discharge diagnosis" required />
            </div>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-dim)', marginBottom: 16 }}>
              Room charges will be auto-calculated and posted to patient billing ledger.
            </p>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
              <button className="btn-ghost" onClick={() => setDischarging(null)}>Cancel</button>
              <button className="btn-primary" onClick={handleDischarge}
                disabled={!dischargeDiagnosis.trim()}>Process Discharge</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
