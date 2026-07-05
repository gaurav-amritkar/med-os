import { useState, useEffect } from 'react';
import { dashboardApi } from '../api';
import useAuthStore from '../store/authStore';
import { Link } from 'react-router-dom';

export default function Dashboard() {
  const [stats, setStats] = useState(null);
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const user = useAuthStore((s) => s.user);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [statsRes, notifRes] = await Promise.all([
        dashboardApi.dashboard(),
        dashboardApi.notifications(false),
      ]);
      setStats(statsRes.data);
      setNotifications((notifRes.data || []).slice(0, 5));
    } catch (err) {
      console.error('Dashboard fetch error', err);
    } finally {
      setLoading(false);
    }
  };

  const statCards = [
    { label: 'Active Admissions', value: stats?.activeAdmissions || 0, desc: 'Currently in care', color: 'var(--info)' },
    { label: 'Today VIP Appts', value: stats?.todayAppointments || 0, desc: 'Scheduled for today', color: 'var(--success)' },
    { label: 'Open Encounters', value: stats?.openEncounters || 0, desc: 'Pending physician sign-off', color: 'var(--warning)' },
    { label: 'Total Patients', value: stats?.totalPatients || 0, desc: 'All registered patients', color: 'var(--primary)' },
    { label: 'Pending Invoices', value: stats?.pendingInvoices || 0, desc: 'Awaiting payment', color: 'var(--danger)' },
    { label: 'Expiring Stock', value: stats?.expiringStockCount || 0, desc: 'Items expiring in 30d', color: 'var(--warning)' },
  ];

  return (
    <div>
      <div className="page-header">
        <h1>Dashboard</h1>
        <p>Welcome back, {user?.fullName || user?.username}. Here is your hospital overview.</p>
      </div>

      <div className="grid-3" style={{ marginBottom: 32 }}>
        {loading ? (
          <div className="stat-card" style={{ gridColumn: '1/-1', textAlign: 'center', padding: 40, color: 'var(--text-dim)' }}>
            Loading analytics...
          </div>
        ) : statCards.map((s) => (
          <div key={s.label} className="stat-card">
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
            <Link to="/patients" className="btn-ghost" style={{ textDecoration: 'none', justifyContent: 'flex-start', padding: '12px 16px' }}>
              ◈ Register New Patient
            </Link>
            <Link to="/encounters" className="btn-ghost" style={{ textDecoration: 'none', justifyContent: 'flex-start', padding: '12px 16px' }}>
              ◎ Start OPD Encounter
            </Link>
            <Link to="/admissions" className="btn-ghost" style={{ textDecoration: 'none', justifyContent: 'flex-start', padding: '12px 16px' }}>
              ▣ Admit / View Wards
            </Link>
            <Link to="/pharmacy" className="btn-ghost" style={{ textDecoration: 'none', justifyContent: 'flex-start', padding: '12px 16px' }}>
              ⬡ Manage Pharmacy Stock
            </Link>
            <Link to="/billing" className="btn-ghost" style={{ textDecoration: 'none', justifyContent: 'flex-start', padding: '12px 16px' }}>
              ₿ Generate Invoice
            </Link>
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <h3>Recent Notifications</h3>
          </div>
          {notifications.length === 0 ? (
            <div className="empty-state" style={{ padding: 20 }}>
              <p>No recent notifications</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              {notifications.map((n) => (
                <div key={n.id} style={{
                  padding: '12px 0',
                  borderBottom: '1px solid var(--border-light)',
                  fontSize: '0.85rem',
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 2 }}>
                    <span style={{ fontWeight: 500, color: 'var(--text-white)' }}>{n.title}</span>
                    <span className={`badge badge-${n.type === 'critical' ? 'danger' : n.type === 'warning' ? 'warning' : n.type === 'success' ? 'success' : 'info'}`}>
                      {n.type}
                    </span>
                  </div>
                  <div style={{ color: 'var(--text-dim)', fontSize: '0.8rem' }}>{n.message}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
