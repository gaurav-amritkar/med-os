import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';
import Header from './Header';
import ToastContainer from './ToastContainer';
import PatientBanner from './PatientBanner';

export default function Layout() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  return (
    <>
      <a className="skip-link" href="#main">
        Skip to main content
      </a>

      <div className="app-shell">
        <Sidebar
          isOpen={mobileNavOpen}
          onClose={() => setMobileNavOpen(false)}
        />

        <Header
          navOpen={mobileNavOpen}
          onMenuClick={() => setMobileNavOpen((v) => !v)}
        />

        <PatientBanner />

        <main id="main" className="page">
          <Outlet />
        </main>
      </div>

      <ToastContainer />
    </>
  );
}
