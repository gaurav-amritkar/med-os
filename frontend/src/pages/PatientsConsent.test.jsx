import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import Patients from './Patients';
import { patientApi, encounterApi, billingApi } from '../api';
import useAuthStore from '../store/authStore';

vi.mock('../api', () => ({
  patientApi: {
    list: vi.fn(),
    summary: vi.fn(),
    register: vi.fn(),
    get: vi.fn(),
    recordConsent: vi.fn(),
    withdrawConsent: vi.fn(),
  },
  encounterApi: { listByPatient: vi.fn() },
  billingApi: { getInvoices: vi.fn() },
  userApi: {},
}));

const WITHOUT_CONSENT = {
  id: 'p1', uhid: 'UHID000001', name: 'Anita Sharma', age: 28, gender: 'female',
  phone: '9876500001', bloodGroup: 'A+', dpdpConsent: false, outstanding: 0,
};

const PAGE = {
  data: [WITHOUT_CONSENT],
  meta: { page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true },
};

function renderPage(path = "/patients") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/patients" element={<Patients />} />
        <Route path="/patients/:id" element={<Patients />} />
      </Routes>
    </MemoryRouter>
  );
}

async function openProfile(user) {
  await waitFor(() => expect(screen.getByText('Anita Sharma')).toBeInTheDocument());
  await user.click(screen.getByText('Anita Sharma'));
  await waitFor(() => expect(screen.getByRole('button', { name: /back/i })).toBeInTheDocument());
}

describe('late DPDP consent (issue 114)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    patientApi.get.mockResolvedValue({ data: WITHOUT_CONSENT });
    patientApi.summary.mockResolvedValue({ data: { total: 1, newToday: 1, activeOpd: 0, ipd: 0, discharged: 0 } });
    patientApi.list.mockResolvedValue({ ...PAGE, data: PAGE.data.map((p) => ({ ...p })) });
    encounterApi.listByPatient.mockResolvedValue({ data: [] });
    billingApi.getInvoices.mockResolvedValue({ data: [] });
    useAuthStore.setState({ token: 't', user: { username: 'reception', role: 'receptionist' }, role: 'receptionist' });
  });

  it('loads a bookmarked patient and distinguishes failed history from empty history', async () => {
    encounterApi.listByPatient.mockRejectedValueOnce(new Error('offline'));
    renderPage('/patients/p1');
    expect(await screen.findByRole('button', { name: /back/i })).toBeVisible();
    expect(patientApi.get).toHaveBeenCalledWith('p1');
    expect(await screen.findByText('Encounters unavailable.')).toBeVisible();
    expect(screen.queryByText('No encounters')).toBeNull();
  });

  it('offers to capture consent for a patient registered without it', async () => {
    const user = userEvent.setup();
    patientApi.recordConsent.mockResolvedValue({ data: { ...WITHOUT_CONSENT, dpdpConsent: true } });
    renderPage();
    await openProfile(user);

    expect(screen.getByText('DPDP: Not Consented')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Record consent' }));

    await waitFor(() => expect(patientApi.recordConsent).toHaveBeenCalledWith('p1', {
      granted: true,
      purpose: 'Treatment and billing',
    }));
    await waitFor(() => expect(screen.getByText('DPDP: Consented')).toBeInTheDocument());
    expect(screen.queryByText('DPDP: Not Consented')).not.toBeInTheDocument();
  });

  it('withdraws through a confirmation that explains the history is kept', async () => {
    const user = userEvent.setup();
    patientApi.list.mockResolvedValue({ ...PAGE, data: [{ ...WITHOUT_CONSENT, dpdpConsent: true }] });
    patientApi.get.mockResolvedValue({ data: { ...WITHOUT_CONSENT, dpdpConsent: true } });
    patientApi.withdrawConsent.mockResolvedValue({ data: { ...WITHOUT_CONSENT, dpdpConsent: false } });
    renderPage();
    await openProfile(user);

    await user.click(screen.getByRole('button', { name: 'Withdraw consent' }));
    const dialog = screen.getByRole('dialog', { name: 'Withdraw consent' });
    expect(dialog).toHaveTextContent(/does not delete the earlier grant/i);

    await user.type(screen.getByLabelText('Reason (optional)'), 'patient asked');
    await user.click(screen.getAllByRole('button', { name: 'Withdraw consent' }).at(-1));

    await waitFor(() => expect(patientApi.withdrawConsent).toHaveBeenCalledWith('p1', 'patient asked'));
    await waitFor(() => expect(screen.getByText('DPDP: Not Consented')).toBeInTheDocument());
  });

  it('does not show consent controls to a clinician', async () => {
    useAuthStore.setState({ token: 't', user: { username: 'doc', role: 'doctor' }, role: 'doctor' });
    const user = userEvent.setup();
    renderPage();
    await openProfile(user);

    expect(screen.queryByRole('button', { name: 'Record consent' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Withdraw consent' })).not.toBeInTheDocument();
  });
});

describe.each([true, false])('registration with selected consent = %s', (selected) => {
  it('sends the selected purposes through the registration contract', async () => {
    const user = userEvent.setup();
    useAuthStore.setState({ user: { username: 'desk', role: 'receptionist' } });
    patientApi.list.mockResolvedValue(PAGE);
    patientApi.summary.mockResolvedValue({ data: {} });
    patientApi.register.mockResolvedValue({ data: WITHOUT_CONSENT });
    renderPage();
    await user.click(screen.getByText('+ Register Patient'));
    if (selected) {
      for (const purpose of ['Treatment', 'Billing', 'Research']) {
        await user.click(screen.getByLabelText(purpose));
        await user.type(screen.getByLabelText('Purpose (' + purpose.toLowerCase() + ')'), purpose + ' explanation');
      }
      await user.click(screen.getByLabelText('Billing'));
    }
    await user.type(screen.getByLabelText('Full Name *'), 'Test Patient');
    await user.type(screen.getByLabelText('Age *'), '30');
    await user.type(screen.getByLabelText('Phone *'), '9999999999');
    await user.click(screen.getByRole('button', { name: 'Register Patient', exact: true }));
    expect(patientApi.register).toHaveBeenLastCalledWith(expect.objectContaining({
      dpdpConsent: selected,
      consentPurpose: selected ? 'Treatment: Treatment explanation; Research: Research explanation' : undefined,
    }));
  });
});
