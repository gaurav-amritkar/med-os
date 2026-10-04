import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Sidebar from '../components/Sidebar';
import useAuthStore from '../store/authStore';

vi.mock('../api', () => ({
  authApi: { getMe: vi.fn() },
}));

import { authApi } from '../api';

function renderSidebar(role) {
  useAuthStore.setState({ user: { username: 'owner', role }, token: 't', tenantId: 'ten-1' });
  return render(
    <MemoryRouter>
      <Sidebar isOpen onClose={() => {}} />
    </MemoryRouter>
  );
}

describe('Sidebar module gating', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('hides the modules the hospital did not enable', async () => {
    authApi.getMe.mockResolvedValue({ data: { features: ['encounters', 'admissions'] } });

    renderSidebar('ADMIN');

    await waitFor(() => expect(screen.queryByText('Pharmacy')).toBeNull());
    await waitFor(() => expect(screen.queryByText('Billing')).toBeNull());
    expect(screen.getByText('OPD')).toBeTruthy();
    expect(screen.getByText('IPD / Wards')).toBeTruthy();
    expect(screen.getByText('Staff')).toBeTruthy();
  });

  it('shows every module when the hospital enabled all of them', async () => {
    authApi.getMe.mockResolvedValue({
      data: { features: ['encounters', 'admissions', 'pharmacy', 'billing'] },
    });

    renderSidebar('ADMIN');

    await waitFor(() => expect(screen.getByText('Pharmacy')).toBeTruthy());
    expect(screen.getByText('Billing')).toBeTruthy();
  });

  it('falls open to the full nav when the feature list cannot be loaded', async () => {
    authApi.getMe.mockRejectedValue(new Error('network down'));

    renderSidebar('ADMIN');

    await waitFor(() => expect(screen.getByText('Pharmacy')).toBeTruthy());
    expect(screen.getByText('Billing')).toBeTruthy();
  });

  it('gives the platform super-admin a provisioning rail instead of a hospital nav', async () => {
    authApi.getMe.mockResolvedValue({ data: { features: [] } });

    renderSidebar('super_admin');

    await waitFor(() => expect(screen.getByText('Register Hospital')).toBeTruthy());
    expect(screen.queryByText('Patients')).toBeNull();
    expect(screen.queryByText('Patients')).toBeNull();
  });
});