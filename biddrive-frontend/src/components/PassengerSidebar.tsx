import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  User, MapPin, Clock, Star, LayoutDashboard,
  LogOut, Settings, ChevronLeft, Plus
} from 'lucide-react';
import { useAuthStore } from '../lib/store';

interface NavItem {
  label: string;
  to: string;
  icon: React.ReactNode;
}

interface Props {
  collapsed: boolean;
  onToggle: () => void;
}

const PassengerSidebar: React.FC<Props> = ({ collapsed, onToggle }) => {
  const { email, logout } = useAuthStore();
  const navigate = useNavigate();

  const navItems: NavItem[] = [
    { label: 'Dashboard', to: '/passenger', icon: <LayoutDashboard size={18} /> },
    { label: 'Book a Ride', to: '/passenger/book', icon: <Plus size={18} /> },
    { label: 'MapCN Live Map', to: '/passenger/map', icon: <MapPin size={18} /> },
    { label: 'My Rides', to: '/passenger/rides', icon: <Clock size={18} /> },
    { label: 'Track Ride', to: '/passenger/track', icon: <MapPin size={18} /> },
    { label: 'Ratings', to: '/passenger/ratings', icon: <Star size={18} /> },
    { label: 'Settings', to: '/passenger/settings', icon: <Settings size={18} /> },
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
            <User size={22} />
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
          <span className="badge badge-initiated" style={{ fontSize: '10px' }}>
            <span className="status-dot active" />
            PASSENGER
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
            end={item.to === '/passenger'}
            className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
            title={collapsed ? item.label : undefined}
          >
            {item.icon}
            {!collapsed && <span>{item.label}</span>}
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

export default PassengerSidebar;
