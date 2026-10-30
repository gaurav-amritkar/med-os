import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import Billing from './Billing';
import { billingApi, patientApi } from '../api';
import useToastStore from '../store/toastStore';

vi.mock('../api', () => ({
  patientApi: { list: vi.fn() },
  billingApi: {
    getUnbilled: vi.fn(),
    getInvoices: vi.fn(),
    createInvoice: vi.fn(),
    recordPayment: vi.fn(),
  },
  userApi: {},
}));

/**
 * A charge selection belongs to the patient it was made for.
 *
 * <p>`selectedCharges` survived a patient switch, so selecting charges for one
 * patient and then choosing another posted the *new* patient id with the *old*
 * charge ids — invoicing the wrong patient. Reachable by ordinary mis-clicking,
 * and the happy path is fine, so it survives manual testing.
 */
const PATIENT_A = { id: 'patient-a', name: 'Asha Rao', uhid: 'UH-0001' };
const PATIENT_B = { id: 'patient-b', name: 'Bilal Khan', uhid: 'UH-0002' };

const CHARGES_A = [
  { id: 'charge-a1', chargeType: 'PHARMACY', description: 'Amoxicillin', quantity: 1, amount: 120, gstPercent: 12, totalAmount: 134.4 },
];
const CHARGES_B = [
  { id: 'charge-b1', chargeType: 'ROOM', description: 'Ward bed', quantity: 2, amount: 2000, gstPercent: 12, totalAmount: 4480 },
];

function renderPage() {
  return render(
    <MemoryRouter>
      <Billing />
    </MemoryRouter>
  );
}

async function pickPatient(user, name) {
  await user.click(await screen.findByText(name));
  await waitFor(() => expect(billingApi.getUnbilled).toHaveBeenCalled());
}

