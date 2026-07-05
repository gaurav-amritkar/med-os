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

export default function Sidebar() {
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const role = user?.role?.toLowerCase() || 'admin';
  const items = navItems[role] || navItems.admin;

  return (
    <aside style={{
      position: 'fixed',
      left: 0,
      top: 0,
      bottom: 0,
      width: 'var(--sidebar-width)',
      background: 'rgba(12, 12, 20, 0.95)',
      backdropFilter: 'blur(24px)',
      borderRight: '1px solid var(--border)',
      zIndex: 'var(--z-sidebar)',
      display: 'flex',
      flexDirection: 'column',
      padding: '20px 0',
    }}>
      <div style={{ padding: '0 20px 24px', borderBottom: '1px solid var(--border)', marginBottom: 16 }}>
        <div style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--primary)', letterSpacing: '-1px' }}>
          MED<span style={{ color: 'var(--accent, #22d3ee)' }}>OS</span>
        </div>
        <div style={{ fontSize: '0.7rem', color: 'var(--text-dim)', letterSpacing: '1px', textTransform: 'uppercase', marginTop: 2 }}>
          HMS v3.0
        </div>
      </div>

      <nav style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2, padding: '0 12px' }}>
        {items.map((item) => {
          const active = location.pathname === item.to;
          return (
            <Link
              key={item.to}
              to={item.to}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '10px 14px',
                borderRadius: 'var(--radius-sm)',
                color: active ? 'var(--text-white)' : 'var(--text-muted)',
                background: active ? 'rgba(99, 102, 241, 0.1)' : 'transparent',
                transition: 'all 0.2s',
                fontSize: '0.9rem',
                fontWeight: active ? 600 : 400,
                textDecoration: 'none',
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
  );
}
