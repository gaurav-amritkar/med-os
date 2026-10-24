import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import TokenQueue from './TokenQueue';
import { appointmentApi } from '../api';
import useAuthStore from '../store/authStore';

vi.mock('../api', () => ({
  appointmentApi: { queue: vi.fn(), summary: vi.fn(), updateStatus: vi.fn(), checkIn: vi.fn() },
}));

/**
 * The queue board per the approved design: it answers three things from one
 * screen — who is being served right now, who waits next, and (in words, where
 * the board's own logic lives) how a token came to exist. Tokens are issued at
 * check-in and reset each morning; the board says so instead of assuming the
 * desk remembers.
 */
const QUEUE = [
  { id: 'a1', tokenNo: 3, patientName: 'Aman Khan', doctorName: 'Dr. Aisha Sharma', apptRef: 'APT-1', status: 'checked_in', appointmentType: 'follow_up' },
  { id: 'a2', tokenNo: 1, patientName: 'Suresh Reddy', doctorName: 'Dr. Aisha Sharma', apptRef: 'APT-3', status: 'in_consultation', appointmentType: 'follow_up' },
  { id: 'a3', tokenNo: 4, patientName: 'Kavita Nair', doctorName: 'Dr. Rajesh Iyer', apptRef: 'APT-2', status: 'checked_in', appointmentType: 'first_visit' },
];

const META = { page: 0, size: 20, totalElements: 3, totalPages: 1, first: true, last: true };
const SUMMARY = {
  total: 4, scheduled: 1, checkedIn: 2, inConsultation: 1,
  completed: 0, cancelled: 0, noShow: 0,
};

function renderPage() {
  return render(
    <MemoryRouter>
      <TokenQueue />
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  useAuthStore.setState({ user: { username: 'admin' }, role: 'admin', token: 't', tenantId: 'ten-1' });
  appointmentApi.queue.mockResolvedValue({ data: QUEUE, meta: META });
  appointmentApi.summary.mockResolvedValue({ data: SUMMARY });
});

describe('Token Queue board', () => {
  it('says how tokens are issued, in the header where the queue lives', async () => {
    renderPage();
    await screen.findByText(/issued at check-in/i);
  });

  it('shows the patient in consultation as being served now', async () => {
    renderPage();
    const serving = await screen.findByText(/now serving/i);
    expect(serving).toBeInTheDocument();
    expect((await screen.findAllByText(/Suresh Reddy/)).length).toBeGreaterThan(0); // in the now-serving panel and the table row
  });

  it('keeps the waiting patients in token order below the now-serving panel', async () => {
    renderPage();
    const rows = await screen.findAllByRole('cell');
    const tokenCells = rows.filter((c) => /^Token \d+$/.test(c.textContent)).map((c) => Number(c.textContent.match(/\d+/)[0]));
    expect(tokenCells).toEqual([...tokenCells].sort((a, b) => a - b));
  });

  it('offers Start consultation to a doctor, not to the front desk', async () => {
    useAuthStore.setState({ role: 'receptionist' });
    renderPage();
    await screen.findByText('Now serving');
    expect(screen.queryByRole('button', { name: /^start$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^complete$/i })).not.toBeInTheDocument();
  });

  it('moves the queue forward from the board itself', async () => {
    const user = userEvent.setup();
    useAuthStore.setState({ role: 'doctor' });
    appointmentApi.updateStatus.mockResolvedValue({ data: { ...QUEUE[0], status: 'in_consultation' } });
    renderPage();
    const buttons = await screen.findAllByRole('button', { name: /^start$/i });
    await user.click(buttons[0]); // first waiting row in token order
    await waitFor(() => expect(appointmentApi.updateStatus).toHaveBeenCalledWith('a1', { status: 'in_consultation' }));
  });

  it('refreshes on demand — the board moves whether or not anyone is watching', async () => {
    const user = userEvent.setup();
    renderPage();
    screen.getByRole('button', { name: /refresh/i });
    appointmentApi.queue.mockResolvedValue({ data: [...QUEUE, { id: 'a5', tokenNo: 5, patientName: 'New Patient', doctorName: 'Dr. Aisha Sharma', apptRef: 'APT-5', status: 'checked_in', appointmentType: 'first_visit' }], meta: META });
    await user.click(screen.getByRole('button', { name: /refresh/i }));
    await screen.findByText('New Patient');
  });
});
