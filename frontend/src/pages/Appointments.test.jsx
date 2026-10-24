import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import Appointments from './Appointments';
import { appointmentApi, patientApi, userApi } from '../api';
import useToastStore from '../store/toastStore';
import useAuthStore from '../store/authStore';

vi.mock('../api', () => ({
  appointmentApi: {
    list: vi.fn(), summary: vi.fn(), book: vi.fn(),
    checkIn: vi.fn(), updateStatus: vi.fn(), delete: vi.fn(),
  },
  patientApi: { list: vi.fn() },
  userApi: { list: vi.fn() },
}));

/**
 * The appointments board per the approved design: booking lives where a
 * receptionist's day lives (a button on the board, not another page), the
 * day-token appears when the patient is checked in, and status moves are
 * offered to the roles the server allows — never to one it 403s.
 */
const APPTS = [
  {
    id: 'a1', appointmentNo: 1, apptRef: 'APT-1', tokenNo: 3,
    patientName: 'Aman Khan', patientUhid: 'UHID000010', patientAge: 22,
    doctorId: 'd1', doctorName: 'Dr. Aisha Sharma', doctorSpecialization: 'General Medicine',
    date: '2026-10-24', time: '14:21', appointmentDate: '2026-10-24T14:21',
    appointmentType: 'follow_up', status: 'checked_in', reason: 'Recurring headaches',
  },
  {
    id: 'a2', appointmentNo: 2, apptRef: 'APT-2', tokenNo: null,
    patientName: 'Kavita Nair', patientUhid: 'UHID000009', patientAge: 41,
    doctorId: 'd2', doctorName: 'Dr. Rajesh Iyer', doctorSpecialization: 'Orthopaedics',
    date: '2026-10-24', time: '15:00', appointmentDate: '2026-10-24T15:00',
    appointmentType: 'first_visit', status: 'scheduled', reason: 'Knee pain',
  },
  {
    id: 'a3', appointmentNo: 3, apptRef: 'APT-3', tokenNo: 1,
    patientName: 'Suresh Reddy', patientUhid: 'UHID000008', patientAge: 57,
    doctorId: 'd1', doctorName: 'Dr. Aisha Sharma', doctorSpecialization: 'General Medicine',
    date: '2026-10-24', time: '16:30', appointmentDate: '2026-10-24T16:30',
    appointmentType: 'follow_up', status: 'in_consultation', reason: 'BP follow-up',
  },
  {
    id: 'a4', appointmentNo: 4, apptRef: 'APT-4', tokenNo: 2,
    patientName: 'Meera Pillai', patientUhid: 'UHID000006', patientAge: 33,
    doctorId: 'd2', doctorName: 'Dr. Rajesh Iyer', doctorSpecialization: 'Orthopaedics',
    date: '2026-10-24', time: '11:00', appointmentDate: '2026-10-24T11:00',
    appointmentType: 'first_visit', status: 'completed', reason: 'Physio review',
  },
];

const META = { page: 0, size: 20, totalElements: 4, totalPages: 1, first: true, last: true };
const SUMMARY = {
  total: 4, scheduled: 1, checkedIn: 1, inConsultation: 1,
  completed: 1, cancelled: 0, noShow: 0,
};

const PATIENTS = {
  data: [
    { id: 'p1', uhid: 'UHID000010', name: 'Aman Khan', age: 22 },
    { id: 'p2', uhid: 'UHID000009', name: 'Kavita Nair', age: 41 },
  ],
};
const USERS = {
  data: [
    { id: 'd1', username: 'doctor', fullName: 'Dr. Aisha Sharma', role: 'doctor', active: true, specialization: 'General Medicine' },
    { id: 'd2', username: 'doctor2', fullName: 'Dr. Rajesh Iyer', role: 'doctor', active: true, specialization: 'Orthopaedics' },
    { id: 'n1', username: 'nurse', fullName: 'Priya Verma', role: 'nurse', active: true },
  ],
};

function asAdmin() {
  useAuthStore.setState({ user: { username: 'admin' }, role: 'admin', token: 't', tenantId: 'ten-1' });
}

function renderPage() {
  return render(
    <MemoryRouter>
      <Appointments />
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  useToastStore.setState({ toasts: [] });
  asAdmin();
  appointmentApi.list.mockResolvedValue({ data: APPTS, meta: META });
  appointmentApi.summary.mockResolvedValue({ data: SUMMARY });
  patientApi.list.mockResolvedValue(PATIENTS);
  userApi.list.mockResolvedValue(USERS);
});

