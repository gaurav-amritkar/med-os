import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Patients from './Patients';
import { patientApi } from '../api';
import useToastStore from '../store/toastStore';

vi.mock('../api', () => ({
  patientApi: { list: vi.fn() },
  userApi: {},
  billingApi: {},
}));

/**
 * A failed request is not an empty result.
 *
 * <p>The list used to swallow every error and render "No patients found", which reports a
 * server fault as "this hospital has no patients". That is the worst possible reading for a
 * clinician: it invites them to conclude the record does not exist, or to go and re-register
 * the patient — when in fact the server returned a 500 and nothing was known.
 */
function renderPage() {
  return render(
    <MemoryRouter>
      <Patients />
    </MemoryRouter>
  );
}

describe('Patients list failure handling', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useToastStore.setState({ toasts: [] });
  });

  it('shows an error, not "No patients found", when the request fails', async () => {
    patientApi.list.mockRejectedValue({
      response: { status: 500, data: { message: 'An unexpected error occurred' } },
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
    expect(screen.getByRole('alert').textContent).toMatch(/could not load patients/i);
    expect(screen.queryByText(/no patients found/i)).not.toBeInTheDocument();
  });

  it('does not blame the search term when the server is at fault', async () => {
    patientApi.list.mockRejectedValue({ response: { status: 500, data: {} } });

    renderPage();

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(screen.queryByText(/no patients match/i)).not.toBeInTheDocument();
  });

  it('still shows the empty state when the request genuinely succeeds with no rows', async () => {
    patientApi.list.mockResolvedValue({
      data: [],
      meta: { page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true },
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/no patients found/i)).toBeInTheDocument();
    });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('renders the rows when the request succeeds', async () => {
    patientApi.list.mockResolvedValue({
      data: [{ id: 'p1', uhid: 'UHID000001', name: 'Anita Sharma', dpdpConsent: true }],
      meta: { page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true },
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('Anita Sharma')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});