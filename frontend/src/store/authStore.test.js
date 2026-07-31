import { describe, it, expect, beforeEach, vi } from 'vitest';
import useAuthStore from './authStore';

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.setState({ token: null, user: null });
  });

  it('starts unauthenticated', () => {
    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('login persists token and user to localStorage and state', () => {
    const user = { userId: 'u1', username: 'admin', role: 'admin' };
    useAuthStore.getState().login('jwt-token', user);

    expect(useAuthStore.getState().token).toBe('jwt-token');
    expect(useAuthStore.getState().user).toEqual(user);
    expect(useAuthStore.getState().isAuthenticated()).toBe(true);
    expect(localStorage.getItem('token')).toBe('jwt-token');
    expect(JSON.parse(localStorage.getItem('user'))).toEqual(user);
  });

  it('logout clears token and user everywhere', () => {
    useAuthStore.getState().login('jwt-token', { username: 'admin' });
    useAuthStore.getState().logout();

    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
  });

  it('restores session from localStorage on store creation', async () => {
    localStorage.setItem('token', 'persisted-token');
    localStorage.setItem('user', JSON.stringify({ username: 'doctor' }));

    vi.resetModules();
    const { default: freshStore } = await import('./authStore');
    expect(freshStore.getState().token).toBe('persisted-token');
    expect(freshStore.getState().user).toEqual({ username: 'doctor' });
    expect(freshStore.getState().isAuthenticated()).toBe(true);
  });
});
