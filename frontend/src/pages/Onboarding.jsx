import { useState, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { onboardingApi } from '../api';
import useToastStore from '../store/toastStore';

const TENANT_TYPES = [
  { value: 'HOSPITAL', label: 'Hospital' },
  { value: 'CLINIC', label: 'Clinic' },
  { value: 'INDIVIDUAL_PRACTITIONER', label: 'Individual Practitioner' },
  { value: 'PHARMACY', label: 'Pharmacy' },
];

const FEATURE_OPTIONS = [
  { value: 'encounters', label: 'Encounters & appointments', hint: 'OPD desk, consultations, visit notes' },
  { value: 'admissions', label: 'Admissions & wards', hint: 'Inpatient beds, ward rounds, discharge' },
  { value: 'pharmacy', label: 'Pharmacy', hint: 'Dispensing, stock, purchase orders' },
  { value: 'billing', label: 'Billing', hint: 'Invoices, payments, ledgers' },
];

const initial = {
  name: '',
  type: 'HOSPITAL',
  slug: '',
  contactEmail: '',
  contactPhone: '',
  address: '',
  features: FEATURE_OPTIONS.map((f) => f.value),
  adminUsername: '',
  adminPassword: '',
  adminEmail: '',
  adminFullName: '',
};

const fmt = {
  label: { fontSize: '0.8rem', fontWeight: 600, color: 'var(--ink-muted)', marginBottom: 6, display: 'block' },
  input: {
    width: '100%',
    padding: '10px 12px',
    borderRadius: 'var(--r-sm)',
    border: '1px solid var(--line)',
    background: 'var(--surface)',
    color: 'var(--ink)',
    fontSize: '0.9rem',
    outline: 'none',
  },
  card: {
    width: 'min(620px, 92vw)',
    margin: '48px auto',
    padding: 28,
    borderRadius: 'var(--r-md)',
    background: 'var(--surface)',
    border: '1px solid var(--line)',
    boxShadow: 'var(--shadow-1)',
  },
  heading: { margin: 0, fontSize: '1.5rem', fontWeight: 700, color: 'var(--ink)' },
  sub: { margin: '6px 0 24px', fontSize: '0.85rem', color: 'var(--ink-muted)' },
  grid: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 },
  full: { gridColumn: '1 / -1' },
  section: { marginTop: 24, paddingTop: 18, borderTop: '1px solid var(--line)' },
};

/**
 * A labelled input.
 *
 * The label is bound to the control with htmlFor/id. Without that the fields
 * had no accessible name at all: a screen reader announced "edit text" for
 * every field, and the form could not be driven by its labels.
 */
let fieldSeq = 0;
function Field({ label, grid, ...rest }) {
  const id = useMemo(() => `onboarding-field-${(fieldSeq += 1)}`, []);
  return (
    <div style={grid ? { ...fmt.grid, gridColumn: '1 / -1' } : {}}>
      <label style={fmt.label} htmlFor={id}>
        {label}
      </label>
      <input id={id} style={fmt.input} {...rest} />
    </div>
  );
}

