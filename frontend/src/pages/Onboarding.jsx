import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { onboardingApi } from '../api';
import useToastStore from '../store/toastStore';

const TENANT_TYPES = [
  { value: 'HOSPITAL', label: 'Hospital' },
  { value: 'CLINIC', label: 'Clinic' },
  { value: 'INDIVIDUAL_PRACTITIONER', label: 'Individual Practitioner' },
  { value: 'PHARMACY', label: 'Pharmacy' },
];

const initial = {
  name: '',
  type: 'HOSPITAL',
  slug: '',
  contactEmail: '',
  contactPhone: '',
  address: '',
  adminUsername: '',
  adminPassword: '',
  adminEmail: '',
  adminFullName: '',
};

const fmt = {
  label: { fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: 6, display: 'block' },
  input: {
    width: '100%',
    padding: '10px 12px',
    borderRadius: 'var(--radius-sm)',
    border: '1px solid var(--border)',
    background: 'var(--surface-solid)',
    color: 'var(--text)',
    fontSize: '0.9rem',
    outline: 'none',
  },
  card: {
    width: 'min(620px, 92vw)',
    margin: '48px auto',
    padding: 28,
    borderRadius: 'var(--radius-md)',
    background: 'var(--surface-solid)',
    border: '1px solid var(--border)',
    boxShadow: 'var(--shadow-sm)',
  },
  heading: { margin: 0, fontSize: '1.5rem', fontWeight: 700, color: 'var(--text)' },
  sub: { margin: '6px 0 24px', fontSize: '0.85rem', color: 'var(--text-muted)' },
  grid: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 },
  full: { gridColumn: '1 / -1' },
  section: { marginTop: 24, paddingTop: 18, borderTop: '1px solid var(--border)' },
};

function Field({ label, grid, ...rest }) {
  return (
    <div style={grid ? { ...fmt.grid, gridColumn: '1 / -1' } : {}}>
      <label style={fmt.label}>{label}</label>
      <input style={fmt.input} {...rest} />
    </div>
  );
}

export default function Onboarding() {
  const [form, setForm] = useState(initial);
  const [loading, setLoading] = useState(false);
  const addToast = useToastStore((s) => s.addToast);
  const navigate = useNavigate();

  const set =
    (key) =>
    (e) => {
      const { value, type, checked } = e.target;
      setForm((f) => ({ ...f, [key]: type === 'checkbox' ? checked : value }));
    };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      await onboardingApi.registerTenant(form);
      addToast('Tenant registered. Sign in with the new admin account.', 'success');
      navigate('/login');
    } catch (err) {
      addToast(err.response?.data?.message || 'Registration failed', 'critical');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        background: 'var(--bg-deep)',
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'center',
        padding: '32px 16px',
      }}
    >
      <div style={fmt.card}>
        <div style={{ textAlign: 'center' }}>
          <div
            style={{
              width: 52,
              height: 52,
              margin: '0 auto 14px',
              borderRadius: 14,
              background: 'var(--primary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '1.5rem',
              color: 'white',
            }}
          >
            ➕
          </div>
          <h1 style={fmt.heading}>Register Your Facility</h1>
          <p style={fmt.sub}>Create a new hospital/clinic organisation and its first admin account.</p>
        </div>

        <form onSubmit={handleSubmit}>
          <div style={fmt.section}>
            <div style={fmt.grid}>
              <Field label="Facility Name" value={form.name} onChange={set('name')} required />
              <div>
                <label style={fmt.label}>Facility Type</label>
                <select style={fmt.input} value={form.type} onChange={set('type')}>
                  {TENANT_TYPES.map((t) => (
                    <option key={t.value} value={t.value}>
                      {t.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div style={fmt.grid}>
              <Field label="URL Slug (lowercase, unique)" value={form.slug} onChange={set('slug')} required placeholder="my-hospital" />
              <Field label="Contact Email" type="email" value={form.contactEmail} onChange={set('contactEmail')} required />
              <Field label="Contact Phone" value={form.contactPhone} onChange={set('contactPhone')} required />
              <Field label="Address" value={form.address} onChange={set('address')} />
            </div>
          </div>

          <div style={fmt.section}>
            <h2 style={{ fontSize: '1rem', margin: '0 0 14px', color: 'var(--text)' }}>First Admin Account</h2>
            <div style={fmt.grid}>
              <Field label="Username" value={form.adminUsername} onChange={set('adminUsername')} required />
              <Field label="Full Name" value={form.adminFullName} onChange={set('adminFullName')} />
              <Field label="Admin Email" type="email" value={form.adminEmail} onChange={set('adminEmail')} required />
              <Field label="Password" type="password" value={form.adminPassword} onChange={set('adminPassword')} required minLength={8} />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            style={{
              marginTop: 24,
              width: '100%',
              padding: '12px 16px',
              borderRadius: 'var(--radius-sm)',
              border: 'none',
              background: loading ? 'var(--text-dim)' : 'var(--primary)',
              color: 'white',
              fontSize: '0.95rem',
              fontWeight: 600,
              cursor: loading ? 'default' : 'pointer',
            }}
          >
            {loading ? 'Submitting...' : 'Register Facility →'}
          </button>
        </form>

        <p style={{ marginTop: 20, textAlign: 'center', fontSize: '0.85rem', color: 'var(--text-dim)' }}>
          Already have an account?{' '}
          <Link to="/login" style={{ color: 'var(--primary)', fontWeight: 600 }}>
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}