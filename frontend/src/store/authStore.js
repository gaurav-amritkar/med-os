import { create } from 'zustand';

const TOKEN_KEY = 'medos_token';
const USER_KEY = 'medos_user';
const TOKEN_EXPIRY_KEY = 'medos_token_expiry';
const TENANT_KEY = 'medos_tenant';
const ROLE_KEY = 'medos_role';

/**
 * Get token from sessionStorage with expiration check
 */
const getStoredToken = () => {
  try {
    const token = sessionStorage.getItem(TOKEN_KEY);
    const expiry = sessionStorage.getItem(TOKEN_EXPIRY_KEY);

    if (!token || !expiry) return null;

    // Check if token is expired
    if (Date.now() > parseInt(expiry, 10)) {
      sessionStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(USER_KEY);
      sessionStorage.removeItem(TOKEN_EXPIRY_KEY);
      sessionStorage.removeItem(TENANT_KEY);
      sessionStorage.removeItem(ROLE_KEY);
      return null;
    }

    return token;
  } catch {
    return null;
  }
};

/**
 * Get user from sessionStorage - also checks token expiry
 */
const getStoredUser = () => {
  try {
    const token = sessionStorage.getItem(TOKEN_KEY);
    const expiry = sessionStorage.getItem(TOKEN_EXPIRY_KEY);

    // If no token or expired, return null
    if (!token || !expiry) return null;
    if (Date.now() > parseInt(expiry, 10)) {
      return null;
    }

    const user = sessionStorage.getItem(USER_KEY);
    return user ? JSON.parse(user) : null;
  } catch {
    return null;
  }
};

const getStoredTenant = () => {
  return sessionStorage.getItem(TENANT_KEY) || null;
};

const getStoredRole = () => {
  return sessionStorage.getItem(ROLE_KEY) || null;
};

/**
 * Calculate token expiry (default 24 hours if not provided by backend)
 */
const calculateExpiry = (expiresIn) => {
  const seconds = expiresIn || 24 * 60 * 60;
  return Date.now() + (seconds * 1000);
};

const useAuthStore = create((set, get) => ({
  user: getStoredUser(),
  token: getStoredToken(),
  tenantId: getStoredTenant(),
  role: getStoredRole(),

  login: (token, user, expiresIn) => {
    const expiry = calculateExpiry(expiresIn);
    sessionStorage.setItem(TOKEN_KEY, token);
    sessionStorage.setItem(USER_KEY, JSON.stringify(user));
    sessionStorage.setItem(TOKEN_EXPIRY_KEY, expiry.toString());
    if (user.role) sessionStorage.setItem(ROLE_KEY, user.role);
    if (user.tenantId) sessionStorage.setItem(TENANT_KEY, user.tenantId);
    set({ token, user, role: user.role, tenantId: user.tenantId });
  },

  logout: () => {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(TOKEN_EXPIRY_KEY);
    sessionStorage.removeItem(TENANT_KEY);
    sessionStorage.removeItem(ROLE_KEY);
    set({ token: null, user: null, role: null, tenantId: null });
  },

  isAuthenticated: () => !!getStoredToken(),
  getToken: () => getStoredToken(),

  getTenantId: () => get().tenantId,

  refreshToken: (newToken, expiresIn) => {
    const expiry = calculateExpiry(expiresIn);
    sessionStorage.setItem(TOKEN_KEY, newToken);
    sessionStorage.setItem(TOKEN_EXPIRY_KEY, expiry.toString());
    set({ token: newToken });
  },

  initAuth: () => {
    const token = getStoredToken();
    const user = getStoredUser();
    const tenantId = getStoredTenant();
    const role = getStoredRole();
    if (token && user) {
      set({ token, user, tenantId, role });
    }
    return { token, user, tenantId, role };
  },
}));

export default useAuthStore;