describe('Billing charge selection across a patient switch', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useToastStore.setState({ toasts: [] });
    patientApi.list.mockResolvedValue({ data: [PATIENT_A, PATIENT_B] });
    billingApi.getInvoices.mockResolvedValue({ data: [] });
    billingApi.getUnbilled.mockImplementation((patientId) =>
      Promise.resolve({ data: patientId === PATIENT_A.id ? CHARGES_A : CHARGES_B })
    );
    billingApi.createInvoice.mockResolvedValue({ data: { invoiceNumber: 'INV-1', totalAmount: 134.4 } });
  });

  it('drops the selection when the patient changes', async () => {
    const user = userEvent.setup();
    renderPage();

    await pickPatient(user, PATIENT_A.name);
    await user.click(screen.getByRole('button', { name: /^bill /i }));
    await user.click(screen.getByRole('button', { name: /select all/i }));
    expect(screen.getByText(/1 charges selected/i)).toBeInTheDocument();

    // Choose the other patient while the selection is still open.
    await pickPatient(user, PATIENT_B.name);

    await user.click(screen.getByRole('button', { name: /^bill /i }));
    await waitFor(() => expect(screen.getByText(/charges selected/i)).toBeInTheDocument());
    expect(screen.getByText(/0 charges selected/i)).toBeInTheDocument();
  });

  it('clears the invoice discount when the patient changes', async () => {
    const user = userEvent.setup();
    renderPage();
    await pickPatient(user, PATIENT_A.name);
    await user.click(screen.getByRole('button', { name: /^bill /i }));
    await user.clear(screen.getByRole('spinbutton'));
    await user.type(screen.getByRole('spinbutton'), '10');

    await pickPatient(user, PATIENT_B.name);
    await user.click(screen.getByRole('button', { name: /^bill /i }));

    expect(screen.getByRole('spinbutton')).toHaveValue(0);
  });

  it('never posts one patient id with another patient charge ids', async () => {
    const user = userEvent.setup();
    renderPage();

    await pickPatient(user, PATIENT_A.name);
    await user.click(screen.getByRole('button', { name: /^bill /i }));
    await user.click(screen.getByRole('button', { name: /select all/i }));

    // Switch patient with the selection still populated, then bill straight
    // away without re-selecting — the ordinary mis-click.
    await pickPatient(user, PATIENT_B.name);
    await user.click(screen.getByRole('button', { name: /^bill /i }));
    await user.click(screen.getByRole('button', { name: /^generate invoice$/i }));

    // The invariant is about what reaches the ledger, not about whether the
    // request is made: a correct fix may refuse outright, or may submit only
    // charges belonging to the patient. Never the other patient's ids.
    await waitFor(() => {
      if (billingApi.createInvoice.mock.calls.length > 0) {
        expect(billingApi.createInvoice.mock.calls[0][0].chargeIds).toBeDefined();
      }
    }).catch(() => {});

    const validIds = CHARGES_B.map((c) => c.id);
    billingApi.createInvoice.mock.calls.forEach(([payload]) => {
      (payload.chargeIds || []).forEach((id) => {
        expect(validIds, `posted charge ${id} does not belong to ${payload.patientId}`).toContain(id);
      });
    });
  });

  it('allows selecting an individual charge inside the invoice dialog', async () => {
    const user = userEvent.setup();
    renderPage();
    await pickPatient(user, PATIENT_A.name);
    await user.click(screen.getByRole('button', { name: /^bill /i }));
    expect(screen.getByText(/select charges for asha rao/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^generate invoice$/i })).toBeDisabled();
    expect(screen.getByText(/select at least one charge/i)).toBeInTheDocument();

    const chargeCheckbox = screen.getByRole('checkbox', { name: /amoxicillin/i });
    await user.click(chargeCheckbox);
    expect(chargeCheckbox).toBeChecked();
    expect(screen.getByText(/1 charges selected/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^generate invoice$/i })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: /^generate invoice$/i }));
    await waitFor(() => expect(billingApi.createInvoice).toHaveBeenCalledWith(
      expect.objectContaining({ patientId: PATIENT_A.id, chargeIds: ['charge-a1'] })
    ));
  });

  it('submits only the chosen subset of multiple charges', async () => {
    const secondCharge = {
      id: 'charge-a2', chargeType: 'ROOM', description: 'Consult room',
      quantity: 1, amount: 80, gstPercent: 12, totalAmount: 89.6,
    };
    billingApi.getUnbilled.mockResolvedValue({ data: [...CHARGES_A, secondCharge] });
    const user = userEvent.setup();
    renderPage();
    await pickPatient(user, PATIENT_A.name);
    await user.click(screen.getByRole('button', { name: /^bill /i }));
    await user.click(screen.getByRole('checkbox', { name: /amoxicillin/i }));
    await user.click(screen.getByRole('button', { name: /^generate invoice$/i }));

    await waitFor(() => expect(billingApi.createInvoice).toHaveBeenCalledWith(
      expect.objectContaining({ patientId: PATIENT_A.id, chargeIds: ['charge-a1'] })
    ));
  });

  it('previews a flat discount against the selected charges GST base', async () => {
    const user = userEvent.setup();
    renderPage();
    await pickPatient(user, PATIENT_A.name);
    await user.click(screen.getByRole('button', { name: /^bill /i }));
    await user.click(screen.getByRole('checkbox', { name: /amoxicillin/i }));

    const discountInput = screen.getByRole('spinbutton');
    await user.clear(discountInput);
    await user.type(discountInput, '10');

    expect(screen.getByText('Total: ₹123.20')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /^generate invoice$/i }));
    await waitFor(() => expect(billingApi.createInvoice).toHaveBeenCalledWith(
      expect.objectContaining({ discount: 10, chargeIds: ['charge-a1'] })
    ));
  });

  it('blocks a discount larger than the selected charges subtotal', async () => {
    const user = userEvent.setup();
    renderPage();
    await pickPatient(user, PATIENT_A.name);
    await user.click(screen.getByRole('button', { name: /^bill /i }));
    await user.click(screen.getByRole('checkbox', { name: /amoxicillin/i }));
    await user.clear(screen.getByRole('spinbutton'));
    await user.type(screen.getByRole('spinbutton'), '121');

    expect(screen.getByRole('alert')).toHaveTextContent(/cannot exceed/i);
    expect(screen.getByRole('button', { name: /^generate invoice$/i })).toBeDisabled();
  });

  it('allocates discount cents consistently across different GST rates', async () => {
    const mixedRateCharges = [
      { id: 'charge-5', chargeType: 'PHARMACY', description: 'Medicine', quantity: 1, amount: 1.01, gstPercent: 5, totalAmount: 1.06 },
      { id: 'charge-12', chargeType: 'ROOM', description: 'Room', quantity: 1, amount: 2.01, gstPercent: 12, totalAmount: 2.25 },
      { id: 'charge-18', chargeType: 'LAB', description: 'Lab', quantity: 1, amount: 3.01, gstPercent: 18, totalAmount: 3.55 },
    ];
    billingApi.getUnbilled.mockResolvedValue({ data: mixedRateCharges });
    const user = userEvent.setup();
    renderPage();
    await pickPatient(user, PATIENT_A.name);
    await user.click(screen.getByRole('button', { name: /^bill /i }));
    await user.click(screen.getByRole('button', { name: /select all 3 charges/i }));
    await user.clear(screen.getByRole('spinbutton'));
    await user.type(screen.getByRole('spinbutton'), '1.00');

    expect(screen.getByText('Total: ₹5.72')).toBeInTheDocument();
  });

  it('handles patients with no unbilled charges without opening a dead-end invoice dialog', async () => {
    billingApi.getUnbilled.mockResolvedValue({ data: [] });
    const user = userEvent.setup();
    renderPage();
    await pickPatient(user, PATIENT_A.name);

    expect(screen.getByText(/no unbilled charges/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^bill /i })).not.toBeInTheDocument();
  });

  it('makes select all and clear selection scope explicit', async () => {
    const user = userEvent.setup();
    renderPage();
    await pickPatient(user, PATIENT_A.name);
    await user.click(screen.getByRole('button', { name: /^bill /i }));
    await user.click(screen.getByRole('button', { name: /select all 1 charges/i }));

    expect(screen.getByRole('checkbox', { name: /amoxicillin/i })).toBeChecked();
    await user.click(screen.getByRole('button', { name: /clear selection/i }));
    expect(screen.getByText(/0 charges selected/i)).toBeInTheDocument();
  });
});
