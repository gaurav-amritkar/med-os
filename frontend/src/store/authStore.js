import { create } from 'zustand';

const storedUser = (() => {
  try {
    const u = localStorage.getItem('user');
    return u ? JSON.parse(u) : null;
  } catch { return null; }
})();

const useAuthStore = create((set) => ({
  user: storedUser,
  token: localStorage.getItem('token'),
  login: (token, user) => {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(user));
    set({ token, user });
  },
  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    set({ token: null, user: null });
  },
  isAuthenticated: () => !!localStorage.getItem('token'),
}));

export default useAuthStore;
