import useAuthStore from '../store/authStore';
import Icon from './icons';

export default function Header({ onMenuClick, navOpen }) {
  const { user, logout } = useAuthStore();
  const initial = user?.fullName?.[0] || user?.username?.[0] || 'U';

  return (
    <header className="topbar">
      <button
        type="button"
        onClick={onMenuClick}
        className="btn-secondary btn-icon hamburger-btn"
        aria-label="Toggle navigation"
        aria-expanded={navOpen ? 'true' : 'false'}
        aria-controls="primary-nav"
      >
        <Icon name="menu" size={20} />
      </button>

      <div className="topbar__identity">
        <div className="user-info">
          <div className="topbar__name">{user?.fullName || user?.username}</div>
          {user?.role && <div className="topbar__role">{user.role}</div>}
        </div>
      </div>

      <div className="topbar__avatar" aria-hidden="true">
        {initial}
      </div>

      <button type="button" onClick={logout} className="btn-secondary btn-sm logout-btn">
        <Icon name="logout" size={16} />
        Exit
      </button>
    </header>
  );
}
