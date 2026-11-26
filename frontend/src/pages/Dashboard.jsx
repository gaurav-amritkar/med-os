import { useState, useEffect } from 'react';
import { dashboardApi } from '../api';
import useAuthStore from '../store/authStore';
import { Link } from 'react-router-dom';

function useDashboardResource(load) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState({ loading: true });
  useEffect(() => {
    let current = true;
    load().then(({ data }) => {
      if (current) setResult({ data });
    }).catch(() => {
      if (current) setResult({ failed: true });
    });
    return () => { current = false; };
  }, [load, attempt]);
  function retry() {
    setResult({ loading: true });
    setAttempt(value => value + 1);
  }
  return { ...result, retry };
}

const loadNotifications = () => dashboardApi.notifications(false);
const loadCounts = () => dashboardApi.dashboard();

const quickActions = [
  { to: '/patients', label: 'View patients', roles: ['ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'BILLING'] },
  { to: '/encounters', label: 'View OPD encounters', roles: ['ADMIN', 'DOCTOR', 'NURSE'] },
  { to: '/admissions', label: 'View wards', roles: ['ADMIN', 'DOCTOR', 'NURSE'] },
  { to: '/pharmacy', label: 'View pharmacy stock', roles: ['ADMIN', 'DOCTOR', 'PHARMACIST'] },
  { to: '/billing', label: 'View billing', roles: ['ADMIN', 'BILLING'] },
  { to: '/lab-orders', label: 'View lab orders', roles: ['LAB', 'ADMIN', 'DOCTOR', 'NURSE'] },
];

export default function Dashboard() {
  const user = useAuthStore(s => s.user);
  const tenant = useAuthStore(s => s.tenantId);
  return <HospitalOverview key={JSON.stringify([tenant, user?.username])} user={user} />;
}

function HospitalOverview({ user }) {
  const counts = useDashboardResource(loadCounts);
  const alerts = useDashboardResource(loadNotifications);
  const stats = counts.data;
  const notifications = alerts.data;

  const statCards = [
    { label: 'Active Admissions', value: stats?.activeAdmissions ?? '—', desc: 'Currently in care', color: 'var(--info)' },
    { label: 'Today’s appointments', value: stats?.todayAppointments ?? '—', desc: 'Scheduled for today', color: 'var(--normal)' },
    { label: 'Open Encounters', value: stats?.openEncounters ?? '—', desc: 'Pending physician sign-off', color: 'var(--urgent)' },
    { label: 'Total Patients', value: stats?.totalPatients ?? '—', desc: 'All registered patients', color: 'var(--action)' },
    { label: 'Pending Invoices', value: stats?.pendingInvoices ?? '—', desc: 'Awaiting payment', color: 'var(--critical)' },
    { label: 'Expiring Stock', value: stats?.expiringStockCount ?? '—', desc: 'Items expiring in 30d', color: 'var(--urgent)' },
  ];

  return (
    <div>
      <div className="page-header">
        <h1>Dashboard</h1>
        <p>Welcome back, {user?.fullName || user?.username}. Here is your hospital overview.</p>
      </div>

      <div className="grid-3 dashboard-stats">
        {counts.failed ? (
          <div role="alert">Hospital counts unavailable. <button className="btn-secondary" onClick={counts.retry}>Retry hospital counts</button></div>
        ) : counts.loading ? (
          <>
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <div key={i} className="stat-card skeleton skeleton-stat-card" />
            ))}
          </>
        ) : statCards.map((s) => (
          <div key={s.label} className="stat-card" style={{ borderTopColor: s.color }}>
            <div className="stat-label">{s.label}</div>
            <div className="stat-value" style={{ color: s.color }}>{s.value}</div>
            <div className="stat-desc">{s.desc}</div>
          </div>
        ))}
      </div>

      <div className="grid-2">
        <div className="card">
          <div className="card-header">
            <h3>Quick Actions</h3>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {quickActions.filter(action => action.roles.includes(user?.role?.toUpperCase())).map(action => (
              <Link key={action.to} to={action.to} className="btn-ghost" style={{ textDecoration: 'none', justifyContent: 'flex-start', padding: '12px 16px' }}>
                {action.label}
              </Link>
            ))}
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <h3>Recent Notifications</h3>
          </div>
          {alerts.failed ? (
            <div role="alert">Notifications unavailable. <button className="btn-secondary" onClick={alerts.retry}>Retry notifications</button></div>
          ) : alerts.loading ? (
            <p role="status">Loading notifications…</p>
          ) : notifications.length === 0 ? (
            <div className="empty-state" style={{ padding: 20 }}>
              <p>No recent notifications</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              {notifications.slice(0, 5).map((n) => (
                <div key={n.id} style={{
                  padding: '12px 0',
                  borderBottom: '1px solid var(--rule)',
                  fontSize: '0.85rem',
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 2 }}>
                    <span style={{ fontWeight: 500, color: 'var(--ink)' }}>{n.title}</span>
                    <span className={`badge badge-${n.type === 'critical' ? 'danger' : n.type === 'warning' ? 'warning' : n.type === 'success' ? 'success' : 'info'}`}>
                      {n.type}
                    </span>
                  </div>
                  <div style={{ color: 'var(--ink-faint)', fontSize: '0.8rem' }}>{n.message}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
