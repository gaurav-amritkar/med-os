import useAuthStore from '../store/authStore';

export default function Header() {
  const { user, logout } = useAuthStore();
  return (
    <header style={{
      position: 'fixed',
      top: 0,
      left: 'var(--sidebar-width)',
      right: 0,
      height: 'var(--header-height)',
      background: 'rgba(6, 6, 11, 0.8)',
      backdropFilter: 'blur(16px)',
      borderBottom: '1px solid var(--border)',
      zIndex: 'var(--z-header)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'flex-end',
      padding: '0 28px',
      gap: 16,
    }}>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
      }}>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-white)' }}>
            {user?.fullName || user?.username}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', textTransform: 'capitalize' }}>
            {user?.role}
          </div>
        </div>
        <div style={{
          width: 36,
          height: 36,
          borderRadius: '50%',
          background: 'var(--primary)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontWeight: 700,
          color: 'white',
          fontSize: '0.85rem',
        }}>
          {user?.fullName?.[0] || user?.username?.[0] || 'U'}
        </div>
        <button
          onClick={logout}
          className="btn-ghost btn-sm"
          title="Logout"
          style={{ marginLeft: 4 }}
        >
          ← Exit
        </button>
      </div>
    </header>
  );
}
