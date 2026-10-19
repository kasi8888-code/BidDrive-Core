import React, { useState } from 'react';
import { Outlet } from 'react-router-dom';
import PassengerSidebar from '../components/PassengerSidebar';
import Topbar from '../components/Topbar';

const PassengerLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className="app-shell">
      <Topbar />
      <div className="main-layout">
        <PassengerSidebar collapsed={collapsed} onToggle={() => setCollapsed(!collapsed)} />
        <main className="page-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default PassengerLayout;
