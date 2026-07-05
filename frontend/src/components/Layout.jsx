import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';
import Header from './Header';
import ToastContainer from './ToastContainer';

export default function Layout() {
  return (
    <div style={{ minHeight: '100vh' }}>
      <Sidebar />
      <Header />
      <main style={{
        marginLeft: 'var(--sidebar-width)',
        paddingTop: 'calc(var(--header-height) + 28px)',
        paddingLeft: 32,
        paddingRight: 32,
        paddingBottom: 40,
        minHeight: '100vh',
      }}>
        <Outlet />
      </main>
      <ToastContainer />
    </div>
  );
}
