import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';
import Header from './Header';
import ToastContainer from './ToastContainer';

export default function Layout() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  return (
    <div style={{ minHeight: '100vh' }}>
      <Sidebar
        isOpen={mobileNavOpen}
        onClose={() => setMobileNavOpen(false)}
      />
      <Header onMenuClick={() => setMobileNavOpen((v) => !v)} />
      <main style={{
        marginLeft: 'var(--sidebar-width)',
        paddingTop: 'calc(var(--header-height) + 28px)',
        paddingLeft: 32,
        paddingRight: 32,
        paddingBottom: 40,
        minHeight: '100vh',
        maxWidth: '100%',
      }}>
        <Outlet />
      </main>
      <ToastContainer />
    </div>
  );
}
