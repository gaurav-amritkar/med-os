import axios from 'axios';
import useAuthStore from '../store/authStore';
import useLoadingStore from '../store/loadingStore';

const baseURL = import.meta.env.VITE_API_URL
  ? (import.meta.env.VITE_API_URL.startsWith('/')
      ? import.meta.env.VITE_API_URL
      : `${import.meta.env.VITE_API_URL}/api`)
  : 'http://localhost:8080/api';

const client = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
});

/**
 * Request interceptor - add auth token and start loading
 */
client.interceptors.request.use(
  (config) => {
    const token = useAuthStore.getState().getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
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