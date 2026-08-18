import { create } from 'zustand';

const TOKEN_KEY = 'medos_token';
const USER_KEY = 'medos_user';
const TOKEN_EXPIRY_KEY = 'medos_token_expiry';

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

/**
 * Calculate token expiry (default 24 hours if not provided by backend)
 */
const calculateExpiry = (expiresIn) => {
  // If backend provides expires_in (seconds), use it
  // Otherwise default to 24 hours
  const seconds = expiresIn || 24 * 60 * 60;
  return Date.now() + (seconds * 1000);
};

const useAuthStore = create((set, get) => ({
  user: getStoredUser(),
  token: getStoredToken(),
  
  /**
   * Login - store token and user in sessionStorage
   * @param {string} token - JWT token
   * @param {Object} user - User object
   * @param {number} expiresIn - Token expiry in seconds (optional)
   */
  login: (token, user, expiresIn) => {
    const expiry = calculateExpiry(expiresIn);
    sessionStorage.setItem(TOKEN_KEY, token);
    sessionStorage.setItem(USER_KEY, JSON.stringify(user));
    sessionStorage.setItem(TOKEN_EXPIRY_KEY, expiry.toString());
    set({ token, user });
  },
  
  /**
   * Logout - clear sessionStorage and state
   */
  logout: () => {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(TOKEN_EXPIRY_KEY);
    set({ token: null, user: null });
  },
  
  /**
   * Check if user is authenticated (token exists and not expired)
   */
  isAuthenticated: () => {
    const token = getStoredToken();
    return !!token;
  },
  
  /**
   * Get current token (with expiration check)
   */
  getToken: () => {
    return getStoredToken();
  },
  
  /**
   * Refresh token - update token and expiry
   */
  refreshToken: (newToken, expiresIn) => {
    const expiry = calculateExpiry(expiresIn);
    sessionStorage.setItem(TOKEN_KEY, newToken);
    sessionStorage.setItem(TOKEN_EXPIRY_KEY, expiry.toString());
    set({ token: newToken });
  },
  
  /**
   * Initialize auth state from storage (useful on app load)
   */
  initAuth: () => {
    const token = getStoredToken();
    const user = getStoredUser();
    if (token && user) {
      set({ token, user });
    }
    return { token, user };
  },
}));

export default useAuthStore;