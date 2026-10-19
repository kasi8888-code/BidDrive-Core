import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  Car, Navigation as _Nav, Wallet, Star, LayoutDashboard,
  LogOut, Settings, ChevronLeft, RadioTower
} from 'lucide-react';
import { useAuthStore } from '../lib/store';

interface NavItem {
  label: string;
  to: string;
  icon: React.ReactNode;
  badge?: number;
}

interface Props {
  collapsed: boolean;
  onToggle: () => void;
}

const DriverSidebar: React.FC<Props> = ({ collapsed, onToggle }) => {
  const { email, logout } = useAuthStore();
  const navigate = useNavigate();

  const navItems: NavItem[] = [
    { label: 'Dashboard', to: '/driver', icon: <LayoutDashboard size={18} /> },
    { label: 'Live Rides', to: '/driver/rides', icon: <RadioTower size={18} /> },
    { label: 'MapCN Live Map', to: '/driver/map', icon: <RadioTower size={18} /> },
    { label: 'My Bids', to: '/driver/bids', icon: <Car size={18} /> },
    { label: 'Wallet', to: '/driver/wallet', icon: <Wallet size={18} /> },
    { label: 'Ratings', to: '/driver/ratings', icon: <Star size={18} /> },
    { label: 'Settings', to: '/driver/settings', icon: <Settings size={18} /> },
  ];

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <aside className={`sidebar ${collapsed ? 'collapsed' : ''}`}>

      <div className="topbar" style={{ borderBottom: '1px solid var(--border-subtle)', borderRight: 'none' }}>
        {!collapsed && (
          <div className="topbar-logo">
            <Car size={22} />
            BidDrive
          </div>
        )}
        <button
          className="btn btn-ghost btn-icon"
          style={{ marginLeft: 'auto' }}
          onClick={onToggle}
          title="Toggle sidebar"
        >
          <ChevronLeft size={16} style={{ transform: collapsed ? 'rotate(180deg)' : 'none', transition: 'transform 0.3s' }} />
        </button>
      </div>

      {!collapsed && (
        <div style={{ padding: '12px 16px 0' }}>
          <span className="badge badge-available" style={{ fontSize: '10px' }}>
            <span className="status-dot active" />
            DRIVER
          </span>
          <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: 6, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {email}
          </p>
        </div>
      )}

      <nav className="nav-section" style={{ flex: 1 }}>
        {!collapsed && <div className="nav-section-label">Navigation</div>}
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/driver'}
            className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
            title={collapsed ? item.label : undefined}
          >
            {item.icon}
            {!collapsed && <span>{item.label}</span>}
            {!collapsed && item.badge !== undefined && (
              <span className="nav-item-count">{item.badge}</span>
            )}
          </NavLink>
        ))}
      </nav>

      <div style={{ padding: '12px' }}>
        <button className="nav-item w-full" onClick={handleLogout} title={collapsed ? 'Logout' : undefined}>
          <LogOut size={18} />
          {!collapsed && <span>Logout</span>}
        </button>
      </div>
    </aside>
  );
};

export default DriverSidebar;