export default function Onboarding() {
  const [form, setForm] = useState(initial);
  const [loading, setLoading] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const addToast = useToastStore((s) => s.addToast);
  const navigate = useNavigate();

  const set =
    (key) =>
    (e) => {
      const { value, type, checked } = e.target;
      setForm((f) => ({ ...f, [key]: type === 'checkbox' ? checked : value }));
    };

  const toggleFeature =
    (value) => () =>
      setForm((f) => ({
        ...f,
        features: f.features.includes(value)
          ? f.features.filter((v) => v !== value)
          : [...f.features, value],
      }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (form.features.length === 0) {
      setSubmitError('Select at least one module before submitting.');
      return;
    }
    setLoading(true);
    setSubmitError(null);
    try {
      await onboardingApi.registerTenant(form);
      addToast('Tenant registered. Sign in with the new admin account.', 'success');
      navigate('/login');
    } catch (err) {
      const status = err.response?.status;
      if (status === 401 || status === 403) {
        // The endpoint is gated behind medos.onboarding.public-signup, which
        // defaults to false. Surface the real reason instead of the raw
        // "Authentication required", which reads as a broken form.
        setSubmitError(
          'Self-serve registration is disabled on this deployment. Ask your platform administrator to create this organisation, or sign in with an existing account.'
        );
        addToast('Registration is disabled on this deployment', 'critical');
      } else {
        const message = err.response?.data?.message || 'Registration failed';
        setSubmitError(message);
        addToast(message, 'critical');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        background: 'var(--canvas)',
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
              background: 'var(--action)',
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

        <div
          role="status"
          style={{
            margin: '0 0 20px',
            padding: '12px 14px',
            borderRadius: 'var(--r-sm)',
            border: '1px solid var(--line)',
            background: 'var(--sunken)',
            fontSize: '0.85rem',
            color: 'var(--ink-muted)',
            lineHeight: 1.5,
          }}
        >
          Self-serve registration is disabled by default. Ask your platform
          administrator to create this organisation for you, or sign in with an
          account you already have. If your administrator has enabled
          registration for this deployment, the form below will submit normally.
        </div>

        {submitError && (
          <div
            role="alert"
            style={{
              margin: '0 0 20px',
              padding: '12px 14px',
              borderRadius: 'var(--r-sm)',
              border: '1px solid var(--critical)',
              color: 'var(--critical)',
              fontSize: '0.85rem',
              lineHeight: 1.5,
            }}
          >
            {submitError}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div style={fmt.section}>
            <div style={fmt.grid}>
              <Field label="Facility Name" value={form.name} onChange={set('name')} required />
              <div>
                <label style={fmt.label} htmlFor="onboarding-field-type">Facility Type</label>
                <select id="onboarding-field-type" style={fmt.input} value={form.type} onChange={set('type')}>
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
            <h2 style={{ fontSize: '1rem', margin: '0 0 14px', color: 'var(--ink)' }}>Enabled Modules</h2>
            <p style={{ fontSize: '0.85rem', color: 'var(--ink-muted)', margin: '0 0 12px' }}>
              Only the modules you switch on here are visible and reachable for this organisation. At least one is required.
            </p>
            <div style={{ display: 'grid', gap: 8 }}>
              {FEATURE_OPTIONS.map((f) => {
                const id = `onboarding-feature-${f.value}`;
                const checked = form.features.includes(f.value);
                return (
                  <label
                    key={f.value}
                    htmlFor={id}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      padding: '10px 12px',
                      border: `1px solid ${checked ? 'var(--action)' : 'var(--ink-faint)'}`,
                      borderRadius: 'var(--r-sm)',
                      background: checked ? 'var(--canvas)' : 'transparent',
                      fontSize: '0.9rem',
                      cursor: 'pointer',
                    }}
                  >
                    <input
                      id={id}
                      type="checkbox"
                      checked={checked}
                      onChange={() => toggleFeature(f.value)}
                    />
                    <span>
                      {f.label}
                      <span style={{ display: 'block', fontSize: '0.78rem', color: 'var(--ink-muted)' }}>
                        {f.hint}
                      </span>
                    </span>
                  </label>
                );
              })}
            </div>
            {form.features.length === 0 && (
              <p role="alert" style={{ fontSize: '0.82rem', color: 'var(--critical)', margin: '10px 0 0' }}>
                Select at least one module.
              </p>
            )}
          </div>

          <div style={fmt.section}>
            <h2 style={{ fontSize: '1rem', margin: '0 0 14px', color: 'var(--ink)' }}>First Admin Account</h2>
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
              borderRadius: 'var(--r-sm)',
              border: 'none',
              background: loading ? 'var(--ink-faint)' : 'var(--action)',
              color: 'white',
              fontSize: '0.95rem',
              fontWeight: 600,
              cursor: loading ? 'default' : 'pointer',
            }}
          >
            {loading ? 'Submitting...' : 'Register Facility →'}
          </button>
        </form>

        <p style={{ marginTop: 20, textAlign: 'center', fontSize: '0.85rem', color: 'var(--ink-faint)' }}>
          Already have an account?{' '}
          <Link to="/login" style={{ color: 'var(--action)', fontWeight: 600 }}>
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}