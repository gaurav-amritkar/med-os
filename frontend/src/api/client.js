import axios from 'axios';
import useAuthStore from '../store/authStore';
import useLoadingStore from '../store/loadingStore';

// Build baseURL:
//  - No VITE_API_URL → default to localhost backend directly
//  - Starts with http/https → absolute, append /api/v1
//  - Starts with / → relative, use /api/v1 (nginx proxies /api/v1/ → backend:8080)
const raw = import.meta.env.VITE_API_URL;
let baseURL;
if (!raw) {
  baseURL = 'http://localhost:8080/api/v1';
} else if (raw.startsWith('http')) {
  baseURL = `${raw.replace(/\/$/, '')}/api/v1`;
} else {
  // Relative path — always go through /api/v1 so nginx can proxy it
  baseURL = '/api/v1';
}

const client = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
});

/**
 * Request interceptor - add auth token and start loading
 */
client.interceptors.request.use(
  (config) => {
    const { token, tenantId } = useAuthStore.getState();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    // Add tenantId header if available
    if (tenantId) {
      config.headers['X-Tenant-Id'] = tenantId;
    }
    // Start global loading
    useLoadingStore.getState().startLoading();
    return config;
  },
  (error) => {
    useLoadingStore.getState().stopLoading();
    return Promise.reject(error);
  }
);

/**
 * Response interceptor - stop loading, handle 401, and other errors
 */
client.interceptors.response.use(
  (response) => {
    useLoadingStore.getState().stopLoading();
    return response;
  },
  (error) => {
    useLoadingStore.getState().stopLoading();

    if (error.response?.status === 401) {
      // Token expired or invalid - logout and redirect
      useAuthStore.getState().logout();
      window.location.href = '/login';
    }

    return Promise.reject(error);
  }
);

export default client;
