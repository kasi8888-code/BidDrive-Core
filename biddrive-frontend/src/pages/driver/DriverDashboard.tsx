import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Car, DollarSign, CheckCircle, TrendingUp, MapPin, Clock, Star } from 'lucide-react';
import toast from 'react-hot-toast';
import { driversApi, ridesApi, walletApi, type Ride, type DriverWallet } from '../../lib/api';
import { useAuthStore } from '../../lib/store';
import { wsService } from '../../lib/websocket';

const DriverDashboard: React.FC = () => {
  const { userId } = useAuthStore();
  const navigate = useNavigate();

  const [status, setStatus] = useState<string>('Available');
  const [wallet, setWallet] = useState<DriverWallet | null>(null);
  const [activeRides, setActiveRides] = useState<Ride[]>([]);
  const [liveRideCount, setLiveRideCount] = useState(0);
  const [statusUpdating, setStatusUpdating] = useState(false);
  const [locationEnabled, setLocationEnabled] = useState(false);

  useEffect(() => {
    const load = async () => {
      try {
        const [walletRes, ridesRes, driverRes] = await Promise.all([
          walletApi.getMyWallet().catch(() => ({ data: null })),
          ridesApi.getAll('INITIATED').catch(() => ({ data: [] })),
          userId ? driversApi.getById(userId).catch(() => ({ data: null })) : Promise.resolve({ data: null }),
        ]);
        if (walletRes.data) setWallet(walletRes.data);
        if (ridesRes.data) setLiveRideCount(ridesRes.data.length);
        if (driverRes.data?.status) setStatus(driverRes.data.status);

        const myRidesRes = await ridesApi.getAll();
        const mine = myRidesRes.data.filter(
          (r: Ride) => r.driverId === userId && ['MATCHED', 'ARRIVING', 'DRIVER_ARRIVING', 'ARRIVED', 'IN_PROGRESS'].includes(r.status)
        );
        setActiveRides(mine);
      } catch (e) {

      }
    };
    load();
  }, [userId]);

  useEffect(() => {
    const unsub = wsService.subscribe('/topic/rides', (event: any) => {
      if (event.eventType === 'RIDE_CREATED') {
        setLiveRideCount((n) => n + 1);
        toast('🚗 New ride available!', { style: { background: '#1E1E2E', color: '#F0F0FF', border: '1px solid rgba(108,99,255,0.3)' } });
      }
    });
    return unsub;
  }, []);

  useEffect(() => {
    if (!locationEnabled) return;
    const watchId = navigator.geolocation.watchPosition(
      (pos) => {
        driversApi.updateLocation(pos.coords.latitude, pos.coords.longitude).catch(() => {});
      },
      () => setLocationEnabled(false),
      { enableHighAccuracy: true, maximumAge: 5000 }
    );
    return () => navigator.geolocation.clearWatch(watchId);
  }, [locationEnabled]);

  const toggleStatus = async () => {
    const next = status === 'Available' ? 'Offline' : 'Available';
    setStatusUpdating(true);
    try {
      await driversApi.updateStatus(next);
      setStatus(next);
      toast.success(`You are now ${next}`);
    } catch {
      toast.error('Status update failed');
    } finally {
      setStatusUpdating(false);
    }
  };

  const toggleLocation = () => {
    if (!locationEnabled) {
      navigator.geolocation.getCurrentPosition(
        async (pos) => {
          await driversApi.updateLocation(pos.coords.latitude, pos.coords.longitude);
          setLocationEnabled(true);
          toast.success('Location sharing enabled');
        },
        async () => {

          try {
            await driversApi.updateLocation(13.0418, 80.2341);
            setLocationEnabled(true);
            toast.success('Location set to Chennai (Demo)');
          } catch {
            toast.error('Location update failed');
          }
        }
      );
    } else {
      setLocationEnabled(false);
      toast('Location sharing paused');
    }
  };

  const isOnline = status === 'Available';

  return (
    <div className="animate-fade">
      <div className="page-header">
        <h1 className="page-title">Driver Dashboard</h1>
        <p className="page-subtitle">Manage your rides and track your earnings</p>
      </div>

      <div className="page-body">

        <div style={{ display: 'flex', gap: 16, marginBottom: 24, flexWrap: 'wrap' }}>
          <div className="status-toggle-panel" style={{ flex: 1, minWidth: 240 }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 14, fontWeight: 700, marginBottom: 2 }}>
                {isOnline ? '🟢 Online' : '⚫ Offline'}
              </div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                {isOnline ? 'You are accepting ride requests' : 'You are not accepting rides'}
              </div>
            </div>
            <label className="toggle">
              <input type="checkbox" checked={isOnline} onChange={toggleStatus} disabled={statusUpdating} />
              <span className="toggle-slider" />
            </label>
          </div>

          <div className="status-toggle-panel" style={{ flex: 1, minWidth: 240 }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 14, fontWeight: 700, marginBottom: 2 }}>
                {locationEnabled ? '📍 GPS Active' : '📍 GPS Off'}
              </div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                {locationEnabled ? 'Sharing real-time location' : 'Passengers cannot find you'}
              </div>
            </div>
            <label className="toggle">
              <input type="checkbox" checked={locationEnabled} onChange={toggleLocation} />
              <span className="toggle-slider" />
            </label>
          </div>
        </div>

        <div className="stats-grid" style={{ marginBottom: 28 }}>
          <div className="stat-card">
            <div className="stat-icon stat-icon-gold">
              <DollarSign size={22} />
            </div>
            <div className="stat-content">
              <div className="stat-value text-gold">
                ₹{wallet ? Number(wallet.totalEarnings).toFixed(0) : '—'}
              </div>
              <div className="stat-label">Total Earnings</div>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-icon stat-icon-success">
              <CheckCircle size={22} />
            </div>
            <div className="stat-content">
              <div className="stat-value text-success">{wallet?.totalRides ?? '—'}</div>
              <div className="stat-label">Completed Rides</div>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-icon stat-icon-primary">
              <TrendingUp size={22} />
            </div>
            <div className="stat-content">
              <div className="stat-value" style={{ color: '#6C63FF' }}>{liveRideCount}</div>
              <div className="stat-label">Live Ride Requests</div>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-icon stat-icon-primary">
              <DollarSign size={22} />
            </div>
            <div className="stat-content">
              <div className="stat-value text-gold">
                ₹{wallet ? Number(wallet.availableBalance).toFixed(0) : '—'}
              </div>
              <div className="stat-label">Available Balance</div>
            </div>
          </div>
        </div>

        {activeRides.length > 0 && (
          <div style={{ marginBottom: 24 }}>
            <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 12 }}>🔴 Active Ride</h2>
            {activeRides.map((ride) => (
              <div
                key={ride.id}
                className="ride-card card-glow"
                onClick={() => navigate(`/driver/rides/${ride.id}`)}
              >
                <div className="ride-card-header">
                  <span className="text-bold">Ride #{ride.id}</span>
                  <span className={`badge badge-${ride.status.toLowerCase()}`}>{ride.status}</span>
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
                    <span className="fare-display">₹{ride.finalFare ?? ride.baseFare}</span>
                  </span>
                  {ride.otp && (
                    <span className="ride-meta-item">
                      <Star size={13} />
                      OTP: <span className="text-brand text-bold text-mono">{ride.otp}</span>
                    </span>
                  )}
                </div>
                <button
                  className="btn btn-primary btn-sm w-full mt-3"
                  onClick={(e) => { e.stopPropagation(); navigate(`/driver/rides/${ride.id}`); }}
                >
                  Manage Ride
                </button>
              </div>
            ))}
          </div>
        )}

        <div className="card" style={{ textAlign: 'center', background: 'linear-gradient(135deg, rgba(108,99,255,0.08), rgba(59,55,232,0.04))' }}>
          <div style={{ fontSize: 32, marginBottom: 12 }}>🚗</div>
          <h3 style={{ fontSize: 18, fontWeight: 700, marginBottom: 8 }}>
            {liveRideCount > 0 ? `${liveRideCount} rides waiting for bids!` : 'No rides available right now'}
          </h3>
          <p className="text-secondary" style={{ marginBottom: 16, fontSize: 14 }}>
            Browse live auction rides and place your lowest bid to win the trip.
          </p>
          <button className="btn btn-primary" onClick={() => navigate('/driver/rides')}>
            <MapPin size={16} />
            Browse Live Rides
          </button>
        </div>
      </div>
    </div>
  );
};

export default DriverDashboard;
