import { describe, it, expect, beforeEach, vi } from 'vitest';
import useAuthStore from './authStore';

describe('authStore', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    useAuthStore.setState({ token: null, user: null });
  });

  it('starts unauthenticated', () => {
    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('login persists token and user to sessionStorage and state', () => {
    const user = { userId: 'u1', username: 'admin', role: 'admin' };
    useAuthStore.getState().login('jwt-token', user, 3600);

    expect(useAuthStore.getState().token).toBe('jwt-token');
    expect(useAuthStore.getState().user).toEqual(user);
    expect(useAuthStore.getState().isAuthenticated()).toBe(true);
    expect(sessionStorage.getItem('medos_token')).toBe('jwt-token');
    expect(sessionStorage.getItem('medos_user')).toBeTruthy();
    expect(sessionStorage.getItem('medos_token_expiry')).toBeTruthy();
  });

  it('logout clears token and user everywhere', () => {
    useAuthStore.getState().login('jwt-token', { username: 'admin' }, 3600);
    useAuthStore.getState().logout();

    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem('medos_token')).toBeNull();
    expect(sessionStorage.getItem('medos_user')).toBeNull();
    expect(sessionStorage.getItem('medos_token_expiry')).toBeNull();
  });

  it('restores session from sessionStorage on store creation', async () => {
    sessionStorage.setItem('medos_token', 'persisted-token');
    sessionStorage.setItem('medos_user', JSON.stringify({ username: 'doctor' }));
    const expiry = Date.now() + 3600000;
    sessionStorage.setItem('medos_token_expiry', expiry.toString());

    vi.resetModules();
    const { default: freshStore } = await import('./authStore');
    expect(freshStore.getState().token).toBe('persisted-token');
    expect(freshStore.getState().user).toEqual({ username: 'doctor' });
    expect(freshStore.getState().isAuthenticated()).toBe(true);
  });

  it('returns null for expired token', async () => {
    sessionStorage.setItem('medos_token', 'expired-token');
    sessionStorage.setItem('medos_user', JSON.stringify({ username: 'doctor' }));
    const expiry = Date.now() - 1000; // 1 second ago
    sessionStorage.setItem('medos_token_expiry', expiry.toString());

    vi.resetModules();
    const { default: freshStore } = await import('./authStore');
    expect(freshStore.getState().token).toBeNull();
    expect(freshStore.getState().user).toBeNull();
    expect(freshStore.getState().isAuthenticated()).toBe(false);
  });

  it('getToken returns null for expired token', () => {
    useAuthStore.getState().login('jwt-token', { username: 'admin' }, -1); // Expired
    
    expect(useAuthStore.getState().getToken()).toBeNull();
  });

  it('refreshToken updates token and expiry', () => {
    useAuthStore.getState().login('old-token', { username: 'admin' }, 3600);
    useAuthStore.getState().refreshToken('new-token', 7200);

    expect(useAuthStore.getState().token).toBe('new-token');
    expect(sessionStorage.getItem('medos_token')).toBe('new-token');
    expect(useAuthStore.getState().isAuthenticated()).toBe(true);
  });
});