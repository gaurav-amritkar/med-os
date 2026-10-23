import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import Patients from './Patients';
import { patientApi } from '../api';

vi.mock('../api', () => ({
  patientApi: {
    list: vi.fn(),
    summary: vi.fn().mockResolvedValue({
      data: { total: 5, newToday: 2, activeOpd: 1, ipd: 3, discharged: 4 },
    }),
    register: vi.fn(),
  },
  encounterApi: {},
  billingApi: {},
  userApi: {},
}));

const PAGE = {
  data: [
    { id: 'p1', uhid: 'UHID000001', name: 'Anita Sharma', age: 28, gender: 'female',
      phone: '9876500001', bloodGroup: 'A+', dpdpConsent: true, outstanding: 0 },
    { id: 'p2', uhid: 'UHID000002', name: 'Suresh Reddy', age: 62, gender: 'male',
      phone: '9876500002', bloodGroup: 'B+', dpdpConsent: false, outstanding: 100 },
  ],
  meta: { page: 0, size: 20, totalElements: 2, totalPages: 1, first: true, last: true },
};

function renderPage() {
  return render(
    <MemoryRouter>
      <Patients />
    </MemoryRouter>
  );
}

/**
 * The Patients Hub (Wave 1, parity row 3): the counter rail answers Total /
 * New Today / Active OPD / IPD / Discharged at a glance, and the cohort +
 * blood-group filters reach the API verbatim.
 */
describe('Patients Hub', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    patientApi.summary.mockResolvedValue({
      data: { total: 5, newToday: 2, activeOpd: 1, ipd: 3, discharged: 4 },
    });
    patientApi.list.mockResolvedValue({ ...PAGE, data: PAGE.data.map((p) => ({ ...p })) });
    patientApi.register.mockResolvedValue({
      data: { ...PAGE.data[0], uhid: 'UHID000001' },
    });
  });

  it('renders the five-counter rail from the summary endpoint', async () => {
    renderPage();

    await waitFor(() => {
      const row = document.querySelector('.counter-row');
      expect(row?.textContent).toContain('New Today');
      expect(row?.textContent).toContain('Total Records');
      expect(row?.textContent).toContain('Active OPD');
      expect(row?.textContent).toContain('Inpatient (IPD)');
      expect(row?.textContent).toContain('Discharged');
      expect(row?.textContent).toContain('5');
      expect(row?.textContent).toContain('2');
      return true;
    });
  });

  it('rows render with UHID, serials and the View action', async () => {
    renderPage();

    await waitFor(() => expect(screen.getByText('Anita Sharma')).toBeInTheDocument());
    expect(screen.getByText('UHID000001')).toBeInTheDocument();
    expect(screen.getByText('28/F')).toBeInTheDocument();
    expect(screen.getByText('₹100')).toBeInTheDocument();
  });

  it('the IPD radio refetches with type=ipd', async () => {
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByText('Anita Sharma')).toBeInTheDocument());
    await user.click(screen.getByRole('radio', { name: 'Admitted (IPD)' }));

    await waitFor(() => {
      const params = patientApi.list.mock.calls.at(-1)?.[0] ?? {};
      expect(params.type).toBe('ipd');
    });
  });

  it('the blood-group select refetches with bloodGroup set', async () => {
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByText('Anita Sharma')).toBeInTheDocument());
    await user.selectOptions(screen.getByRole('combobox', { name: 'All blood groups' }), 'A+');

    await waitFor(() => {
      const params = patientApi.list.mock.calls.at(-1)?.[0] ?? {};
      expect(params.bloodGroup).toBe('A+');
    });
  });

  it('Clear resets both filters and refetches unfiltered', async () => {
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByText('Anita Sharma')).toBeInTheDocument());
    await user.click(screen.getByRole('radio', { name: 'Discharged' }));
    await waitFor(() => expect(patientApi.list.mock.calls.at(-1)?.[0].type).toBe('discharged'));

    await user.click(screen.getByRole('button', { name: 'Clear' }));

    await waitFor(() => {
      const params = patientApi.list.mock.calls.at(-1)?.[0] ?? {};
      expect(params.type).toBeUndefined();
      expect(params.bloodGroup).toBeUndefined();
    });
  });
});
