import React, { useState, useEffect } from 'react';
import { useNavigate, Navigate } from 'react-router-dom';
import { useAuth, type LoginInitiationResult } from '../context/AuthContext';
import {
  Bot,
  ArrowRight,
  AlertCircle,
  CheckCircle2,
  Eye,
  EyeOff,
  UserPlus,
  LogIn,
  Lock,
  Mail,
  User,
  Building2,
  ShieldCheck,
  RotateCcw,
  Clock,
  ArrowLeft
} from 'lucide-react';

export const LoginPage: React.FC = () => {
  const { token, login, verifyOtp, resendOtp, register } = useAuth();
  const navigate = useNavigate();

  // Mode: 'login' | 'register'
  const [mode, setMode] = useState<'login' | 'register'>('login');

  // MFA Challenge state
  const [mfaChallenge, setMfaChallenge] = useState<LoginInitiationResult | null>(null);
  const [otpDigits, setOtpDigits] = useState('');
  const [cooldownRemaining, setCooldownRemaining] = useState(0);
  const [expiryRemaining, setExpiryRemaining] = useState(300);
  const [resending, setResending] = useState(false);

  // Form states
  const [usernameOrEmail, setUsernameOrEmail] = useState('admin');
  const [password, setPassword] = useState('admin123');

  const [regUsername, setRegUsername] = useState('');
  const [regEmail, setRegEmail] = useState('');
  const [regPassword, setRegPassword] = useState('');
  const [regCompanyName, setRegCompanyName] = useState('');

  // UI state
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  // Field validation touched states for register
  const [touched, setTouched] = useState({ username: false, email: false, password: false });

  // Countdown timers for OTP expiry and resend cooldown
  useEffect(() => {
    if (!mfaChallenge) return;
    const interval = setInterval(() => {
      setCooldownRemaining((prev) => (prev > 0 ? prev - 1 : 0));
      setExpiryRemaining((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(interval);
  }, [mfaChallenge]);

  if (token) {
    return <Navigate to="/" replace />;
  }

  // Inline validation logic
  const isEmailValid = (email: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  const isUsernameValid = (user: string) => user.trim().length >= 3 && user.trim().length <= 50;
  const isPasswordValid = (pass: string) => pass.length >= 6;

  const formatTimer = (totalSeconds: number) => {
    const m = Math.floor(totalSeconds / 60);
    const s = totalSeconds % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      const res = await login(usernameOrEmail.trim(), password);
      if (res && res.mfaRequired) {
        setMfaChallenge(res);
        setOtpDigits('');
        setCooldownRemaining(res.resendCooldownSeconds || 60);
        setExpiryRemaining(res.expiresInSeconds || 300);
        setSuccess(`Verification code dispatched to ${res.maskedEmail}`);
      } else {
        navigate('/');
      }
    } catch (err: any) {
      if (!err.response) {
        setError('Unable to connect to backend server. Please verify your connection.');
      } else if (err.response.status === 401) {
        setError('Invalid credentials. Please verify your username and password.');
      } else if (err.response.status === 503) {
        setError(err.response.data?.message || 'Email delivery service is currently unavailable. Please check staging SMTP relay.');
      } else {
        setError(err.response.data?.message || 'Authentication request failed. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    // Trigger validation
    setTouched({ username: true, email: true, password: true });

    if (!isUsernameValid(regUsername)) {
      setError('Username must be between 3 and 50 characters.');
      return;
    }
    if (!isEmailValid(regEmail)) {
      setError('Please provide a valid email address.');
      return;
    }
    if (!isPasswordValid(regPassword)) {
      setError('Password must be at least 6 characters long.');
      return;
    }

    setLoading(true);

    try {
      const res = await register(
        regUsername.trim(),
        regEmail.trim(),
        regPassword,
        undefined,
        regCompanyName.trim() || undefined
      );
      if (res && res.mfaRequired) {
        setMfaChallenge(res);
        setOtpDigits('');
        setCooldownRemaining(res.resendCooldownSeconds || 60);
        setExpiryRemaining(res.expiresInSeconds || 300);
        setSuccess(`Account registered! Verification code dispatched to ${res.maskedEmail}`);
      } else {
        navigate('/');
      }
    } catch (err: any) {
      if (!err.response) {
        setError('Unable to connect to backend server. Please try again later.');
      } else if (err.response.status === 400 || err.response.status === 409) {
        setError(err.response.data?.message || 'Registration failed. Username or email may already be taken.');
      } else if (err.response.status === 503) {
        setError(err.response.data?.message || 'Email delivery service is currently unavailable.');
      } else {
        setError(err.response.data?.message || 'An unexpected error occurred during account creation.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleOtpSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!mfaChallenge || !mfaChallenge.mfaToken) return;
    if (otpDigits.trim().length !== 6) {
      setError('Please enter the complete 6-digit verification code.');
      return;
    }
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      await verifyOtp(mfaChallenge.mfaToken, otpDigits.trim());
      setSuccess('Verification successful! Launching Control Tower...');
      setTimeout(() => {
        navigate('/');
      }, 400);
    } catch (err: any) {
      if (!err.response) {
        setError('Unable to connect to backend server.');
      } else if (err.response.status === 400 || err.response.status === 401) {
        setError(err.response.data?.message || 'Invalid or expired verification code.');
      } else if (err.response.status === 429) {
        setError(err.response.data?.message || 'Too many attempts. Verification session locked.');
      } else {
        setError(err.response.data?.message || 'Verification failed. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleResendOtp = async () => {
    if (!mfaChallenge || !mfaChallenge.mfaToken || cooldownRemaining > 0 || resending) return;
    setError('');
    setSuccess('');
    setResending(true);

    try {
      const res = await resendOtp(mfaChallenge.mfaToken);
      setCooldownRemaining(res.resendCooldownSeconds || 60);
      setExpiryRemaining(res.expiresInSeconds || 300);
      setSuccess(`A new verification code was dispatched to ${mfaChallenge.maskedEmail}`);
    } catch (err: any) {
      if (!err.response) {
        setError('Unable to connect to backend server.');
      } else {
        setError(err.response.data?.message || 'Failed to resend code. Please try again later.');
      }
    } finally {
      setResending(false);
    }
  };

  const handleResetToSignIn = () => {
    setMfaChallenge(null);
    setOtpDigits('');
    setError('');
    setSuccess('');
  };

  const handleSelectDemoUser = (user: string, pass: string) => {
    setMode('login');
    setMfaChallenge(null);
    setUsernameOrEmail(user);
    setPassword(pass);
    setError('');
    setSuccess('');
  };

  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center p-4 relative overflow-hidden font-sans">
      {/* Background Glows */}
      <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-cyan-600/15 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-blue-600/15 rounded-full blur-3xl pointer-events-none" />

      <div className="w-full max-w-md glass-panel p-8 rounded-2xl shadow-2xl relative z-10 border border-slate-800">
        {/* Header Logo */}
        <div className="flex flex-col items-center mb-6">
          <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-cyan-500 to-blue-600 flex items-center justify-center shadow-xl shadow-cyan-500/30 mb-4">
            {mfaChallenge ? <ShieldCheck className="w-8 h-8 text-white" /> : <Bot className="w-8 h-8 text-white" />}
          </div>
          <h1 className="text-2xl font-bold text-white tracking-tight">
            {mfaChallenge ? 'Two-Factor Verification' : 'Supply Chain Control Tower'}
          </h1>
          <p className="text-xs text-cyan-400 font-semibold tracking-wide mt-1">
            {mfaChallenge ? 'Secure Email One-Time Password' : 'Enterprise Intelligence Platform'}
          </p>
        </div>

        {/* Tab Switcher (Only visible before MFA challenge) */}
        {!mfaChallenge && (
          <div className="flex rounded-xl bg-slate-900/90 p-1 mb-6 border border-slate-800">
            <button
              type="button"
              onClick={() => {
                setMode('login');
                setError('');
                setSuccess('');
              }}
              className={`flex-1 py-2 px-3 rounded-lg text-xs font-bold transition-all flex items-center justify-center space-x-2 ${
                mode === 'login'
                  ? 'bg-gradient-to-r from-cyan-500 to-blue-600 text-white shadow-md'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <LogIn className="w-3.5 h-3.5" />
              <span>Sign In</span>
            </button>

            <button
              type="button"
              onClick={() => {
                setMode('register');
                setError('');
                setSuccess('');
              }}
              className={`flex-1 py-2 px-3 rounded-lg text-xs font-bold transition-all flex items-center justify-center space-x-2 ${
                mode === 'register'
                  ? 'bg-gradient-to-r from-cyan-500 to-blue-600 text-white shadow-md'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <UserPlus className="w-3.5 h-3.5" />
              <span>Create Account</span>
            </button>
          </div>
        )}

        {/* Error Notification */}
        {error && (
          <div role="alert" className="mb-5 p-3 rounded-lg bg-rose-950/70 border border-rose-500/40 flex items-start space-x-2.5 text-rose-300 text-xs">
            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
            <span className="leading-relaxed">{error}</span>
          </div>
        )}

        {/* Success Notification */}
        {success && (
          <div role="status" className="mb-5 p-3 rounded-lg bg-emerald-950/70 border border-emerald-500/40 flex items-start space-x-2.5 text-emerald-300 text-xs">
            <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5" />
            <span className="leading-relaxed">{success}</span>
          </div>
        )}

        {/* STEP 2: OTP VERIFICATION CARD */}
        {mfaChallenge ? (
          <form onSubmit={handleOtpSubmit} className="space-y-5">
            <div className="p-3.5 rounded-xl bg-slate-900/80 border border-slate-800 text-center space-y-1">
              <p className="text-xs text-slate-300">
                A one-time verification code has been dispatched to:
              </p>
              <p className="text-sm font-mono font-bold text-cyan-400">
                {mfaChallenge.maskedEmail}
              </p>
            </div>

            <div>
              <label htmlFor="otp-input" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2 text-center">
                Enter 6-Digit Code
              </label>
              <input
                id="otp-input"
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                maxLength={6}
                value={otpDigits}
                onChange={(e) => {
                  const cleaned = e.target.value.replace(/\D/g, '').slice(0, 6);
                  setOtpDigits(cleaned);
                }}
                autoFocus
                required
                disabled={loading}
                className="w-full py-3 px-4 rounded-xl bg-slate-900/90 border border-cyan-500/50 text-white font-mono text-center text-2xl tracking-[0.4em] placeholder-slate-600 focus:outline-none focus:ring-2 focus:ring-cyan-500/50 focus:border-cyan-400 transition-all"
                placeholder="••••••"
              />
            </div>

            {/* Countdown and Expiry Notice */}
            <div className="flex items-center justify-between text-xs px-1">
              <div className="flex items-center space-x-1.5 text-slate-400">
                <Clock className="w-3.5 h-3.5" />
                <span>
                  Expires in:{' '}
                  <span className={`font-mono font-bold ${expiryRemaining < 60 ? 'text-rose-400' : 'text-slate-200'}`}>
                    {formatTimer(expiryRemaining)}
                  </span>
                </span>
              </div>

              <button
                type="button"
                onClick={handleResendOtp}
                disabled={cooldownRemaining > 0 || resending}
                className="text-cyan-400 hover:text-cyan-300 disabled:text-slate-600 font-semibold flex items-center space-x-1 text-xs transition-colors cursor-pointer disabled:cursor-not-allowed"
              >
                <RotateCcw className={`w-3 h-3 ${resending ? 'animate-spin' : ''}`} />
                <span>
                  {cooldownRemaining > 0 ? `Resend (${cooldownRemaining}s)` : 'Resend Code'}
                </span>
              </button>
            </div>

            <button
              type="submit"
              disabled={loading || otpDigits.length !== 6 || expiryRemaining === 0}
              className="w-full py-3 px-4 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-white font-bold text-xs shadow-lg shadow-cyan-500/25 transition-all flex items-center justify-center space-x-2 disabled:opacity-50 cursor-pointer"
            >
              <span>{loading ? 'Verifying OTP...' : 'Verify & Launch Control Tower'}</span>
              {!loading && <ArrowRight className="w-4 h-4" />}
            </button>

            <div className="pt-2 text-center">
              <button
                type="button"
                onClick={handleResetToSignIn}
                className="text-slate-400 hover:text-slate-200 text-xs font-medium inline-flex items-center space-x-1 transition-colors"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>Back to Sign In</span>
              </button>
            </div>
          </form>
        ) : (
          <>
            {/* STEP 1: SIGN IN FORM */}
            {mode === 'login' && (
              <form onSubmit={handleLoginSubmit} className="space-y-4">
                <div>
                  <label htmlFor="login-username" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                    Username or Email
                  </label>
                  <div className="relative">
                    <User className="w-4 h-4 text-slate-500 absolute left-3.5 top-3.5" />
                    <input
                      id="login-username"
                      type="text"
                      value={usernameOrEmail}
                      onChange={(e) => setUsernameOrEmail(e.target.value)}
                      required
                      disabled={loading}
                      className="w-full pl-10 pr-4 py-2.5 rounded-lg bg-slate-900/90 border border-slate-700/80 text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500 focus:ring-2 focus:ring-cyan-500/40 transition-all text-xs"
                      placeholder="admin or admin@supplychain.com"
                    />
                  </div>
                </div>

                <div>
                  <label htmlFor="login-password" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                    Password
                  </label>
                  <div className="relative">
                    <Lock className="w-4 h-4 text-slate-500 absolute left-3.5 top-3.5" />
                    <input
                      id="login-password"
                      type={showPassword ? 'text' : 'password'}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      required
                      disabled={loading}
                      className="w-full pl-10 pr-10 py-2.5 rounded-lg bg-slate-900/90 border border-slate-700/80 text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500 focus:ring-2 focus:ring-cyan-500/40 transition-all text-xs"
                      placeholder="••••••••"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3 top-3 text-slate-400 hover:text-slate-200 focus:outline-none"
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full py-3 px-4 rounded-lg bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-white font-bold text-xs shadow-lg shadow-cyan-500/25 transition-all flex items-center justify-center space-x-2 disabled:opacity-50 mt-2 cursor-pointer"
                >
                  <span>{loading ? 'Authenticating...' : 'Sign In (Sends Verification Code)'}</span>
                  {!loading && <ArrowRight className="w-4 h-4" />}
                </button>
              </form>
            )}

            {/* STEP 1: REGISTER FORM */}
            {mode === 'register' && (
              <form onSubmit={handleRegisterSubmit} className="space-y-4">
                <div>
                  <label htmlFor="reg-username" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                    Username
                  </label>
                  <div className="relative">
                    <User className="w-4 h-4 text-slate-500 absolute left-3.5 top-3.5" />
                    <input
                      id="reg-username"
                      type="text"
                      value={regUsername}
                      onChange={(e) => setRegUsername(e.target.value)}
                      onBlur={() => setTouched(prev => ({ ...prev, username: true }))}
                      required
                      disabled={loading}
                      className={`w-full pl-10 pr-4 py-2.5 rounded-lg bg-slate-900/90 border text-white placeholder-slate-500 focus:outline-none focus:ring-2 transition-all text-xs ${
                        touched.username && !isUsernameValid(regUsername)
                          ? 'border-rose-500/80 focus:ring-rose-500/40'
                          : 'border-slate-700/80 focus:border-cyan-500 focus:ring-cyan-500/40'
                      }`}
                      placeholder="Choose username (3-50 chars)"
                    />
                  </div>
                  {touched.username && !isUsernameValid(regUsername) && (
                    <p className="text-[11px] text-rose-400 mt-1">Username must be between 3 and 50 characters.</p>
                  )}
                </div>

                <div>
                  <label htmlFor="reg-email" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                    Work Email Address
                  </label>
                  <div className="relative">
                    <Mail className="w-4 h-4 text-slate-500 absolute left-3.5 top-3.5" />
                    <input
                      id="reg-email"
                      type="email"
                      value={regEmail}
                      onChange={(e) => setRegEmail(e.target.value)}
                      onBlur={() => setTouched(prev => ({ ...prev, email: true }))}
                      required
                      disabled={loading}
                      className={`w-full pl-10 pr-4 py-2.5 rounded-lg bg-slate-900/90 border text-white placeholder-slate-500 focus:outline-none focus:ring-2 transition-all text-xs ${
                        touched.email && !isEmailValid(regEmail)
                          ? 'border-rose-500/80 focus:ring-rose-500/40'
                          : 'border-slate-700/80 focus:border-cyan-500 focus:ring-cyan-500/40'
                      }`}
                      placeholder="name@company.com"
                    />
                  </div>
                  {touched.email && !isEmailValid(regEmail) && (
                    <p className="text-[11px] text-rose-400 mt-1">Please enter a valid email address.</p>
                  )}
                </div>

                <div>
                  <label htmlFor="reg-password" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                    Password
                  </label>
                  <div className="relative">
                    <Lock className="w-4 h-4 text-slate-500 absolute left-3.5 top-3.5" />
                    <input
                      id="reg-password"
                      type={showPassword ? 'text' : 'password'}
                      value={regPassword}
                      onChange={(e) => setRegPassword(e.target.value)}
                      onBlur={() => setTouched(prev => ({ ...prev, password: true }))}
                      required
                      disabled={loading}
                      className={`w-full pl-10 pr-10 py-2.5 rounded-lg bg-slate-900/90 border text-white placeholder-slate-500 focus:outline-none focus:ring-2 transition-all text-xs ${
                        touched.password && !isPasswordValid(regPassword)
                          ? 'border-rose-500/80 focus:ring-rose-500/40'
                          : 'border-slate-700/80 focus:border-cyan-500 focus:ring-cyan-500/40'
                      }`}
                      placeholder="At least 6 characters"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3 top-3 text-slate-400 hover:text-slate-200 focus:outline-none"
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                  {touched.password && !isPasswordValid(regPassword) && (
                    <p className="text-[11px] text-rose-400 mt-1">Password must be at least 6 characters long.</p>
                  )}
                </div>

                <div>
                  <label htmlFor="reg-company" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                    Company / Organization Name <span className="text-slate-500 font-normal normal-case">(optional)</span>
                  </label>
                  <div className="relative">
                    <Building2 className="w-4 h-4 text-slate-500 absolute left-3.5 top-3.5" />
                    <input
                      id="reg-company"
                      type="text"
                      value={regCompanyName}
                      onChange={(e) => setRegCompanyName(e.target.value)}
                      disabled={loading}
                      className="w-full pl-10 pr-4 py-2.5 rounded-lg bg-slate-900/90 border border-slate-700/80 focus:border-cyan-500 focus:ring-cyan-500/40 text-white placeholder-slate-500 focus:outline-none focus:ring-2 transition-all text-xs"
                      placeholder="e.g. Apex Global Logistics (creates enterprise tenant)"
                    />
                  </div>
                  <p className="text-[11px] text-slate-400 mt-1">
                    Creates a new dedicated company tenant with Admin access. If blank, creates a private workspace.
                  </p>
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full py-3 px-4 rounded-lg bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-white font-bold text-xs shadow-lg shadow-cyan-500/25 transition-all flex items-center justify-center space-x-2 disabled:opacity-50 mt-2 cursor-pointer"
                >
                  <span>{loading ? 'Creating Account...' : 'Register Account'}</span>
                  {!loading && <UserPlus className="w-4 h-4" />}
                </button>
              </form>
            )}

            {/* Quick Demo Credentials Footer */}
            <div className="mt-6 pt-5 border-t border-slate-800/80 text-center">
              <p className="text-[11px] text-slate-400 mb-2">Click to load demo credentials:</p>
              <div className="flex justify-center space-x-3 text-xs">
                <button
                  type="button"
                  onClick={() => handleSelectDemoUser('admin', 'admin123')}
                  className="px-2.5 py-1 rounded bg-slate-900 border border-slate-800 hover:border-cyan-500/50 text-cyan-400 font-mono text-[11px] transition-colors cursor-pointer"
                >
                  admin / admin123
                </button>
                <span className="text-slate-600 self-center">•</span>
                <button
                  type="button"
                  onClick={() => handleSelectDemoUser('manager', 'manager123')}
                  className="px-2.5 py-1 rounded bg-slate-900 border border-slate-800 hover:border-cyan-500/50 text-cyan-400 font-mono text-[11px] transition-colors cursor-pointer"
                >
                  manager / manager123
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default LoginPage;
