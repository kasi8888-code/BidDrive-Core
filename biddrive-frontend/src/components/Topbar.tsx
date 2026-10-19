import React from 'react';
import { Bell, Wifi, WifiOff } from 'lucide-react';
import { useAuthStore } from '../lib/store';
import { wsService } from '../lib/websocket';

const Topbar: React.FC = () => {
  const { email, role } = useAuthStore();
  const [wsConnected, setWsConnected] = React.useState(wsService.isConnected());

  React.useEffect(() => {
    const interval = setInterval(() => {
      setWsConnected(wsService.isConnected());
    }, 2000);
    return () => clearInterval(interval);
  }, []);

  return (
    <header className="topbar" style={{ width: '100%' }}>
      <div style={{ flex: 1 }} />
      <div className="flex items-center gap-3">

        <span
          className="flex items-center gap-2"
          style={{ fontSize: '12px', color: wsConnected ? '#43E97B' : 'var(--text-muted)' }}
          title={wsConnected ? 'Live connected' : 'Reconnecting...'}
        >
          {wsConnected ? <Wifi size={14} /> : <WifiOff size={14} />}
          {wsConnected ? 'Live' : 'Offline'}
        </span>

        <button className="btn btn-ghost btn-icon" title="Notifications">
          <Bell size={16} />
        </button>

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            background: 'var(--bg-elevated)',
            border: '1px solid var(--border-default)',
            borderRadius: 'var(--radius-full)',
            padding: '5px 14px',
          }}
        >
          <div
            style={{
              width: 28,
              height: 28,
              borderRadius: '50%',
              background: 'var(--gradient-primary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 11,
              fontWeight: 700,
              color: '#fff',
              flexShrink: 0,
            }}
          >
            {email?.charAt(0).toUpperCase() ?? '?'}
          </div>
          <div style={{ lineHeight: 1.2 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-primary)' }}>
              {email?.split('@')[0] ?? 'User'}
            </div>
            <div style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase' }}>{role}</div>
          </div>
        </div>
      </div>
    </header>
  );
};

export default Topbar;
