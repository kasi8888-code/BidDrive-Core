import React, { useState } from 'react';
import { Outlet } from 'react-router-dom';
import DriverSidebar from '../components/DriverSidebar';
import Topbar from '../components/Topbar';

const DriverLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className="app-shell">
      <Topbar />
      <div className="main-layout">
        <DriverSidebar collapsed={collapsed} onToggle={() => setCollapsed(!collapsed)} />
        <main className="page-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default DriverLayout;
