import { useState, useEffect } from 'react';
import { pharmacyApi, encounterApi } from '../api';
import useToastStore from '../store/toastStore';

export default function Pharmacy() {
  const [medicines, setMedicines] = useState([]);
  const [pendingRx, setPendingRx] = useState([]);
  const [selectedMed, setSelectedMed] = useState(null);
  const [batches, setBatches] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [tab, setTab] = useState('inventory');
  const [showStockIn, setShowStockIn] = useState(false);
  const [stockForm, setStockForm] = useState({ batchNo: '', expiryDate: '', quantity: 10, purchasePrice: '', supplier: '' });
  const addToast = useToastStore((s) => s.addToast);

  const [dispenseRx, setDispenseRx] = useState(null);
  const [dispenseQty, setDispenseQty] = useState(1);

  useEffect(() => {
    pharmacyApi.listMedicines().then(({ data }) => setMedicines(data)).catch(() => {});
    encounterApi.pendingPrescriptions().then(({ data }) => setPendingRx(data || [])).catch(() => {});
  }, []);

  const selectMedicine = async (med) => {
    setSelectedMed(med);
    setTab('batches');
    const [batchesRes, txnRes] = await Promise.all([
      pharmacyApi.getBatches(med.id),
      pharmacyApi.getTransactions(med.id),
    ]);
    setBatches(batchesRes.data || []);
    setTransactions(txnRes.data || []);
  };

  const handleStockIn = async (e) => {
    e.preventDefault();
    if (!selectedMed) return;
    try {
      await pharmacyApi.addStock(selectedMed.id, {
        batchNo: stockForm.batchNo,
        expiryDate: stockForm.expiryDate,
        quantity: parseInt(stockForm.quantity),
        purchasePrice: stockForm.purchasePrice || undefined,
        supplier: stockForm.supplier || undefined,
      });
      addToast(`Stock added to ${selectedMed.name}`, 'success');
      setShowStockIn(false);
      setStockForm({ batchNo: '', expiryDate: '', quantity: 10, purchasePrice: '', supplier: '' });
      selectMedicine(selectedMed);
    } catch (err) {
      addToast(err.response?.data?.message || 'Failed', 'critical');
    }
  };

  const handleDispense = async () => {
    if (!dispenseRx) return;
    try {
      await pharmacyApi.dispense({
        patientId: dispenseRx.patientId,
        prescriptionId: dispenseRx.id,
        quantity: dispenseQty,
      });
      addToast('Dispensed successfully', 'success');
      setDispenseRx(null);
      setDispenseQty(1);
      const { data } = await encounterApi.pendingPrescriptions();
      setPendingRx(data || []);
    } catch (err) {
      addToast(err.response?.data?.message || 'Dispense failed', 'critical');
    }
  };

  const isExpiringSoon = (date) => {
    const d = new Date(date);
    const in30 = new Date(); in30.setDate(in30.getDate() + 30);
    return d <= in30;
  };

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1>Pharmacy Vault</h1>
          <p>Inventory, FEFO dispensing, and stock ledger</p>
        </div>
        <div style={{ display: 'flex', gap: 8 }} className="badges-row">
          <span className={`badge badge-warning`}>{pendingRx.length} pending</span>
        </div>
      </div>

      {dispenseRx && (
        <div className="modal-overlay" onClick={() => setDispenseRx(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Dispense Prescription</h2>
              <button className="btn-ghost btn-sm" onClick={() => setDispenseRx(null)}>✕</button>
            </div>
            <div className="form-group">
              <label>Prescription ID</label>
              <input value={dispenseRx.id} disabled />
            </div>
            <div className="form-group">
              <label>Quantity to Dispense</label>
              <input type="number" min={1} value={dispenseQty} onChange={(e) => setDispenseQty(parseInt(e.target.value) || 1)} />
            </div>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-dim)', marginBottom: 16 }}>
              System will auto-deduct using FEFO (First-Expired-First-Out) and post charges to patient ledger.
            </p>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
              <button className="btn-ghost" onClick={() => setDispenseRx(null)}>Cancel</button>
              <button className="btn-primary" onClick={handleDispense}>Dispense FEFO</button>
            </div>
          </div>
        </div>
      )}

      <div style={{ display: 'flex', gap: 20 }} className="pharmacy-layout">
        <div style={{ width: 340, flexShrink: 0 }}>
          <div className="card" style={{ marginBottom: 20 }}>
            <div className="card-header">
              <h3>Pending Rx</h3>
            </div>
            {pendingRx.length === 0 ? (
              <div style={{ padding: 20, textAlign: 'center', color: 'var(--text-dim)', fontSize: '0.85rem' }}>No pending prescriptions</div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                {pendingRx.map((rx) => (
                  <div key={rx.id} style={{
                    padding: '10px 12px',
                    border: '1px solid var(--border)',
                    borderRadius: 'var(--radius-sm)',
                    fontSize: '0.85rem',
                  }}>
                    <div style={{ fontWeight: 500, color: 'var(--text-white)' }}>Rx: {rx.medicineId?.slice(0, 8)}</div>
                    <div style={{ color: 'var(--text-dim)', fontSize: '0.8rem' }}>{rx.dosage} {rx.frequency}</div>
                    <button className="btn-success btn-sm" style={{ marginTop: 6, width: '100%', justifyContent: 'center' }}
                      onClick={() => { setDispenseRx(rx); setDispenseQty(1); }}>
                      Dispense
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="card">
            <div className="card-header">
              <h3>Medicine Catalog</h3>
            </div>
            <input placeholder="Search medicines..." style={{ marginBottom: 12 }}
              onChange={async (e) => {
                if (e.target.value.length > 2) {
                  const { data } = await pharmacyApi.listMedicines();
                  setMedicines(data.filter(m => m.name.toLowerCase().includes(e.target.value.toLowerCase())));
                } else if (!e.target.value) {
                  const { data } = await pharmacyApi.listMedicines();
                  setMedicines(data);
                }
              }} />
            <div style={{ maxHeight: 400, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 2 }}>
              {medicines.map((m) => (
                <button key={m.id} className="btn-ghost"
                  style={{
                    justifyContent: 'flex-start',
                    textAlign: 'left',
                    padding: '8px 12px',
                    fontSize: '0.85rem',
                    background: selectedMed?.id === m.id ? 'rgba(99,102,241,0.1)' : undefined,
                  }}
                  onClick={() => selectMedicine(m)}>
                  {m.name}
                  <span style={{ marginLeft: 'auto', color: 'var(--text-dim)', fontSize: '0.75rem' }}>₹{m.unitPrice}</span>
                </button>
              ))}
            </div>
          </div>
        </div>

        <div style={{ flex: 1 }}>
          {!selectedMed ? (
            <div className="card" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 300 }}>
              <div className="empty-state">
                <p>Select a medicine to view inventory details</p>
              </div>
            </div>
          ) : (
            <div className="card">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
                <div>
                  <h3 style={{ color: 'var(--text-white)', fontSize: '1.1rem' }}>{selectedMed.name}</h3>
                  <div style={{ fontSize: '0.85rem', color: 'var(--text-dim)' }}>
                    {selectedMed.genericName} • {selectedMed.manufacturer} • {selectedMed.category} • ₹{selectedMed.unitPrice}/{selectedMed.unit}
                  </div>
                </div>
                <button className="btn-primary btn-sm" onClick={() => setShowStockIn(true)}>+ Stock In</button>
              </div>

              <div className="tabs">
                {['batches', 'ledger'].map((t) => (
                  <button key={t} className={`tab ${tab === t ? 'active' : ''}`} onClick={() => setTab(t)}>
                    {t.charAt(0).toUpperCase() + t.slice(1)}
                  </button>
                ))}
              </div>

              {tab === 'batches' && (
                <table>
                  <thead><tr><th>Batch</th><th>Expiry</th><th>Remaining</th><th>Supplier</th></tr></thead>
                  <tbody>
                    {batches.map((b) => (
                      <tr key={b.id}>
                        <td style={{ fontFamily: 'monospace' }}>{b.batchNo}</td>
                        <td style={isExpiringSoon(b.expiryDate) ? { color: 'var(--danger)' } : {}}>
                          {b.expiryDate}
                          {isExpiringSoon(b.expiryDate) && <span className="badge badge-danger" style={{ marginLeft: 8 }}>Expiring</span>}
                        </td>
                        <td style={{ fontWeight: 600 }}>{b.remainingQty}</td>
                        <td>{b.supplier || '-'}</td>
                      </tr>
                    ))}
                    {batches.length === 0 && <tr><td colSpan={4} style={{ textAlign: 'center', color: 'var(--text-dim)' }}>No batches. Add stock.</td></tr>}
                  </tbody>
                </table>
              )}

              {tab === 'ledger' && (
                <table>
                  <thead><tr><th>Date</th><th>Type</th><th>Qty</th><th>Ref</th></tr></thead>
                  <tbody>
                    {transactions.map((t) => (
                      <tr key={t.id}>
                        <td>{new Date(t.performedAt).toLocaleDateString()}</td>
                        <td><span className={`badge badge-${t.transactionType === 'in' ? 'success' : t.transactionType === 'out' ? 'danger' : 'warning'}`}>{t.transactionType}</span></td>
                        <td style={{ color: t.quantity < 0 ? 'var(--danger)' : 'var(--success)', fontWeight: 600 }}>{t.quantity}</td>
                        <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{t.referenceNo || '-'}</td>
                      </tr>
                    ))}
                    {transactions.length === 0 && <tr><td colSpan={4} style={{ textAlign: 'center', color: 'var(--text-dim)' }}>No transactions</td></tr>}
                  </tbody>
                </table>
              )}

              {showStockIn && (
                <div className="modal-overlay" onClick={() => setShowStockIn(false)}>
                  <div className="modal" onClick={(e) => e.stopPropagation()}>
                    <div className="modal-header">
                      <h2>Add Stock: {selectedMed.name}</h2>
                      <button className="btn-ghost btn-sm" onClick={() => setShowStockIn(false)}>✕</button>
                    </div>
                    <form onSubmit={handleStockIn}>
                      <div className="form-group">
                        <label>Batch Number *</label>
                        <input value={stockForm.batchNo} onChange={(e) => setStockForm({...stockForm, batchNo: e.target.value})} required />
                      </div>
                      <div className="form-row">
                        <div className="form-group">
                          <label>Expiry Date *</label>
                          <input type="date" value={stockForm.expiryDate} onChange={(e) => setStockForm({...stockForm, expiryDate: e.target.value})} required />
                        </div>
                        <div className="form-group">
                          <label>Quantity *</label>
                          <input type="number" min={1} value={stockForm.quantity} onChange={(e) => setStockForm({...stockForm, quantity: e.target.value})} required />
                        </div>
                      </div>
                      <div className="form-row">
                        <div className="form-group">
                          <label>Purchase Price</label>
                          <input type="number" step="0.01" value={stockForm.purchasePrice} onChange={(e) => setStockForm({...stockForm, purchasePrice: e.target.value})} />
                        </div>
                        <div className="form-group">
                          <label>Supplier</label>
                          <input value={stockForm.supplier} onChange={(e) => setStockForm({...stockForm, supplier: e.target.value})} />
                        </div>
                      </div>
                      <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end', marginTop: 20 }}>
                        <button type="button" className="btn-ghost" onClick={() => setShowStockIn(false)}>Cancel</button>
                        <button type="submit" className="btn-primary">Add Stock</button>
                      </div>
                    </form>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
