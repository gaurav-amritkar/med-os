import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import Login from './Login';
import { authApi } from '../api';
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

describe('Login page', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    useAuthStore.setState({ token: null, user: null });
    useToastStore.setState({ toasts: [] });
    vi.clearAllMocks();
  });

  it('renders the login form', () => {
    renderLogin();
    expect(screen.getByLabelText('Username')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('logs in successfully, stores the token and navigates to dashboard', async () => {
    authApi.login.mockResolvedValue({
      data: {
        token: 'jwt-123',
        userId: 'u1',
        username: 'admin',
        fullName: 'System Administrator',
        role: 'admin',
        specialization: null,
        expiresIn: 3600,
      },
    });

    renderLogin();
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/dashboard'));
    expect(authApi.login).toHaveBeenCalledWith({ username: 'admin', password: 'secret' });
    expect(useAuthStore.getState().token).toBe('jwt-123');
    expect(useAuthStore.getState().user.username).toBe('admin');
    expect(sessionStorage.getItem('medos_token')).toBe('jwt-123');
    expect(useToastStore.getState().toasts[0].message).toMatch(/welcome back/i);
  });

  it('tells an authenticated visitor they are signed in instead of redirecting', async () => {
    // Set valid token in sessionStorage
    const expiry = Date.now() + 3600000; // 1 hour from now
    sessionStorage.setItem('medos_token', 'existing-token');
    sessionStorage.setItem('medos_token_expiry', expiry.toString());
    sessionStorage.setItem('medos_user', JSON.stringify({ username: 'admin', role: 'admin' }));

    // Re-initialize auth store
    useAuthStore.getState().initAuth();

    renderLogin();

    // This used to assert a silent redirect to /dashboard, which is how the
    // "Sign in" link on the onboarding page became a no-op: for a super_admin
    // the redirect target was /onboarding, the page carrying the link.
    //
    // Now the page explains the situation and offers Continue or Sign out.
    // Deliberate navigation wins over convenience — someone opening the
    // sign-in page to switch accounts should not be moved without asking.
    await waitFor(() => expect(screen.getByText(/already signed in/i)).toBeInTheDocument());
    expect(screen.getByRole('link', { name: /continue/i })).toHaveAttribute('href', '/dashboard');
    expect(screen.getByRole('button', { name: /sign out/i })).toBeInTheDocument();
  });

  it('shows an error toast on failed login and stays on the page', async () => {
    authApi.login.mockRejectedValue({
      response: { data: { message: 'Invalid credentials' } },
    });

    renderLogin();
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'wrong' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => expect(useToastStore.getState().toasts[0].message).toBe('Invalid credentials'));
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
    expect(useAuthStore.getState().token).toBeNull();
  });
});
