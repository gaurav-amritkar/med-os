import { Navigate } from 'react-router-dom';
import useAuthStore from '../store/authStore';

export default function ProtectedRoute({ children, roles }) {
  const token = useAuthStore((s) => s.token);
  const user = useAuthStore((s) => s.user);

  if (!token) return <Navigate to="/login" replace />;
  if (roles && user && !roles.includes(user.role.toUpperCase())) {
    return <Navigate to="/" replace />;
  }
  return children;
}
