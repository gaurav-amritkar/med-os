import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import Onboarding from './Onboarding';
import { onboardingApi } from '../api';
import useToastStore from '../store/toastStore';
import useAuthStore from '../store/authStore';

vi.mock('../api', () => ({
  onboardingApi: { registerTenant: vi.fn(), listTenants: vi.fn() },
  patientApi: {},
  billingApi: {},
  userApi: {},
}));

/**
 * Two things a platform administrator needs from this page.
 *
 * 1. The "Sign in" link has to actually take them somewhere.
 *
 *    It did not. Login.jsx redirects an already-authenticated caller away on
 *    mount, and for a super_admin the destination is /onboarding — the page the
 *    link is on. So clicking "Sign in" bounced straight back and the link read
 *    as dead.
 *
 * 2. A list of the organisations already onboarded, so a duplicate is visible
 *    before it is a 409.
 */
const TENANTS = [
  {
    id: 't-1',
    name: 'Asha Rao Hospital',
    slug: 'asha-rai',
    type: 'HOSPITAL',
    contactEmail: 'hello@asha-rai.test',
    contactPhone: '+91 90000 00001',
    address: '1 Test Street',
    active: true,
  },
  {
    id: 't-2',
    name: "Bilal's Clinic",
    slug: 'bilal-clinic',
    type: 'CLINIC',
    contactEmail: 'hello@bilal-clinic.test',
    contactPhone: '+91 90000 00002',
    address: '2 Test Street',
    active: false,
  },
];

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/onboarding']}>
      <Routes>
        <Route path="/onboarding" element={<Onboarding />} />
        <Route path="/login" element={<h1>Sign in page</h1>} />
        <Route path="/dashboard" element={<h1>Dashboard page</h1>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('Onboarding page — sign-in link and onboarded hospitals', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useToastStore.setState({ toasts: [] });
    useAuthStore.setState({ token: null, role: null, userId: null, username: null, user: null, tenantId: null });
    onboardingApi.listTenants.mockResolvedValue({ data: TENANTS });
  });

  it('lists the organisations already onboarded', async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('Asha Rao Hospital')).toBeInTheDocument();
    });
    // Rendered as a URL path, because that is what an administrator copies.
    expect(screen.getByText(/\/asha-rai/)).toBeInTheDocument();
    expect(screen.getByText("Bilal's Clinic")).toBeInTheDocument();
  });

  it('marks a deactivated organisation as such rather than hiding it', async () => {
    renderPage();

    await waitFor(() => expect(screen.getByText("Bilal's Clinic")).toBeInTheDocument());
    // The question an admin actually has is "why can't this person sign in",
    // which needs the row present with its state visible. Scoped to the row so
    // it cannot be satisfied by the registration banner above it.
    const row = screen.getByText("Bilal's Clinic").closest('li');
    expect(row).toBeInTheDocument();
    expect(within(row).getByText('Inactive')).toBeInTheDocument();
  });

  it('says so when nothing has been onboarded yet', async () => {
    onboardingApi.listTenants.mockResolvedValue({ data: [] });
    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/no organisations|nothing.*yet/i)).toBeInTheDocument();
    });
  });

  it('does not break the page when the list request fails', async () => {
    onboardingApi.listTenants.mockRejectedValue({ response: { status: 403 } });
    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/cannot|not available|restricted/i)).toBeInTheDocument();
    });
    // The registration form is the page's primary job; a failure to list must
    // not take it offline.
    expect(screen.getByText('Register Your Facility')).toBeInTheDocument();
  });

  it('the sign-in link navigates to the sign-in page', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('link', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /sign in page/i })).toBeInTheDocument();
    });
  });
});
