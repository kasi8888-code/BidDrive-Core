import React, { useEffect, useState } from 'react';
import { driversApi } from '../../lib/api';
import { useAuthStore } from '../../lib/store';
import toast from 'react-hot-toast';
import { Settings, Car, User, MapPin } from 'lucide-react';

const DriverSettingsPage: React.FC = () => {
  const { userId, email } = useAuthStore();
  const [driver, setDriver] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [preferenceMode, setPreferenceMode] = useState('BOTH');

  useEffect(() => {
    if (!userId) return;
    driversApi.getById(userId).then((res) => {
      setDriver(res.data);
      setPreferenceMode(res.data.preferenceMode ?? 'BOTH');
    }).finally(() => setLoading(false));
  }, [userId]);

  const savePreference = async () => {
    setSaving(true);
    try {
      await driversApi.updateStatus(driver.status);
      toast.success('Settings saved');
    } catch {
      toast.error('Save failed');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 300 }}>
      <span className="spinner" />
    </div>
  );

  return (
    <div className="animate-fade">
      <div className="page-header">
        <h1 className="page-title">Driver Settings</h1>
        <p className="page-subtitle">Manage your profile and preferences</p>
      </div>
      <div className="page-body" style={{ maxWidth: 640 }}>

        <div className="card" style={{ marginBottom: 20 }}>
          <div className="flex items-center gap-4" style={{ marginBottom: 20 }}>
            <div style={{
              width: 64, height: 64, borderRadius: '50%',
              background: 'var(--gradient-primary)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 24, fontWeight: 700, color: '#fff', flexShrink: 0,
            }}>
              {email?.charAt(0).toUpperCase()}
            </div>
            <div>
              <div style={{ fontWeight: 700, fontSize: 18 }}>{driver?.name}</div>
              <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{driver?.email}</div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>
                {driver?.carType} · {driver?.licensePlate}
              </div>
            </div>
          </div>
          <div className="divider" />
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <div>
              <div className="form-label">Phone Number</div>
              <div style={{ fontSize: 14, color: 'var(--text-primary)', marginTop: 4 }}>{driver?.phoneNumber}</div>
            </div>
            <div>
              <div className="form-label">Rating</div>
              <div style={{ fontSize: 14, fontWeight: 700, color: '#F7B731', marginTop: 4 }}>
                ⭐ {Number(driver?.rating).toFixed(2)} ({driver?.totalRatings} ratings)
              </div>
            </div>
          </div>
        </div>

        <div className="card" style={{ marginBottom: 20 }}>
          <h3 style={{ fontWeight: 700, marginBottom: 4 }}>Ride Preference Mode</h3>
          <p style={{ fontSize: 13, color: 'var(--text-muted)', marginBottom: 16 }}>
            Control which types of ride requests you receive alerts for.
          </p>
          <div style={{ display: 'flex', gap: 10 }}>
            {[
              { value: 'BOTH', label: 'Both Modes', desc: 'Auction & Instant', icon: '⚡' },
              { value: 'AUCTION_ONLY', label: 'Auction Only', desc: 'Bidding rides', icon: '⚖️' },
              { value: 'INSTANT_ONLY', label: 'Instant Only', desc: 'Fixed price', icon: '🚀' },
            ].map((opt) => (
              <button
                key={opt.value}
                onClick={() => setPreferenceMode(opt.value)}
                style={{
                  flex: 1, padding: '12px 8px',
                  borderRadius: 'var(--radius-md)',
                  background: preferenceMode === opt.value ? 'rgba(108,99,255,0.1)' : 'var(--bg-elevated)',
                  border: `1px solid ${preferenceMode === opt.value ? 'rgba(108,99,255,0.4)' : 'var(--border-default)'}`,
                  color: preferenceMode === opt.value ? '#6C63FF' : 'var(--text-secondary)',
                  cursor: 'pointer', fontFamily: 'inherit',
                  display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4, textAlign: 'center',
                }}
              >
                <span style={{ fontSize: 20 }}>{opt.icon}</span>
                <span style={{ fontSize: 12, fontWeight: 700 }}>{opt.label}</span>
                <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>{opt.desc}</span>
              </button>
            ))}
          </div>
        </div>

        <button className="btn btn-primary" onClick={savePreference} disabled={saving}>
          {saving ? <span className="spinner spinner-sm" /> : <>
            <Settings size={15} />
            Save Settings
          </>}
        </button>
      </div>
    </div>
  );
};

export default DriverSettingsPage;
