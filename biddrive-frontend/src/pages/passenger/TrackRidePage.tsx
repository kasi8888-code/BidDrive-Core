import React, { useEffect, useState } from 'react';
import { MapPin, Navigation } from 'lucide-react';
import { ridesApi, type Ride } from '../../lib/api';
import { useAuthStore } from '../../lib/store';
import { wsService } from '../../lib/websocket';
import RideMap from '../../components/RideMap';
import { useNavigate } from 'react-router-dom';

const TrackRidePage: React.FC = () => {
  const { userId } = useAuthStore();
  const navigate = useNavigate();
  const [activeRide, setActiveRide] = useState<Ride | null>(null);
  const [loading, setLoading] = useState(true);

  const findActiveRide = async () => {
    const res = await ridesApi.getAll();
    const active = res.data.find((r: Ride) =>
      r.passengerId === userId &&
      ['INITIATED', 'MATCHED', 'ARRIVING', 'ARRIVED', 'IN_PROGRESS'].includes(r.status)
    );
    setActiveRide(active ?? null);
    setLoading(false);
  };

  useEffect(() => {
    findActiveRide();

    const unsub = wsService.subscribe('/user/queue/notifications', (event: any) => {
      if (['AUCTION_MATCHED', 'RIDE_STARTED', 'RIDE_COMPLETED'].includes(event.eventType)) {
        findActiveRide();
      }
    });
    return () => unsub();
  }, [userId]);

  if (loading) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 300 }}>
      <span className="spinner" />
    </div>
  );

  if (!activeRide) return (
    <div className="animate-fade">
      <div className="page-header">
        <h1 className="page-title">Track Ride</h1>
        <p className="page-subtitle">Real-time GPS tracking for your active ride</p>
      </div>
      <div className="page-body">
        <div className="empty-state">
          <div className="empty-state-icon">📍</div>
          <div className="empty-state-title">No active ride</div>
          <div className="empty-state-desc">
            You don't have any ongoing rides right now. Book a ride to start tracking.
          </div>
          <button className="btn btn-primary" onClick={() => navigate('/passenger/book')}>
            Book a Ride
          </button>
        </div>
      </div>
    </div>
  );

  return (
    <div className="animate-fade">
      <div className="page-header">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="page-title">Tracking Ride #{activeRide.id}</h1>
            <span className={`badge badge-${activeRide.status.toLowerCase()}`}>{activeRide.status.replace('_', ' ')}</span>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={() => navigate(`/passenger/rides/${activeRide.id}`)}>
            Full Details →
          </button>
        </div>
      </div>
      <div className="page-body">
        {/* Route info bar */}
        <div className="card" style={{ padding: '14px 20px', marginBottom: 16, display: 'flex', alignItems: 'center', gap: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flex: 1 }}>
            <span className="location-dot pickup" />
            <span style={{ fontSize: 14, fontWeight: 600 }}>{activeRide.pickupLocation}</span>
          </div>
          <Navigation size={16} style={{ color: 'var(--text-muted)', flexShrink: 0 }} />
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flex: 1 }}>
            <span className="location-dot dest" />
            <span style={{ fontSize: 14, fontWeight: 600 }}>{activeRide.destinationLocation}</span>
          </div>
          {activeRide.finalFare && (
            <div style={{ fontSize: 18, fontWeight: 800, color: '#F7B731', marginLeft: 8 }}>
              ₹{activeRide.finalFare}
            </div>
          )}
        </div>

        {/* Live Auction Banner if ride is in auction */}
        {activeRide.status === 'INITIATED' && activeRide.rideType === 'AUCTION' && (
          <div className="card" style={{
            background: 'linear-gradient(135deg, rgba(108,99,255,0.15), rgba(247,183,49,0.1))',
            border: '1.5px solid rgba(247,183,49,0.4)',
            padding: '16px 20px',
            marginBottom: 16,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <span style={{ fontSize: 24 }}>📡</span>
              <div>
                <div style={{ fontWeight: 700, fontSize: 15, color: '#fff' }}>Reverse Auction in Progress (90s)</div>
                <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Drivers are competing with bids to win your trip</div>
              </div>
            </div>
            <button className="btn btn-primary btn-sm" onClick={() => navigate(`/passenger/rides/${activeRide.id}`)}>
              View Live Bids & Countdown →
            </button>
          </div>
        )}

        {/* OTP if matched */}
        {activeRide.otp && ['MATCHED', 'ARRIVING', 'ARRIVED'].includes(activeRide.status) && (
          <div className="card" style={{ textAlign: 'center', padding: '20px', marginBottom: 16, border: '1px solid rgba(108,99,255,0.3)' }}>
            <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 10 }}>Share OTP with driver to start the trip</p>
            <div className="otp-display">
              {activeRide.otp.split('').map((d, i) => (
                <div key={i} className="otp-digit">{d}</div>
              ))}
            </div>
          </div>
        )}

        {/* Full-screen map */}
        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          <RideMap
            pickupLat={activeRide.pickupLatitude}
            pickupLng={activeRide.pickupLongitude}
            destLat={activeRide.destinationLatitude}
            destLng={activeRide.destinationLongitude}
            height={520}
          />
        </div>

        {/* Status messages */}
        <div className="alert alert-info mt-4">
          <MapPin size={16} />
          <div>
            {activeRide.status === 'INITIATED' && '⏳ Waiting for drivers to place bids on your ride...'}
            {activeRide.status === 'MATCHED' && '🎉 Driver matched! They will arrive shortly.'}
            {activeRide.status === 'ARRIVING' && '🚗 Your driver is on the way to your location!'}
            {activeRide.status === 'ARRIVED' && '📍 Your driver has arrived. Share your OTP to start.'}
            {activeRide.status === 'IN_PROGRESS' && '🚀 Trip in progress — enjoy your ride!'}
          </div>
        </div>
      </div>
    </div>
  );
};

export default TrackRidePage;
