import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Onboarding from './Onboarding';
import { onboardingApi } from '../api';
import useToastStore from '../store/toastStore';

vi.mock('../api', () => ({
  onboardingApi: { registerTenant: vi.fn() },
}));

function renderOnboarding() {
  return render(
    <MemoryRouter initialEntries={['/onboarding']}>
      <Onboarding />
    </MemoryRouter>
  );
}

/** Fill every field so a submit reaches the API rather than failing validation. */
function fillForm() {
  fireEvent.change(screen.getByLabelText(/facility name/i), { target: { value: 'Rogue Hospital' } });
  fireEvent.change(screen.getByLabelText(/slug/i), { target: { value: 'rogue' } });
  fireEvent.change(screen.getByLabelText(/contact email/i), { target: { value: 'c@x.test' } });
  fireEvent.change(screen.getByLabelText(/contact phone/i), { target: { value: '+919800000000' } });
  fireEvent.change(screen.getByLabelText(/^username$/i), { target: { value: 'rogue.admin' } });
  fireEvent.change(screen.getByLabelText(/^password$/i), { target: { value: 'Sup3rSecret!pass' } });
  fireEvent.change(screen.getByLabelText(/admin email/i), { target: { value: 'a@x.test' } });
}

describe('Onboarding page when self-serve registration is closed', () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] });
    vi.clearAllMocks();
  });

  // The endpoint used to be permitAll. It is now gated behind
  // medos.onboarding.public-signup, which defaults to false, so a 401 is the
  // expected response rather than an exceptional one. Surfacing the raw
  // "Authentication required" would read as a malfunction.
  it('explains before submission that registration is closed by default', () => {
    renderOnboarding();
    expect(
      screen.getByText(/self-serve registration is disabled|registration is closed/i)
    ).toBeInTheDocument();
  });

  it('shows an actionable message rather than a raw 401 when submitted anyway', async () => {
    onboardingApi.registerTenant.mockRejectedValue({ response: { status: 401 } });
    renderOnboarding();
    fillForm();

    fireEvent.click(screen.getByRole('button', { name: /register/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent(/self-serve registration is disabled/i);
    expect(alert).toHaveTextContent(/platform administrator/i);
    expect(alert).not.toHaveTextContent('Authentication required');
  });

  it('treats a 403 the same way, since a tenant admin is also refused', async () => {
    onboardingApi.registerTenant.mockRejectedValue({ response: { status: 403 } });
    renderOnboarding();
    fillForm();

    fireEvent.click(screen.getByRole('button', { name: /register/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/self-serve registration is disabled/i);
    });
  });

  it('still surfaces a genuine server error message', async () => {
    onboardingApi.registerTenant.mockRejectedValue({
      response: { status: 400, data: { message: 'Slug already in use' } },
    });
    renderOnboarding();
    fillForm();

    fireEvent.click(screen.getByRole('button', { name: /register/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Slug already in use');
    });
  });
});
