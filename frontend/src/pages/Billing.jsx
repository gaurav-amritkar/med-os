import { useState, useEffect } from 'react';
import { billingApi, patientApi } from '../api';
import Modal from '../components/Modal';
import useToastStore from '../store/toastStore';

function toCents(value) {
  return Math.round((Number(value) + Number.EPSILON) * 100);
}

function invoicePreview(charges, discountValue) {
  const taxableBases = new Map();
  charges.forEach((charge) => {
    const rate = Number(charge.gstPercent) || 0;
    taxableBases.set(rate, (taxableBases.get(rate) || 0) + toCents(charge.amount));
  });
  const groups = [...taxableBases.entries()]
    .map(([rate, baseCents]) => ({ rate, baseCents }))
    .sort((a, b) => a.rate - b.rate);
  const subtotalCents = groups.reduce((sum, group) => sum + group.baseCents, 0);
  const numericDiscount = Number(discountValue) || 0;
  const discountCents = toCents(numericDiscount);
  if (numericDiscount < 0 || discountCents > subtotalCents) {
    return { subtotalCents, totalCents: 0, valid: false };
  }
  if (discountCents === 0) {
    return {
      subtotalCents,
      totalCents: charges.reduce((sum, charge) => sum + toCents(charge.totalAmount), 0),
      valid: true,
    };
  }

  const denominator = BigInt(subtotalCents);
  let allocatedCents = 0;
  const shares = groups.map((group) => {
    const numerator = BigInt(discountCents) * BigInt(group.baseCents);
    const floorCents = Number(numerator / denominator);
    allocatedCents += floorCents;
    return { ...group, floorCents, remainder: numerator % denominator };
  });
  const remainingCents = discountCents - allocatedCents;
  shares.sort((a, b) => a.remainder === b.remainder
    ? a.rate - b.rate
    : a.remainder > b.remainder ? -1 : 1);
  const gstCents = shares.reduce((sum, share, index) => {
    const allocatedDiscount = share.floorCents + (index < remainingCents ? 1 : 0);
    const discountedBase = share.baseCents - allocatedDiscount;
    const rateCents = BigInt(toCents(share.rate));
    const taxNumerator = BigInt(discountedBase) * rateCents;
    return sum + Number((taxNumerator + 5000n) / 10000n);
  }, 0);

  return { subtotalCents, totalCents: subtotalCents - discountCents + gstCents, valid: true };
}

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
  const selectedChargeItems = unbilledCharges.filter((charge) => selectedCharges.includes(charge.id));
  const preview = invoicePreview(selectedChargeItems, invoiceDiscount);

  useEffect(() => {
    patientApi.list().then(({ data }) => setPatients(data)).catch(() => {});
  }, []);

  const selectPatient = async (patient) => {
    setSelectedPatient(patient);
    // The selection belongs to the patient it was made for. Carrying it across
    // posted this patient's id with the previous patient's charge ids, which
    // invoices the wrong person — reachable by ordinary mis-clicking, and the
    // happy path looks fine, so it survives manual testing.
    setSelectedCharges([]);
    setInvoiceDiscount(0);
    setShowInvoiceModal(false);
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
    if (!preview.valid) {
      addToast('Discount cannot exceed the selected charges subtotal', 'critical');
      return;
    }
    // Second guard: only charge ids that belong to the patient being billed. The
    // reset above should make this a no-op, but posting another patient's
    // charge id is the one mistake on this page that reaches the ledger.
    const payableIds = new Set(unbilledCharges.map((c) => c.id));
    const chargeIds = selectedCharges.filter((id) => payableIds.has(id));
    if (chargeIds.length === 0) {
      addToast('No charges selected for this patient', 'critical');
      return;
    }
    try {
      const { data } = await billingApi.createInvoice({
        patientId: selectedPatient.id,
        chargeIds,
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
            <h3 style={{ marginBottom: 16, color: 'var(--ink)' }}>Patients</h3>
            <input placeholder="Search..." style={{ marginBottom: 12 }}
              onChange={async (e) => {
                const term = e.target.value;
                // Refetch when the term drops under the minimum length too,
                // otherwise the previous result set stays on screen and no
                // longer matches what is typed.
                const { data } = await patientApi.list({ search: term.length > 2 ? term : undefined });
                setPatients(data);
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
                  <div style={{ fontSize: '0.75rem', color: 'var(--ink-faint)' }}>
                    Outstanding: <span style={{ color: (p.outstanding || 0) > 0 ? 'var(--critical)' : 'var(--normal)', fontWeight: 600 }}>
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
                    <h3 style={{ color: 'var(--ink)' }}>{selectedPatient.name}</h3>
                    <div style={{ fontSize: '0.85rem', color: 'var(--ink-faint)' }}>
                      {selectedPatient.uhid} • Outstanding: <strong style={{ color: 'var(--critical)' }}>₹{selectedPatient.outstanding || 0}</strong>
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
                  <h4 style={{ color: 'var(--ink)', fontSize: '0.95rem' }}>
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
                      <tr><td colSpan={showInvoiceModal ? 7 : 6} style={{ textAlign: 'center', color: 'var(--ink-faint)' }}>
                        No unbilled charges. Pharmacy dispenses and room charges are auto-posted here.
                      </td></tr>
                    )}
                  </tbody>
                </table>
              </div>

              <div className="card">
                <h3 style={{ color: 'var(--ink)', marginBottom: 16, fontSize: '0.95rem' }}>Invoices</h3>
                <table>
                  <thead><tr><th>Invoice #</th><th>Date</th><th>Amount</th><th>Paid</th><th>Balance</th><th>Status</th><th></th></tr></thead>
                  <tbody>
                    {invoices.map((inv) => (
                      <tr key={inv.id}>
                        <td style={{ fontFamily: 'monospace', color: 'var(--action)' }}>{inv.invoiceNumber}</td>
                        <td>{new Date(inv.invoiceDate).toLocaleDateString()}</td>
                        <td style={{ fontWeight: 600 }}>₹{inv.totalAmount}</td>
                        <td style={{ color: 'var(--normal)' }}>₹{inv.paidAmount}</td>
                        <td style={{ color: inv.totalAmount - inv.paidAmount > 0 ? 'var(--critical)' : 'var(--normal)' }}>
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
                    {invoices.length === 0 && <tr><td colSpan={7} style={{ textAlign: 'center', color: 'var(--ink-faint)' }}>No invoices yet</td></tr>}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>
      </div>

      {showInvoiceModal && (
        <Modal title="Generate Invoice" onClose={() => setShowInvoiceModal(false)}>
            <p style={{ marginBottom: 16, color: 'var(--ink-faint)', fontSize: '0.85rem' }}>
              Select charges for {selectedPatient?.name}, then generate a GST-compliant invoice.
            </p>
            <div role="group" aria-label="Charges to include" style={{ maxHeight: 260, overflowY: 'auto', marginBottom: 16 }}>
              {unbilledCharges.map((charge) => (
                <label key={charge.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 4px', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    aria-label={`Include ${charge.description}`}
                    checked={selectedCharges.includes(charge.id)}
                    onChange={() => toggleCharge(charge.id)}
                    style={{ width: 'auto' }}
                  />
                  <span style={{ flex: 1 }}>{charge.description}</span>
                  <span>₹{charge.totalAmount}</span>
                </label>
              ))}
              {unbilledCharges.length === 0 && <p>No unbilled charges available.</p>}
            </div>
            <div className="form-group">
              <label htmlFor="invoice-discount">Discount (₹)</label>
              <input id="invoice-discount" type="number" min={0} max={(preview.subtotalCents / 100).toFixed(2)} step="0.01" value={invoiceDiscount}
                onChange={(e) => setInvoiceDiscount(parseFloat(e.target.value) || 0)} />
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12, color: 'var(--ink-muted)', fontSize: '0.85rem' }}>
              <span>{selectedCharges.length} charges selected</span>
              <span>Total: ₹{(preview.totalCents / 100).toFixed(2)}</span>
            </div>
            {!preview.valid && <p role="alert" style={{ color: 'var(--critical)', marginBottom: 12 }}>Discount cannot exceed the selected charges subtotal of ₹{(preview.subtotalCents / 100).toFixed(2)}.</p>}
            {selectedCharges.length === 0 && <p style={{ color: 'var(--ink-faint)', marginBottom: 12 }}>Select at least one charge to generate an invoice.</p>}
            <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
              <button className="btn-ghost" onClick={() => { setSelectedCharges(unbilledCharges.map(c => c.id)); }}>
                Select all {unbilledCharges.length} charges
              </button>
              {selectedCharges.length > 0 && <button className="btn-ghost" onClick={() => setSelectedCharges([])}>Clear selection</button>}
              <button className="btn-primary" onClick={generateInvoice}
                disabled={selectedCharges.length === 0 || !preview.valid}>
                Generate Invoice
              </button>
            </div>
        </Modal>
      )}

      {showPaymentModal && (
        <Modal title="Record Payment" onClose={() => setShowPaymentModal(null)}>
            <div style={{ marginBottom: 16, fontSize: '0.85rem', color: 'var(--ink-faint)' }}>
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
        </Modal>
      )}
    </div>
  );
}
