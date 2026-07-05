import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import useAuthStore from '../store/authStore';
import { authApi } from '../api';
import useToastStore from '../store/toastStore';

export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const login = useAuthStore((s) => s.login);
  const addToast = useToastStore((s) => s.addToast);
  const navigate = useNavigate();

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
      });
      addToast(`Welcome back, ${data.fullName}`, 'success');
      navigate('/');
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
      background: 'var(--bg-deep)',
      position: 'relative',
      overflow: 'hidden',
    }}>
      <div style={{
        position: 'absolute',
        top: '-20%',
        left: '-10%',
        width: '500px',
        height: '500px',
        background: 'radial-gradient(circle, rgba(99,102,241,0.15), transparent 70%)',
        borderRadius: '50%',
        pointerEvents: 'none',
      }} />
      <div style={{
        position: 'absolute',
        bottom: '-20%',
        right: '-10%',
        width: '400px',
        height: '400px',
        background: 'radial-gradient(circle, rgba(34,211,238,0.1), transparent 70%)',
        borderRadius: '50%',
        pointerEvents: 'none',
      }} />

      <div className="card" style={{ width: '100%', maxWidth: 420, padding: 40 }}>
        <div style={{ textAlign: 'center', marginBottom: 36 }}>
          <div style={{ fontSize: '2.4rem', fontWeight: 800, color: 'var(--primary)', letterSpacing: '-1px' }}>
            MED<span style={{ color: '#22d3ee' }}>OS</span>
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', letterSpacing: '2px', textTransform: 'uppercase', marginTop: 4 }}>
            Hospital Management System v3.0
          </div>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Username</label>
            <input
              type="text"
              placeholder="Enter username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              autoFocus
            />
          </div>
          <div className="form-group">
            <label>Password</label>
            <input
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
          borderRadius: 'var(--radius-sm)',
          background: 'var(--surface-2)',
          fontSize: '0.75rem',
          color: 'var(--text-dim)',
          lineHeight: 1.7,
        }}>
          <strong style={{ color: 'var(--text-muted)' }}>Demo Accounts:</strong><br />
          admin / doctor / nurse / reception / pharmacy / billing<br />
          <span style={{ color: 'var(--text-dim)' }}>Password: <strong style={{ color: 'var(--text-muted)' }}>password</strong></span>
        </div>
      </div>
    </div>
  );
}
