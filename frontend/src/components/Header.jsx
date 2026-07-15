import useAuthStore from '../store/authStore';

export default function Header({ onMenuClick }) {
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
      WebkitBackdropFilter: 'blur(16px)',
      borderBottom: '1px solid var(--border)',
      zIndex: 'var(--z-header)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'flex-end',
      padding: '0 28px',
      gap: 16,
    }}>
      {/* Hamburger — visible only on mobile via CSS */}
      <button
        onClick={onMenuClick}
        className="btn-ghost btn-icon hamburger-btn"
        aria-label="Toggle menu"
        style={{ marginRight: 'auto' }}
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
          <line x1="3" y1="6" x2="21" y2="6" />
          <line x1="3" y1="12" x2="21" y2="12" />
          <line x1="3" y1="18" x2="21" y2="18" />
        </svg>
      </button>

      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
      }}>
        <div className="user-info" style={{ textAlign: 'right' }}>
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
          flexShrink: 0,
        }}>
          {user?.fullName?.[0] || user?.username?.[0] || 'U'}
        </div>
        <button
          onClick={logout}
          className="btn-ghost btn-sm logout-btn"
          title="Logout"
          style={{ marginLeft: 4 }}
        >
          ← Exit
        </button>
      </div>
    </header>
  );
}
