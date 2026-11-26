import { useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';
import useAuthStore from '../store/authStore';
import { authApi } from '../api';
import Icon from './icons';

const navItems = {
  super_admin: [
    { to: '/onboarding', label: 'Register Hospital', icon: 'user' },
  ],
  admin: [
    { to: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { to: '/patients', label: 'Patients', icon: 'patients' },
    { to: '/enquiries', label: 'Enquiry Desk', icon: 'enquiry' },
    { to: '/appointments', label: 'Appointments', icon: 'encounters' },
    { to: '/appointments/queue', label: 'Token Queue', icon: 'encounters' },
    { to: '/encounters', feature: 'encounters', label: 'OPD', icon: 'encounters' },
    { to: '/admissions', feature: 'admissions', label: 'IPD / Wards', icon: 'admissions' },
    { to: '/pharmacy', feature: 'pharmacy', label: 'Pharmacy', icon: 'pharmacy' },
    { to: '/lab-orders', label: 'Lab Orders', icon: 'laborders' },
    { to: '/billing', feature: 'billing', label: 'Billing', icon: 'billing' },
    { to: '/staff', label: 'Staff', icon: 'user' },
  ],
  doctor: [
    { to: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { to: '/patients', label: 'Patients', icon: 'patients' },
    { to: '/encounters', feature: 'encounters', label: 'OPD', icon: 'encounters' },
    { to: '/admissions', feature: 'admissions', label: 'IPD / Wards', icon: 'admissions' },
    { to: '/pharmacy', feature: 'pharmacy', label: 'Pharmacy', icon: 'pharmacy' },
    { to: '/lab-orders', label: 'Lab Orders', icon: 'laborders' },
    { to: '/appointments', label: 'Appointments', icon: 'encounters' },
    { to: '/appointments/queue', label: 'Token Queue', icon: 'encounters' },
  ],
  nurse: [
    { to: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { to: '/patients', label: 'Patients', icon: 'patients' },
    { to: '/encounters', feature: 'encounters', label: 'Encounters', icon: 'encounters' },
    { to: '/admissions', feature: 'admissions', label: 'Admissions', icon: 'admissions' },
    { to: '/lab-orders', label: 'Lab Orders', icon: 'laborders' },
    { to: '/appointments', label: 'Appointments', icon: 'encounters' },
    { to: '/appointments/queue', label: 'Token Queue', icon: 'encounters' },
  ],
  receptionist: [
    { to: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { to: '/patients', label: 'Patients', icon: 'patients' },
    { to: '/enquiries', label: 'Enquiry Desk', icon: 'enquiry' },
    { to: '/appointments', label: 'Appointments', icon: 'encounters' },
    { to: '/appointments/queue', label: 'Token Queue', icon: 'encounters' },
  ],
  pharmacist: [
    { to: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { to: '/pharmacy', feature: 'pharmacy', label: 'Pharmacy', icon: 'pharmacy' },
  ],
  billing: [
    { to: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { to: '/billing', feature: 'billing', label: 'Billing', icon: 'billing' },
    { to: '/patients', label: 'Patients', icon: 'patients' },
  ],
  lab: [
    { to: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { to: '/lab-orders', label: 'Lab Orders', icon: 'laborders' },
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
  const user = useAuthStore((s) => s.user);
  const role = user?.role?.toLowerCase();
  const [tenantFeatures, setTenantFeatures] = useState(null);


  useEffect(() => {
    let cancelled = false;
    authApi.getMe()
      .then(({ data }) => {
        if (!cancelled) {
          if (Array.isArray(data?.features)) setTenantFeatures(data.features);
        }
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [role]);

  const items = navItems[role] || [];
  const visibleItems = tenantFeatures === null
    ? items
    : items.filter(item => !item.feature || tenantFeatures.includes(item.feature));

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
          {visibleItems.map(item => (
            <NavLink key={item.to} to={item.to} end={item.to !== '/patients'} className="rail-item" onClick={onClose}>
              <Icon name={item.icon} size={20} />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <ClockWidget />
      </aside>
    </>
  );
}
