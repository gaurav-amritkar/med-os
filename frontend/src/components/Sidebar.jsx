import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import useAuthStore from '../store/authStore';
import { authApi } from '../api';
import Icon from './icons';

const navItems = {
  super_admin: [
    { to: '/onboarding', label: 'Register Hospital', icon: 'user' },
  ],
  admin: [
    { to: '/', label: 'Dashboard', icon: 'dashboard' },
    { to: '/patients', label: 'Patients', icon: 'patients' },
    { to: '/encounters', feature: 'encounters', label: 'OPD', icon: 'encounters' },
    { to: '/admissions', feature: 'admissions', label: 'IPD / Wards', icon: 'admissions' },
    { to: '/pharmacy', feature: 'pharmacy', label: 'Pharmacy', icon: 'pharmacy' },
    { to: '/billing', feature: 'billing', label: 'Billing', icon: 'billing' },
    { to: '/staff', label: 'Staff', icon: 'user' },
  ],
  doctor: [
    { to: '/', label: 'Dashboard', icon: 'dashboard' },
    { to: '/patients', label: 'Patients', icon: 'patients' },
    { to: '/encounters', feature: 'encounters', label: 'OPD', icon: 'encounters' },
    { to: '/admissions', feature: 'admissions', label: 'IPD / Wards', icon: 'admissions' },
    { to: '/pharmacy', feature: 'pharmacy', label: 'Pharmacy', icon: 'pharmacy' },
  ],
  nurse: [
    { to: '/', label: 'Dashboard', icon: 'dashboard' },
    { to: '/patients', label: 'Patients', icon: 'patients' },
    { to: '/encounters', feature: 'encounters', label: 'Encounters', icon: 'encounters' },
    { to: '/admissions', feature: 'admissions', label: 'Admissions', icon: 'admissions' },
  ],
  receptionist: [
    { to: '/', label: 'Dashboard', icon: 'dashboard' },
    { to: '/patients', label: 'Patients', icon: 'patients' },
    { to: '/encounters', feature: 'encounters', label: 'Appointments', icon: 'encounters' },
  ],
  pharmacist: [
    { to: '/', label: 'Dashboard', icon: 'dashboard' },
    { to: '/pharmacy', feature: 'pharmacy', label: 'Pharmacy', icon: 'pharmacy' },
    { to: '/billing', feature: 'billing', label: 'Ledger', icon: 'billing' },
  ],
  billing: [
    { to: '/', label: 'Dashboard', icon: 'dashboard' },
    { to: '/billing', feature: 'billing', label: 'Billing', icon: 'billing' },
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
const [tenantFeatures, setTenantFeatures] = useState(null);
  useEffect(() => {
    let cancelled = false;
    authApi.getMe()
      .then(({ data }) => {
        if (!cancelled && Array.isArray(data?.features)) setTenantFeatures(data.features);
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [role]);
  const items = navItems[role] || navItems.admin;
  const visibleItems = tenantFeatures === null
    ? items
    : items.filter((item) => !item.feature || tenantFeatures.includes(item.feature));

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
          {visibleItems.map((item) => {
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
