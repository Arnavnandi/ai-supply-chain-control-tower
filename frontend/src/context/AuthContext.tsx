import React, { createContext, useContext, useState, useEffect } from 'react';
import axiosInstance from '../api/axiosInstance';

interface User {
  id: number;
  username: string;
  email: string;
  role: string;
  companyId?: number;
  companyCode?: string;
  companyName?: string;
}

export interface LoginInitiationResult {
  mfaRequired?: boolean;
  mfaToken?: string;
  maskedEmail?: string;
  expiresInSeconds?: number;
  resendCooldownSeconds?: number;
  message?: string;
  token?: string;
  id?: number;
  username?: string;
  email?: string;
  role?: string;
  companyId?: number;
  companyCode?: string;
  companyName?: string;
}

interface AuthContextType {
  user: User | null;
  token: string | null;
  login: (usernameOrEmail: string, password: string) => Promise<LoginInitiationResult>;
  verifyOtp: (mfaToken: string, otp: string) => Promise<void>;
  resendOtp: (mfaToken: string) => Promise<LoginInitiationResult>;
  register: (username: string, email: string, password: string, companyCode?: string, companyName?: string) => Promise<LoginInitiationResult>;
  logout: () => void;
  isLoading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(localStorage.getItem('control_tower_token'));
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    let isMounted = true;

    const fetchUser = async () => {
      if (token) {
        try {
          const res = await axiosInstance.get('/auth/me', { timeout: 8000 });
          if (isMounted) {
            setUser(res.data);
          }
        } catch (err: any) {
          if (isMounted) {
            if (err.response && (err.response.status === 401 || err.response.status === 403)) {
              logout();
            } else {
              console.warn('Session restoration request timed out or experienced network error:', err.message);
            }
          }
        }
      }
      if (isMounted) {
        setIsLoading(false);
      }
    };

    fetchUser();

    return () => {
      isMounted = false;
    };
  }, [token]);

  const login = async (usernameOrEmail: string, password: string): Promise<LoginInitiationResult> => {
    const res = await axiosInstance.post('/auth/login', { usernameOrEmail, password });
    if (res.data && res.data.token) {
      const { token: jwtToken, id, username, email, role, companyId, companyCode, companyName } = res.data;
      localStorage.setItem('control_tower_token', jwtToken);
      setToken(jwtToken);
      setUser({ id, username, email, role, companyId, companyCode, companyName });
    }
    return res.data;
  };

  const verifyOtp = async (mfaToken: string, otp: string): Promise<void> => {
    const res = await axiosInstance.post('/auth/verify-otp', { mfaToken, otp });
    const { token: jwtToken, id, username, email, role, companyId, companyCode, companyName } = res.data;
    localStorage.setItem('control_tower_token', jwtToken);
    setToken(jwtToken);
    setUser({ id, username, email, role, companyId, companyCode, companyName });
  };

  const resendOtp = async (mfaToken: string): Promise<LoginInitiationResult> => {
    const res = await axiosInstance.post('/auth/resend-otp', { mfaToken });
    return res.data;
  };

  const register = async (username: string, email: string, password: string, companyCode?: string, companyName?: string): Promise<LoginInitiationResult> => {
    await axiosInstance.post('/auth/register', { username, email, password, companyCode, companyName });
    return await login(username, password);
  };

  const logout = () => {
    localStorage.removeItem('control_tower_token');
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, login, verifyOtp, resendOtp, register, logout, isLoading }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
};
