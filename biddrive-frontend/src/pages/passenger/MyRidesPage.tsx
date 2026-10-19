import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Clock, MapPin, DollarSign, Filter } from 'lucide-react';
import { ridesApi, type Ride } from '../../lib/api';
import { useAuthStore } from '../../lib/store';
import { formatDistanceToNow } from 'date-fns';

const statuses = ['ALL', 'INITIATED', 'MATCHED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'] as const;
type Status = typeof statuses[number];

const MyRidesPage: React.FC = () => {
  const navigate = useNavigate();
  const { userId } = useAuthStore();
  const [rides, setRides] = useState<Ride[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<Status>('ALL');

  useEffect(() => {
    ridesApi.getAll()
      .then((res) => {
        const mine = res.data
          .filter((r: Ride) => r.passengerId === userId)
          .sort((a: Ride, b: Ride) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
        setRides(mine);
      })
      .finally(() => setLoading(false));
  }, [userId]);

  const filtered = filter === 'ALL' ? rides : rides.filter((r) => r.status === filter);

  return (
    <div className="animate-fade">
      <div className="page-header">
        <h1 className="page-title">My Rides</h1>
        <p className="page-subtitle">{rides.length} total rides in your history</p>
      </div>
      <div className="page-body">

        <div className="flex items-center gap-3 mb-4" style={{ overflowX: 'auto', paddingBottom: 4 }}>
          <Filter size={14} style={{ color: 'var(--text-secondary)', flexShrink: 0 }} />
          <div className="tabs">
            {statuses.map((s) => (
              <button key={s} className={`tab-btn ${filter === s ? 'active' : ''}`} onClick={() => setFilter(s)}>
                {s.replace('_', ' ')}
              </button>
            ))}
          </div>
        </div>

        {loading ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="skeleton" style={{ height: 120, borderRadius: 'var(--radius-lg)' }} />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-icon">🛣️</div>
            <div className="empty-state-title">No rides found</div>
            <div className="empty-state-desc">
              {filter === 'ALL' ? 'Book your first ride to see it here.' : `No rides with status "${filter}".`}
            </div>
            <button className="btn btn-primary" onClick={() => navigate('/passenger/book')}>
              Book a Ride
            </button>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {filtered.map((ride) => (
              <div
                key={ride.id}
                className="ride-card"
                onClick={() => navigate(`/passenger/rides/${ride.id}`)}
              >
                <div className="ride-card-header">
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span className="text-bold" style={{ fontSize: 14 }}>Ride #{ride.id}</span>
                    <span className={`badge badge-${ride.rideType === 'INSTANT' ? 'matched' : 'initiated'}`} style={{ fontSize: 9 }}>
                      {ride.rideType}
                    </span>
                  </div>
                  <span className={`badge badge-${ride.status.toLowerCase().replace('_', '')}`}>
                    {ride.status.replace('_', ' ')}
                  </span>
                </div>

                <div className="ride-route">
                  <div className="ride-location">
                    <span className="location-dot pickup" />
                    <span>{ride.pickupLocation}</span>
                  </div>
                  <div className="ride-location">
                    <span className="location-dot dest" />
                    <span>{ride.destinationLocation}</span>
                  </div>
                </div>

                <div className="ride-meta">
                  <span className="ride-meta-item">
                    <DollarSign size={13} />
                    {ride.finalFare ? (
                      <span>Paid <span className="text-gold text-bold">₹{ride.finalFare}</span></span>
                    ) : (
                      <span>Max <span className="text-secondary">₹{ride.baseFare}</span></span>
                    )}
                  </span>
                  {ride.estimatedDistanceKm && (
                    <span className="ride-meta-item">
                      <MapPin size={13} />
                      {ride.estimatedDistanceKm.toFixed(1)} km
                    </span>
                  )}
                  <span className="ride-meta-item">
                    <Clock size={13} />
                    {formatDistanceToNow(new Date(ride.createdAt), { addSuffix: true })}
                  </span>
                </div>

                {['INITIATED', 'MATCHED', 'ARRIVING', 'ARRIVED', 'IN_PROGRESS'].includes(ride.status) && (
                  <button
                    className="btn btn-primary btn-sm w-full mt-3"
                    onClick={(e) => { e.stopPropagation(); navigate(`/passenger/rides/${ride.id}`); }}
                  >
                    {ride.status === 'INITIATED' ? '👁️ Watch Auction' : '📍 Track Ride'}
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default MyRidesPage;
