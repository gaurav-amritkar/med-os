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

  it('logs in successfully, stores the token and navigates home', async () => {
    authApi.login.mockResolvedValue({
      data: {
        token: 'jwt-123',
        userId: 'u1',
        username: 'admin',
        fullName: 'System Administrator',
        role: 'admin',
        specialization: null,
      },
    });

    renderLogin();
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/'));
    expect(authApi.login).toHaveBeenCalledWith({ username: 'admin', password: 'secret' });
    expect(useAuthStore.getState().token).toBe('jwt-123');
    expect(useAuthStore.getState().user.username).toBe('admin');
    expect(localStorage.getItem('token')).toBe('jwt-123');
    expect(useToastStore.getState().toasts[0].message).toMatch(/welcome back/i);
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
