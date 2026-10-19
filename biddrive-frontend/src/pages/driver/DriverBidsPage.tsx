import React, { useEffect, useState } from 'react';
import { Clock, DollarSign, CheckCircle } from 'lucide-react';
import { ridesApi, type Ride } from '../../lib/api';
import { useAuthStore } from '../../lib/store';
import { formatDistanceToNow } from 'date-fns';

const DriverBidsPage: React.FC = () => {
  const { userId } = useAuthStore();
  const [myRides, setMyRides] = useState<Ride[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<'active' | 'completed' | 'all'>('all');

  useEffect(() => {
    const load = async () => {
      try {
        const res = await ridesApi.getAll();
        const mine = res.data.filter((r: Ride) => r.driverId === userId);
        setMyRides(mine);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [userId]);

  const filtered = myRides.filter((r) => {
    if (filter === 'active') return ['MATCHED', 'ARRIVING', 'ARRIVED', 'IN_PROGRESS'].includes(r.status);
    if (filter === 'completed') return r.status === 'COMPLETED';
    return true;
  });

  return (
    <div className="animate-fade">
      <div className="page-header">
        <h1 className="page-title">My Ride History</h1>
        <p className="page-subtitle">All rides you've won and completed</p>
      </div>
      <div className="page-body">
        <div className="tabs mb-4">
          {(['all', 'active', 'completed'] as const).map((f) => (
            <button key={f} className={`tab-btn ${filter === f ? 'active' : ''}`} onClick={() => setFilter(f)}>
              {f.charAt(0).toUpperCase() + f.slice(1)}
            </button>
          ))}
        </div>

        {loading ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {[1, 2, 3].map((i) => <div key={i} className="skeleton" style={{ height: 100, borderRadius: 'var(--radius-lg)' }} />)}
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-icon">🏁</div>
            <div className="empty-state-title">No rides yet</div>
            <div className="empty-state-desc">Start bidding on live rides to build your history.</div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {filtered.map((ride) => (
              <div key={ride.id} className="card" style={{ padding: 16 }}>
                <div className="flex items-center justify-between mb-2">
                  <span className="text-bold" style={{ fontSize: 14 }}>Ride #{ride.id}</span>
                  <span className={`badge badge-${ride.status.toLowerCase()}`}>{ride.status}</span>
                </div>
                <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 8 }}>
                  {ride.pickupLocation} → {ride.destinationLocation}
                </div>
                <div className="flex gap-4" style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                  {ride.finalFare && (
                    <span className="flex items-center gap-1">
                      <DollarSign size={11} />
                      <span className="text-gold text-bold">₹{ride.finalFare}</span>
                    </span>
                  )}
                  <span className="flex items-center gap-1">
                    <Clock size={11} />
                    {formatDistanceToNow(new Date(ride.createdAt), { addSuffix: true })}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default DriverBidsPage;
