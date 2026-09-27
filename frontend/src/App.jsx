import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Patients from './pages/Patients';
import Encounters from './pages/Encounters';
import Pharmacy from './pages/Pharmacy';
import Admissions from './pages/Admissions';
import Billing from './pages/Billing';
import Onboarding from './pages/Onboarding';
import IconGallery from './pages/IconGallery';
import ToastContainer from './components/ToastContainer';
import useAuthStore from './store/authStore';

function Root() {
  const token = useAuthStore((s) => s.token);
  if (!token) return <Navigate to="/login" replace />;
  return <Navigate to="/dashboard" replace />;
}

export default function App() {
  return (
    <BrowserRouter>
      {/* Mounted at app level, not inside Layout: /login and /onboarding sit
          outside the shell, and a failed sign-in is the single most important
          message the product emits. A live region scoped to one route means
          the authentication error is never announced. */}
      <ToastContainer />

      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/onboarding" element={<Onboarding />} />

        <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/" element={<Root />} />

          <Route path="/patients" element={<ProtectedRoute roles={['ADMIN','DOCTOR','NURSE','RECEPTIONIST','BILLING']}><Patients /></ProtectedRoute>} />
          <Route path="/patients/:id" element={<ProtectedRoute roles={['ADMIN','DOCTOR','NURSE','RECEPTIONIST']}><Patients /></ProtectedRoute>} />
          <Route path="/encounters" element={<ProtectedRoute roles={['ADMIN','DOCTOR','NURSE']}><Encounters /></ProtectedRoute>} />
          <Route path="/pharmacy" element={<ProtectedRoute roles={['ADMIN','PHARMACIST','DOCTOR']}><Pharmacy /></ProtectedRoute>} />
          <Route path="/admissions" element={<ProtectedRoute roles={['ADMIN','DOCTOR','NURSE']}><Admissions /></ProtectedRoute>} />
          <Route path="/billing" element={<ProtectedRoute roles={['ADMIN','BILLING']}><Billing /></ProtectedRoute>} />
        </Route>

        {/* Dev-only: inspection page for the hand-authored icon set. Stripped
            from production builds by the import.meta.env.DEV guard. */}
        {import.meta.env.DEV && <Route path="/icons" element={<IconGallery />} />}

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
