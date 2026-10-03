import { Link, useLocation } from 'react-router-dom';
import useAuthStore from '../store/authStore';
import Icon from './icons';

const navItems = {
  admin: [
    { to: '/', label: 'Dashboard', icon: 'dashboard' },
    { to: '/patients', label: 'Patients', icon: 'patients' },
    { to: '/encounters', label: 'OPD', icon: 'encounters' },
    { to: '/admissions', label: 'IPD / Wards', icon: 'admissions' },
    { to: '/pharmacy', label: 'Pharmacy', icon: 'pharmacy' },
    { to: '/billing', label: 'Billing', icon: 'billing' },
  ],
  doctor: [
    { to: '/', label: 'Dashboard', icon: 'dashboard' },
    { to: '/patients', label: 'Patients', icon: 'patients' },
    { to: '/encounters', label: 'OPD', icon: 'encounters' },
    { to: '/admissions', label: 'IPD / Wards', icon: 'admissions' },
    { to: '/pharmacy', label: 'Pharmacy', icon: 'pharmacy' },
  ],
  nurse: [
    { to: '/', label: 'Dashboard', icon: 'dashboard' },
    { to: '/patients', label: 'Patients', icon: 'patients' },
    { to: '/encounters', label: 'Encounters', icon: 'encounters' },
    { to: '/admissions', label: 'Admissions', icon: 'admissions' },
  ],
  receptionist: [
    { to: '/', label: 'Dashboard', icon: 'dashboard' },
    { to: '/patients', label: 'Patients', icon: 'patients' },
    { to: '/encounters', label: 'Appointments', icon: 'encounters' },
  ],
  pharmacist: [
    { to: '/', label: 'Dashboard', icon: 'dashboard' },
    { to: '/pharmacy', label: 'Pharmacy', icon: 'pharmacy' },
    { to: '/billing', label: 'Ledger', icon: 'billing' },
  ],
  billing: [
    { to: '/', label: 'Dashboard', icon: 'dashboard' },
    { to: '/billing', label: 'Billing', icon: 'billing' },
    { to: '/patients', label: 'Patients', icon: 'patients' },
  ],
};

function ClockWidget() {
  const now = new Date();
  return (
    <div className="rail__foot">
      <div>{now.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })}</div>
      <div className="clock-widget__time">{now.toLocaleTimeString('en-IN')}</div>
    </div>
  );
}

export default function Sidebar({ isOpen, onClose }) {
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const role = user?.role?.toLowerCase() || 'admin';
  const items = navItems[role] || navItems.admin;

  return (
    <>
      {isOpen && (
        <div className="sidebar-overlay" onClick={onClose} aria-hidden="true" />
      )}

      <aside className={`rail ${isOpen ? 'open' : ''}`}>
        <div className="rail__brand">
          <div>
            <div className="rail__wordmark">
              MED<span>OS</span>
            </div>
            <div className="rail__version">HMS v3.0</div>
          </div>

          <button
            type="button"
            className="btn-secondary btn-sm btn-icon sidebar-close"
            onClick={onClose}
            aria-label="Close navigation"
          >
            <Icon name="close" size={18} />
          </button>
        </div>

        <nav className="rail__nav" aria-label="Primary">
          {items.map((item) => {
            const active = location.pathname === item.to;
            return (
              <Link
                key={item.to}
                to={item.to}
                onClick={onClose}
                className="rail-item"
                aria-current={active ? 'page' : undefined}
              >
                <Icon name={item.icon} size={20} />
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
