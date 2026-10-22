import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import Pharmacy from './Pharmacy';
import { pharmacyApi } from '../api';
import useToastStore from '../store/toastStore';

vi.mock('../api', () => ({
  pharmacyApi: {
    listMedicines: vi.fn(),
    getBatches: vi.fn(),
    addStock: vi.fn(),
    addMedicine: vi.fn(),
    dispense: vi.fn(),
    getTransactions: vi.fn(),
    getPendingPrescriptions: vi.fn(),
  },
  encounterApi: { pendingPrescriptions: vi.fn().mockResolvedValue({ data: [] }) },
  patientApi: {},
}));

/**
 * A pharmacist with an empty catalogue has nowhere to go.
 *
 * <p>Stock-in is reachable only after selecting a medicine. With no medicines
 * there is nothing to select, and the catalogue card had no way to create one —
 * the backend has had POST /pharmacy/medicines all along, but nothing in the UI
 * called it. The page rendered an empty box and a right-hand pane reading
 * "Select a medicine to view inventory details", which is an instruction to do
 * something impossible.
 *
 * <p>That is the whole of "adding medicine stock is not working": the feature
 * works, the entry point does not exist.
 */
function renderPage() {
  return render(
    <MemoryRouter>
      <Pharmacy />
    </MemoryRouter>
  );
}

describe('Pharmacy — empty medicine catalogue', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useToastStore.setState({ toasts: [] });
    pharmacyApi.listMedicines.mockResolvedValue({ data: [] });
    pharmacyApi.getPendingPrescriptions.mockResolvedValue({ data: [] });
  });

  it('says the catalogue is empty rather than showing a blank box', async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/no medicines/i)).toBeInTheDocument();
    });
  });

  it('offers a way to add the first medicine', async () => {
    renderPage();

    // The control that was missing entirely.
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /add medicine/i })).toBeInTheDocument();
    });
  });

  it('does not tell the user to select a medicine when there are none', async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/no medicines/i)).toBeInTheDocument();
    });
    expect(screen.queryByText(/select a medicine to view/i)).not.toBeInTheDocument();
  });

  it('creates a medicine and shows it in the catalogue', async () => {
    const user = userEvent.setup();
    pharmacyApi.addMedicine.mockResolvedValue({
      data: { id: 'm-1', name: 'Amoxicillin 500mg', unitPrice: 45.5, active: true },
    });
    pharmacyApi.getBatches.mockResolvedValue({ data: [] });
    pharmacyApi.getTransactions.mockResolvedValue({ data: [] });
    renderPage();

    await user.click(await screen.findByRole('button', { name: /add medicine/i }));
    await user.type(screen.getByLabelText(/^name\s*\*?$/i), 'Amoxicillin 500mg');
    await user.type(screen.getByLabelText(/^unit price\s*\*?$/i), '45.5');
    await user.click(screen.getByRole('button', { name: /^add medicine$/i }));

    await waitFor(() => {
      expect(pharmacyApi.addMedicine).toHaveBeenCalled();
    });
    // The new medicine must reach the list, or the user has to guess whether
    // it worked.
    await waitFor(() => {
      expect(screen.getAllByText('Amoxicillin 500mg')[0]).toBeInTheDocument();
    });
  });

  it('reports a failed create instead of closing silently', async () => {
    const user = userEvent.setup();
    pharmacyApi.addMedicine.mockRejectedValue({
      response: { data: { message: 'That medicine already exists' } },
    });
    renderPage();

    await user.click(await screen.findByRole('button', { name: /add medicine/i }));
    await user.type(screen.getByLabelText(/^name\s*\*?$/i), 'Duplicate');
    await user.type(screen.getByLabelText(/^unit price\s*\*?$/i), '10');
    await user.click(screen.getByRole('button', { name: /^add medicine$/i }));

    await waitFor(() => {
      expect(useToastStore.getState().toasts.length).toBeGreaterThan(0);
    });
  });
});