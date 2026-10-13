import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import useAuthStore from '../store/authStore';
import { authApi } from '../api';
import useToastStore from '../store/toastStore';

export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const login = useAuthStore((s) => s.login);
  const logout = useAuthStore((s) => s.logout);
  const token = useAuthStore((s) => s.token);
  const addToast = useToastStore((s) => s.addToast);
  const navigate = useNavigate();

  const role = useAuthStore((s) => s.role);
  const usernameInSession = useAuthStore((s) => s.username);
  const landing = (role || '').toLowerCase() === 'super_admin' ? '/onboarding' : '/dashboard';

  // An authenticated visitor who deliberately came here is told so and offered
  // a way forward, rather than being redirected on mount.
  //
  // That redirect used to be unconditional, and for a super_admin the
  // destination was /onboarding — the page that carries the "Sign in" link. So
  // clicking it returned them to where they started and the link read as dead.
  // Silent redirection is also wrong on its own terms: someone opening the
  // sign-in page to switch accounts should not be thrown somewhere they did
  // not ask for.
  if (token) {
    return (
      <div className="login-page logged-in-view">
        <div className="card login-card">
          <div className="login-header">
            <div className="login-wordmark">
              MED<span>OS</span>
            </div>
            <div className="login-subtitle">
              Hospital Management System v3.0
            </div>
          </div>

          <p style={{ fontSize: '0.95rem', color: 'var(--ink)', margin: '0 0 6px' }}>
            You are already signed in as <strong>{usernameInSession || 'user'}</strong>.
          </p>
          <p style={{ fontSize: '0.95rem', color: 'var(--ink-faint)', margin: '0 0 16px' }}>
            Your role: <strong>{role || 'unknown'}</strong>
          </p>

          <div style={{ display: 'flex', gap: '12px', justifyContent: 'center', flexWrap: 'wrap' }}>
            <Link to={landing} className="btn-primary" style={{ textDecoration: 'none' }}>
              Continue to {role === 'SUPER_ADMIN' ? 'Onboarding' : 'Dashboard'}
            </Link>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => logout()}
              style={{ textDecoration: 'none' }}
            >
              Sign out to switch accounts
            </button>
          </div>
        </div>
      </div>
    );
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      const { data } = await authApi.login({ username, password });
      login(data.token, {
        expiresIn: data.expiresIn,
        userId: data.userId,
        fullName: data.fullName,
        username: data.username,
        role: data.role,
      }, data.expiresIn);
      addToast(`Welcome back, ${data.fullName}`, 'success');
      navigate((data.role || '').toLowerCase() === 'super_admin' ? '/onboarding' : '/dashboard', { replace: true });
    } catch (err) {
      addToast(err.response?.data?.message || 'Login failed', 'critical');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      {/* Decorative gradient blobs for visual interest */}
      <div className="login-blob login-blob-top-left" />
      <div className="login-blob login-blob-bottom-right" />

      <div className="card login-card">
        <div className="login-header">
          <div className="login-wordmark">
            MED<span>OS</span>
          </div>
          <div className="login-subtitle">
            Hospital Management System v3.0
          </div>
        </div>

        <div className="login-info">
          <p className="login-info-paragraph">
            MedOS is an ultra-premium, role-based Hospital Management System designed to
            digitize, streamline, and intelligently assist end-to-end hospital workflows.
          </p>
          <p className="login-info-paragraph">
            From patient registration and clinical documentation to pharmacy dispensing and
            financial reconciliation — all with enterprise-grade security.
          </p>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="username">Username</label>
            <input
              id="username"
              type="text"
              placeholder="Enter username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              placeholder="Enter password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <button
            type="submit"
            className="btn-primary login-submit"
            disabled={loading}
          >
            {loading ? 'Authenticating...' : 'Sign In →'}
          </button>
        </form>

        <div className="login-footer">
          <Link to="/onboarding" className="no-underline">
            Don't have an account? Register hospital (super-admin)
          </Link>
          <span className="ml-auto" style={{ color: 'var(--ink-faint)', fontSize: '0.8125rem' }}>
            © 2026 MedOS HMS. All rights reserved.
          </span>
        </div>
      </div>
    </div>
  );
}