describe('Appointments board', () => {
  beforeEach(() => {
    appointmentApi.list.mockResolvedValue({ data: APPTS, meta: META });
    appointmentApi.summary.mockResolvedValue({ data: SUMMARY });
  });

  it('offers a way to book from the board — the button the module shipped without', async () => {
    renderPage();
    expect(await screen.findByRole('button', { name: /book appointment/i })).toBeInTheDocument();
  });

  it('books through the modal: patient search, doctor, time, type, reason -> POST', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: /book appointment/i }));

    // Patient picker: search, then choose the matched patient.
    const search = await screen.findByLabelText(/patient/i);
    await user.type(search, 'Kavita');
    await user.click(await screen.findByRole('button', { name: /Kavita Nair/ }));

    await user.selectOptions(screen.getByLabelText(/doctor/i), 'd1');
    // jsdom implements no keystroke editing for datetime-local inputs; both
    // Playwright and browsers take the full value at once, so set it whole.
    fireEvent.change(screen.getByLabelText(/date and time/i), { target: { value: '2026-10-24T15:45' } });
    await user.click(screen.getByRole('radio', { name: /follow-up/i }));
    await user.type(screen.getByLabelText(/reason/i), 'Knee pain');

    await user.click(screen.getByRole('button', { name: /^Book$/i }));

    await waitFor(() => {
      expect(appointmentApi.book).toHaveBeenCalledWith({
        patientId: 'p2',
        doctorId: 'd1',
        appointmentDate: '2026-10-24T15:45',
        type: 'follow_up',
        reason: 'Knee pain',
      });
    });
  });

  it('refuses to submit a booking without a patient chosen', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: /book appointment/i }));

    await user.selectOptions(screen.getByLabelText(/doctor/i), 'd1');
    fireEvent.change(screen.getByLabelText(/date and time/i), { target: { value: '2026-10-24T15:45' } });
    await user.click(screen.getByRole('radio', { name: /follow-up/i }));
    await user.type(screen.getByLabelText(/reason/i), 'Knee pain');

    await user.click(screen.getByRole('button', { name: /^Book$/i }));

    expect(await screen.findByText(/choose a patient/i)).toBeInTheDocument();
    expect(appointmentApi.book).not.toHaveBeenCalled();
  });

  it('prints the day token the check-in issues, in favour of another silent call', async () => {
    const user = userEvent.setup();
    appointmentApi.checkIn.mockResolvedValue({ data: { ...APPTS[1], tokenNo: 4, status: 'checked_in' } });
    renderPage();
    await user.click(await screen.findByRole('button', { name: /check in/i }));

    await waitFor(() => {
      const toasted = useToastStore.getState().toasts.some(
        (t) => /token/i.test(t.message) && t.message.includes('4')
      );
      // "Token 4 issued to Kavita Nair" says what happened, not just that a call succeeded.
      expect(appointmentApi.checkIn).toHaveBeenCalledWith('a2');
      expect(toasted).toBe(true);
    });
  });

  it('shows the day token on the board once it exists, and an em dash before check-in', async () => {
    renderPage();
    const cells = await screen.findAllByRole('cell');
    expect(cells.some((c) => c.textContent === 'Token 3')).toBe(true);  // a1, checked in
    expect(cells.some((c) => c.textContent === '—')).toBe(true);        // a2, not checked in yet
  });

  it('moves a consultation forward only with the transitions the server allows, per role', async () => {
    const user = userEvent.setup();
    renderPage();

    // checked_in -> Start Consultation (doctor/admin only)
    await user.click(await screen.findByRole('button', { name: /^start$/i }));
    await waitFor(() => expect(appointmentApi.updateStatus).toHaveBeenCalledWith('a1', { status: 'in_consultation' }));

    // in_consultation -> Complete
    await user.click(await screen.findByRole('button', { name: /^complete$/i }));
    await waitFor(() => expect(appointmentApi.updateStatus).toHaveBeenCalledWith('a3', { status: 'completed' }));

    // scheduled -> Cancel
    await user.click(await screen.findByRole('button', { name: /^cancel$/i }));
    await waitFor(() => expect(appointmentApi.updateStatus).toHaveBeenCalledWith('a2', { status: 'cancelled' }));
  });

  it('deletes a closed appointment the admin asks to remove, and not with silent forgiveness', async () => {
    const user = userEvent.setup();
    window.confirm = vi.fn(() => true);
    appointmentApi.delete.mockResolvedValue({ data: null });
    renderPage();

    await user.click(await screen.findByRole('button', { name: /^delete$/i }));
    await waitFor(() => expect(appointmentApi.delete).toHaveBeenCalledWith('a4'));
  });

  it('does not offer a receptionist a status move the server would refuse', async () => {
    useAuthStore.setState({ role: 'receptionist' });
    renderPage();
    await screen.findByText('Kavita Nair · UHID000009'); // rows loaded
    expect(screen.queryByRole('button', { name: /^start$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^complete$/i })).not.toBeInTheDocument();
    // A scheduled row can still be checked in by the front desk:
    expect(screen.getByRole('button', { name: /check in/i })).toBeInTheDocument();
  });
});
