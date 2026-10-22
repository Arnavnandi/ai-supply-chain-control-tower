import axios from 'axios';

const DEFAULT_PROD_API_URL = 'https://ai-supply-chain-control-tower-backend.onrender.com/api';

export const DEFAULT_TIMEOUT = 15000;
export const STAGING_TIMEOUT = 60000;

export const getBaseURL = () => {
  if (typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL;
  }
  if (typeof import.meta !== 'undefined' && import.meta.env?.MODE === 'production') {
    return DEFAULT_PROD_API_URL;
  }
  return '/api';
};

export const getTimeout = (mode?: string, customTimeout?: string | number): number => {
  if (customTimeout !== undefined && customTimeout !== '') {
    const parsed = Number(customTimeout);
    if (!isNaN(parsed) && parsed > 0) return parsed;
  }
  const activeMode = mode !== undefined
    ? mode
    : (typeof import.meta !== 'undefined' ? import.meta.env?.MODE : undefined);
  if (activeMode === 'staging') {
    return STAGING_TIMEOUT;
  }
  return DEFAULT_TIMEOUT;
};

const axiosInstance = axios.create({
  baseURL: getBaseURL(),
  timeout: getTimeout(
    typeof import.meta !== 'undefined' ? import.meta.env?.MODE : undefined,
    typeof import.meta !== 'undefined' ? import.meta.env?.VITE_API_TIMEOUT : undefined
  ),
  headers: {
    'Content-Type': 'application/json',
  },
});

axiosInstance.interceptors.request.use(
  (config) => {
    if (typeof localStorage !== 'undefined') {
      const token = localStorage.getItem('control_tower_token');
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    }
    return config;
  },
  (error) => Promise.reject(error)
);

export default axiosInstance;
