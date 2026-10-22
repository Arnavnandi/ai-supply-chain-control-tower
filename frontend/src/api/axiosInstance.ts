import axios from 'axios';

const DEFAULT_PROD_API_URL = 'https://ai-supply-chain-control-tower-backend.onrender.com/api';

const getBaseURL = () => {
  if (typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL;
  }
  if (typeof import.meta !== 'undefined' && import.meta.env?.MODE === 'production') {
    return DEFAULT_PROD_API_URL;
  }
  return '/api';
};

const axiosInstance = axios.create({
  baseURL: getBaseURL(),
  timeout: 15000,
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
