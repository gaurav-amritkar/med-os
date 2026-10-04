import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import useAuthStore from '../store/authStore';
import { authApi } from '../api';
import useToastStore from '../store/toastStore';

export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const login = useAuthStore((s) => s.login);
  const token = useAuthStore((s) => s.token);
  const addToast = useToastStore((s) => s.addToast);
  const navigate = useNavigate();

  const role = useAuthStore((s) => s.role);
  const landing = (role || '').toLowerCase() === 'super_admin' ? '/onboarding' : '/dashboard';

  useEffect(() => {
    if (token) {
      navigate(landing, { replace: true });
    }
  }, [token, landing, navigate]);

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

      <div className="card" style={{ width: '100%', maxWidth: 420, padding: 40 }}>
        <div style={{ textAlign: 'center', marginBottom: 36 }}>
          <div style={{ fontSize: '2.4rem', fontWeight: 800, color: 'var(--action)', letterSpacing: '-1px' }}>
            MED<span style={{ color: 'var(--info)' }}>OS</span>
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--ink-faint)', letterSpacing: '2px', textTransform: 'uppercase', marginTop: 4 }}>
            Hospital Management System v3.0
          </div>
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
            className="btn-primary"
            style={{
              width: '100%',
              justifyContent: 'center',
              padding: 14,
              fontSize: '1rem',
              marginTop: 8,
            }}
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
