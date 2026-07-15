import { useState, useEffect } from 'react';
import { billingApi, patientApi } from '../api';
import useToastStore from '../store/toastStore';

export default function Billing() {
  const [patients, setPatients] = useState([]);
  const [selectedPatient, setSelectedPatient] = useState(null);
  const [unbilledCharges, setUnbilledCharges] = useState([]);
  const [invoices, setInvoices] = useState([]);
  const [showInvoiceModal, setShowInvoiceModal] = useState(false);
  const [selectedCharges, setSelectedCharges] = useState([]);
  const [invoiceDiscount, setInvoiceDiscount] = useState(0);
  const [showPaymentModal, setShowPaymentModal] = useState(null);
  const [paymentForm, setPaymentForm] = useState({ amount: '', paymentMethod: 'cash', transactionRef: '' });
  const addToast = useToastStore((s) => s.addToast);

  useEffect(() => {
    patientApi.list().then(({ data }) => setPatients(data)).catch(() => {});
  }, []);

  const selectPatient = async (patient) => {
    setSelectedPatient(patient);
    try {
      const [unbilledRes, invRes] = await Promise.all([
        billingApi.getUnbilled(patient.id),
        billingApi.getInvoices(patient.id),
      ]);
      setUnbilledCharges(unbilledRes.data || []);
      setInvoices(invRes.data || []);
    } catch {}
  };

  const toggleCharge = (chargeId) => {
    setSelectedCharges((prev) =>
      prev.includes(chargeId) ? prev.filter((id) => id !== chargeId) : [...prev, chargeId]
    );
  };

  const generateInvoice = async () => {
    if (!selectedPatient || selectedCharges.length === 0) return;
    try {
      const { data } = await billingApi.createInvoice({
        patientId: selectedPatient.id,
        chargeIds: selectedCharges,
        discount: invoiceDiscount || 0,
      });
      addToast(`Invoice ${data.invoiceNumber} generated. Total: ₹${data.totalAmount}`, 'success');
      setShowInvoiceModal(false);
      setSelectedCharges([]);
      setInvoiceDiscount(0);
      selectPatient(selectedPatient);
    } catch (err) {
      addToast(err.response?.data?.message || 'Invoice generation failed', 'critical');
    }
  };

  const recordPayment = async (invoice) => {
    try {
      await billingApi.recordPayment({
        invoiceId: invoice.id,
        amount: parseFloat(paymentForm.amount),
        paymentMethod: paymentForm.paymentMethod,
        transactionRef: paymentForm.transactionRef || undefined,
      });
      addToast(`Payment of ₹${paymentForm.amount} recorded`, 'success');
      setShowPaymentModal(null);
      setPaymentForm({ amount: '', paymentMethod: 'cash', transactionRef: '' });
      selectPatient(selectedPatient);
    } catch (err) {
      addToast(err.response?.data?.message || 'Payment failed', 'critical');
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1>Billing & Invoicing</h1>
        <p>Charge management, GST invoices, and payment reconciliation</p>
      </div>

      <div style={{ display: 'flex', gap: 20 }} className="billing-layout">
        <div style={{ width: 300, flexShrink: 0 }}>
          <div className="card">
            <h3 style={{ marginBottom: 16, color: 'var(--text-white)' }}>Patients</h3>
            <input placeholder="Search..." style={{ marginBottom: 12 }}
              onChange={async (e) => {
                if (e.target.value.length > 2) {
                  const { data } = await patientApi.list(e.target.value);
                  setPatients(data);
                } else if (!e.target.value) {
                  const { data } = await patientApi.list();
                  setPatients(data);
                }
              }} />
            <div style={{ maxHeight: 500, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 4 }}>
              {patients.map((p) => (
                <button key={p.id} className="btn-ghost"
                  style={{
                    justifyContent: 'flex-start',
                    textAlign: 'left',
                    padding: '10px 14px',
                    background: selectedPatient?.id === p.id ? 'rgba(99,102,241,0.1)' : undefined,
                  }}
                  onClick={() => selectPatient(p)}>
                  <div style={{ fontWeight: 500, fontSize: '0.9rem' }}>{p.name}</div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                    Outstanding: <span style={{ color: (p.outstanding || 0) > 0 ? 'var(--danger)' : 'var(--success)', fontWeight: 600 }}>
                      ₹{p.outstanding || 0}
                    </span>
                  </div>
                </button>
              ))}
            </div>
          </div>
        </div>

        <div style={{ flex: 1 }}>
          {!selectedPatient ? (
            <div className="card" style={{ minHeight: 300, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <div className="empty-state"><p>Select a patient to view billing</p></div>
            </div>
          ) : (
            <>
              <div className="card" style={{ marginBottom: 20 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
                  <div>
                    <h3 style={{ color: 'var(--text-white)' }}>{selectedPatient.name}</h3>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-dim)' }}>
                      {selectedPatient.uhid} • Outstanding: <strong style={{ color: 'var(--danger)' }}>₹{selectedPatient.outstanding || 0}</strong>
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
                  <h4 style={{ color: 'var(--text-white)', fontSize: '0.95rem' }}>
                    Unbilled Charges ({unbilledCharges.length})
                  </h4>
                  {unbilledCharges.length > 0 && (
                    <button className="btn-primary btn-sm" onClick={() => setShowInvoiceModal(true)}>
                      Bill {selectedCharges.length > 0 ? `(${selectedCharges.length})` : 'All'}
                    </button>
                  )}
                </div>

                <table>
                  <thead><tr><th>{showInvoiceModal && '✓'}</th><th>Type</th><th>Description</th><th>Qty</th><th>Amount</th><th>GST</th><th>Total</th></tr></thead>
                  <tbody>
                    {unbilledCharges.map((c) => (
                      <tr key={c.id}
                        style={showInvoiceModal ? { cursor: 'pointer', background: selectedCharges.includes(c.id) ? 'rgba(99,102,241,0.05)' : undefined } : {}}
                        onClick={() => showInvoiceModal && toggleCharge(c.id)}>
                        {showInvoiceModal && (
                          <td><input type="checkbox" checked={selectedCharges.includes(c.id)}
                            onChange={() => toggleCharge(c.id)} style={{ width: 'auto' }} /></td>
                        )}
                        <td><span className="badge badge-info">{c.chargeType}</span></td>
                        <td style={{ fontSize: '0.85rem' }}>{c.description}</td>
                        <td>{c.quantity}</td>
                        <td>₹{c.amount}</td>
                        <td>{c.gstPercent}%</td>
                        <td style={{ fontWeight: 600 }}>₹{c.totalAmount}</td>
                      </tr>
                    ))}
                    {unbilledCharges.length === 0 && (
                      <tr><td colSpan={showInvoiceModal ? 7 : 6} style={{ textAlign: 'center', color: 'var(--text-dim)' }}>
                        No unbilled charges. Pharmacy dispenses and room charges are auto-posted here.
                      </td></tr>
                    )}
                  </tbody>
                </table>
              </div>

              <div className="card">
                <h3 style={{ color: 'var(--text-white)', marginBottom: 16, fontSize: '0.95rem' }}>Invoices</h3>
                <table>
                  <thead><tr><th>Invoice #</th><th>Date</th><th>Amount</th><th>Paid</th><th>Balance</th><th>Status</th><th></th></tr></thead>
                  <tbody>
                    {invoices.map((inv) => (
                      <tr key={inv.id}>
                        <td style={{ fontFamily: 'monospace', color: 'var(--primary)' }}>{inv.invoiceNumber}</td>
                        <td>{new Date(inv.invoiceDate).toLocaleDateString()}</td>
                        <td style={{ fontWeight: 600 }}>₹{inv.totalAmount}</td>
                        <td style={{ color: 'var(--success)' }}>₹{inv.paidAmount}</td>
                        <td style={{ color: inv.totalAmount - inv.paidAmount > 0 ? 'var(--danger)' : 'var(--success)' }}>
                          ₹{inv.totalAmount - inv.paidAmount}
                        </td>
                        <td><span className={`badge badge-${inv.status === 'paid' ? 'success' : inv.status === 'partially_paid' ? 'warning' : inv.status === 'issued' ? 'info' : 'default'}`}>{inv.status}</span></td>
                        <td>
                          {(inv.status === 'issued' || inv.status === 'partially_paid') && (
                            <button className="btn-success btn-sm"
                              onClick={() => {
                                setShowPaymentModal(inv);
                                setPaymentForm({ ...paymentForm, amount: (inv.totalAmount - inv.paidAmount).toString() });
                              }}>
                              Pay
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                    {invoices.length === 0 && <tr><td colSpan={7} style={{ textAlign: 'center', color: 'var(--text-dim)' }}>No invoices yet</td></tr>}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>
      </div>

      {showInvoiceModal && (
        <div className="modal-overlay" onClick={() => setShowInvoiceModal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Generate Invoice</h2>
              <button className="btn-ghost btn-sm" onClick={() => setShowInvoiceModal(false)}>✕</button>
            </div>
            <p style={{ marginBottom: 16, color: 'var(--text-dim)', fontSize: '0.85rem' }}>
              Select charges to bill, then generate a GST-compliant invoice.
            </p>
            <div className="form-group">
              <label>Discount (₹)</label>
              <input type="number" min={0} value={invoiceDiscount}
                onChange={(e) => setInvoiceDiscount(parseFloat(e.target.value) || 0)} />
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12, color: 'var(--text-muted)', fontSize: '0.85rem' }}>
              <span>{selectedCharges.length} charges selected</span>
              <span>
                Total: ₹{unbilledCharges.filter(c => selectedCharges.includes(c.id)).reduce((s, c) => s + parseFloat(c.totalAmount), 0)}
              </span>
            </div>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
              <button className="btn-ghost" onClick={() => { setSelectedCharges(unbilledCharges.map(c => c.id)); }}>
                Select All
              </button>
              <button className="btn-primary" onClick={generateInvoice}
                disabled={selectedCharges.length === 0}>
                Generate Invoice
              </button>
            </div>
          </div>
        </div>
      )}

      {showPaymentModal && (
        <div className="modal-overlay" onClick={() => setShowPaymentModal(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Record Payment</h2>
              <button className="btn-ghost btn-sm" onClick={() => setShowPaymentModal(null)}>✕</button>
            </div>
            <div style={{ marginBottom: 16, fontSize: '0.85rem', color: 'var(--text-dim)' }}>
              Invoice: {showPaymentModal.invoiceNumber}<br />
              Total: ₹{showPaymentModal.totalAmount} • Pending: ₹{showPaymentModal.totalAmount - showPaymentModal.paidAmount}
            </div>
            <div className="form-group">
              <label>Amount *</label>
              <input type="number" min={1} value={paymentForm.amount}
                onChange={(e) => setPaymentForm({...paymentForm, amount: e.target.value})} />
            </div>
            <div className="form-group">
              <label>Payment Method</label>
              <select value={paymentForm.paymentMethod}
                onChange={(e) => setPaymentForm({...paymentForm, paymentMethod: e.target.value})}>
                <option value="cash">Cash</option>
                <option value="card">Card</option>
                <option value="upi">UPI</option>
                <option value="netbanking">Net Banking</option>
                <option value="insurance">Insurance</option>
                <option value="cheque">Cheque</option>
              </select>
            </div>
            <div className="form-group">
              <label>Transaction Reference</label>
              <input value={paymentForm.transactionRef}
                onChange={(e) => setPaymentForm({...paymentForm, transactionRef: e.target.value})}
                placeholder="Optional ref/ID" />
            </div>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end', marginTop: 20 }}>
              <button className="btn-ghost" onClick={() => setShowPaymentModal(null)}>Cancel</button>
              <button className="btn-success" onClick={() => recordPayment(showPaymentModal)}
                disabled={!paymentForm.amount || parseFloat(paymentForm.amount) <= 0}>
                Record Payment
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
