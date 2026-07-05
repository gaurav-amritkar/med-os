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
import useAuthStore from './store/authStore';

function Root() {
  const token = useAuthStore((s) => s.token);
  if (!token) return <Navigate to="/login" replace />;
  return <Navigate to="/dashboard" replace />;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />

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

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
