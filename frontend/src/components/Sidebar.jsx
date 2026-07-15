import { Link, useLocation } from 'react-router-dom';
import useAuthStore from '../store/authStore';

const navItems = {
  admin: [
    { to: '/', label: 'Dashboard', icon: '◉' },
    { to: '/patients', label: 'Patients', icon: '◈' },
    { to: '/encounters', label: 'OPD', icon: '◎' },
    { to: '/admissions', label: 'IPD / Wards', icon: '▣' },
    { to: '/pharmacy', label: 'Pharmacy', icon: '⬡' },
    { to: '/billing', label: 'Billing', icon: '₿' },
  ],
  doctor: [
    { to: '/', label: 'Dashboard', icon: '◉' },
    { to: '/patients', label: 'Patients', icon: '◈' },
    { to: '/encounters', label: 'OPD', icon: '◎' },
    { to: '/admissions', label: 'IPD / Wards', icon: '▣' },
    { to: '/pharmacy', label: 'Pharmacy', icon: '⬡' },
  ],
  nurse: [
    { to: '/', label: 'Dashboard', icon: '◉' },
    { to: '/patients', label: 'Patients', icon: '◈' },
    { to: '/encounters', label: 'Encounters', icon: '◎' },
    { to: '/admissions', label: 'Admissions', icon: '▣' },
  ],
  receptionist: [
    { to: '/', label: 'Dashboard', icon: '◉' },
    { to: '/patients', label: 'Patients', icon: '◈' },
    { to: '/encounters', label: 'Appointments', icon: '◎' },
  ],
  pharmacist: [
    { to: '/', label: 'Dashboard', icon: '◉' },
    { to: '/pharmacy', label: 'Pharmacy', icon: '⬡' },
    { to: '/billing', label: 'Ledger', icon: '₿' },
  ],
  billing: [
    { to: '/', label: 'Dashboard', icon: '◉' },
    { to: '/billing', label: 'Billing', icon: '₿' },
    { to: '/patients', label: 'Patients', icon: '◈' },
  ],
};

function ClockWidget() {
  const now = new Date();
  return (
    <div className="clock-widget" style={{ padding: '16px 20px', borderTop: '1px solid var(--border)', marginTop: 'auto', fontSize: '0.8rem', color: 'var(--text-dim)' }}>
      <div>{now.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })}</div>
      <div style={{ fontFamily: 'monospace', marginTop: 2 }}>{now.toLocaleTimeString('en-IN')}</div>
    </div>
  );
}

export default function Sidebar({ isOpen, onClose }) {
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const role = user?.role?.toLowerCase() || 'admin';
  const items = navItems[role] || navItems.admin;

  const handleNav = () => {
    if (onClose) onClose();
  };

  return (
    <>
      {/* Mobile overlay */}
      {isOpen && (
        <div
          className="sidebar-overlay"
          onClick={onClose}
          aria-hidden="true"
        />
      )}
      <aside className={`sidebar ${isOpen ? 'open' : ''}`}>
        <div style={{ padding: '0 20px 24px', borderBottom: '1px solid var(--border)', marginBottom: 16, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <div style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--primary)', letterSpacing: '-1px' }}>
              MED<span style={{ color: '#22d3ee' }}>OS</span>
            </div>
            <div style={{ fontSize: '0.7rem', color: 'var(--text-dim)', letterSpacing: '1px', textTransform: 'uppercase', marginTop: 2 }}>
              HMS v3.0
            </div>
          </div>
          {/* Close button — mobile only */}
          <button
            className="btn-ghost btn-sm sidebar-close"
            onClick={onClose}
            aria-label="Close menu"
          >
            ✕
          </button>
        </div>

        <nav style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2, padding: '0 12px' }}>
          {items.map((item) => {
            const active = location.pathname === item.to;
            return (
              <Link
                key={item.to}
                to={item.to}
                onClick={handleNav}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: '11px 14px',
                  borderRadius: 'var(--radius-sm)',
                  color: active ? 'var(--text-white)' : 'var(--text-muted)',
                  background: active ? 'rgba(99, 102, 241, 0.1)' : 'transparent',
                  transition: 'all 0.2s',
                  fontSize: '0.9rem',
                  fontWeight: active ? 600 : 400,
                  textDecoration: 'none',
                  minHeight: 44,
                }}
              >
                <span style={{ fontSize: '1.1rem', opacity: 0.7 }}>{item.icon}</span>
                {item.label}
              </Link>
            );
          })}
        </nav>

        <ClockWidget />
      </aside>
    </>
  );
}
