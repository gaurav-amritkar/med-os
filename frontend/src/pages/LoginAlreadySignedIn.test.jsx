import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import Login from './Login';
import useAuthStore from '../store/authStore';
import useToastStore from '../store/toastStore';

vi.mock('../api', () => ({
  authApi: { login: vi.fn() },
}));

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname}</div>;
}

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Login />
      <LocationProbe />
    </MemoryRouter>
  );
}

/**
 * "Sign in" from the onboarding page has to lead somewhere.
 *
 * <p>It did not, and the cause was a redirect loop rather than a broken link.
 * Login redirected an already-authenticated caller away on mount; for a
 * super-admin the destination is /onboarding, which is the page carrying the
 * link. So clicking it returned the user to where they started and the link
 * read as dead.
 *
 * <p>Silent redirection is also the wrong behaviour on its own: someone who
 * deliberately navigates to the sign-in page to switch accounts is instead
 * thrown somewhere they did not ask for, with no explanation.
 */
describe('Login page — already signed in', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    useToastStore.setState({ toasts: [] });
    vi.clearAllMocks();
  });

  it('offers a way forward instead of silently bouncing a signed-in super-admin', async () => {
    useAuthStore.setState({
      token: 'live-token',
      role: 'super_admin',
      username: 'platform',
      userId: 'u-1',
      tenantId: null,
      user: { username: 'platform', fullName: 'Platform Operator' },
    });

    renderLogin();

    // Staying put with an explanation beats being redirected with none.
    await waitFor(() => {
      expect(screen.getByText(/already signed in/i)).toBeInTheDocument();
    });
    expect(screen.getByRole('link', { name: /continue/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign out/i })).toBeInTheDocument();
  });

  it('offers the same for a signed-in hospital admin', async () => {
    useAuthStore.setState({
      token: 'live-token',
      role: 'admin',
      username: 'asha',
      userId: 'u-2',
      tenantId: 't-1',
      user: { username: 'asha', fullName: 'Asha Rao' },
    });

    renderLogin();

    await waitFor(() => {
      expect(screen.getByText(/already signed in/i)).toBeInTheDocument();
    });
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
  });

  it('an anonymous visitor still gets the sign-in form', () => {
    useAuthStore.setState({ token: null, role: null, user: null, userId: null, tenantId: null });

    renderLogin();

    expect(screen.getByLabelText('Username')).toBeInTheDocument();
    expect(screen.queryByText(/already signed in/i)).not.toBeInTheDocument();
  });
});
