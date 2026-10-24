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
      <div style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--canvas)',
      }}>
        <div className="card login-card" style={{ textAlign: 'center' }}>
          <div className="login-header">
            <div className="login-wordmark">
              MED<span>OS</span>
            </div>
            <div className="login-subtitle">Hospital Management System v3.0</div>
          </div>

          <p style={{ fontSize: '0.95rem', color: 'var(--ink)', margin: '0 0 6px' }}>
            You are already signed in{usernameInSession ? ` as ${usernameInSession}` : ''}.
          </p>
          <p style={{ fontSize: '0.85rem', color: 'var(--ink-muted)', margin: '0 0 20px' }}>
            Continue to where you were, or sign out to use a different account.
          </p>

          <div style={{ display: 'flex', gap: 10, justifyContent: 'center' }}>
            <Link to={landing} className="btn-primary">Continue</Link>
            <button
              type="button"
              className="btn-ghost"
              onClick={() => {
                logout();
                setUsername('');
                setPassword('');
              }}
            >
              Sign out
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
        userId: data.userId,
        username: data.username,
        fullName: data.fullName,
        role: data.role,
        specialization: data.specialization,
        tenantId: data.tenantId,
        tenantName: data.tenantName,
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
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'var(--canvas)',
      position: 'relative',
      overflow: 'hidden',
    }}>
      <div style={{
        position: 'absolute',
        top: '-20%',
        left: '-10%',
        width: '500px',
        height: '500px',
        background: 'radial-gradient(circle, var(--info-tint), transparent 70%)',
        borderRadius: '50%',
        pointerEvents: 'none',
      }} />
      <div style={{
        position: 'absolute',
        bottom: '-20%',
        right: '-10%',
        width: '400px',
        height: '400px',
        background: 'radial-gradient(circle, var(--info-tint), transparent 70%)',
        borderRadius: '50%',
        pointerEvents: 'none',
      }} />

      <div className="card login-card">
        <div className="login-header">
          <div className="login-wordmark">
            MED<span>OS</span>
          </div>
          <div className="login-subtitle">Hospital Management System v3.0</div>
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
              autoFocus
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

        <div style={{
          marginTop: 28,
          padding: 16,
          borderRadius: 'var(--r-sm)',
          background: 'var(--sunken)',
          fontSize: '0.75rem',
          color: 'var(--ink-faint)',
          lineHeight: 1.7,
        }}>
          <strong style={{ color: 'var(--ink-muted)' }}>Local Login Setup:</strong><br />
          Fresh database: sign in as <strong style={{ color: 'var(--ink-muted)' }}>admin</strong> with your
          <strong style={{ color: 'var(--ink-muted)' }}> BOOTSTRAP_ADMIN_PASSWORD</strong>.<br />
          Demo users: run <code>./tools/seed-dev.sh</code>, then use admin/doctor/nurse/reception/pharmacy/billing with password <strong style={{ color: 'var(--ink-muted)' }}>password</strong>.
        </div>
      </div>
    </div>
  );
}
