import React, { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { MapPin, Clock, DollarSign, Navigation, Filter } from 'lucide-react';
import toast from 'react-hot-toast';
import { ridesApi, bidsApi, driversApi, type Ride } from '../../lib/api';
import { useAuthStore } from '../../lib/store';
import { wsService } from '../../lib/websocket';
import { formatDistanceToNow } from 'date-fns';

export const isVehicleCompatible = (requested?: string, driverCar?: string) => {
  if (!requested || !driverCar) return false;
  const req = requested.trim().toLowerCase();
  const car = driverCar.trim().toLowerCase();
  if (req === car) return true;

  if (req === 'bike' || req === 'motorcycle') {
    return car === 'bike' || car === 'motorcycle';
  }
  if (req === 'scooty' || req === 'scooter') {
    return car === 'scooty' || car === 'scooter';
  }

  if (req.includes('auto')) return car.includes('auto');

  if (req === 'hatchback') return car === 'hatchback';
  if (req === 'sedan') return car === 'sedan';
  if (req === 'suv') return car === 'suv';

  if (req.includes('economy') || req.includes('mini')) {
    return car.includes('hatchback') || car.includes('economy') || car.includes('mini') || car.includes('sedan');
  }
  if (req.includes('priority')) {
    return car.includes('sedan') || car.includes('suv') || car.includes('priority');
  }
  return false;
};

const LiveRidesPage: React.FC = () => {
  const navigate = useNavigate();
  const { userId } = useAuthStore();
  const [rides, setRides] = useState<Ride[]>([]);
  const [driverCarType, setDriverCarType] = useState<string>('');
  const [driverStatus, setDriverStatus] = useState<string>('Available');
  const [loading, setLoading] = useState(true);
  const [bidAmounts, setBidAmounts] = useState<Record<number, string>>({});
  const [submitting, setSubmitting] = useState<Record<number, boolean>>({});
  const [filter, setFilter] = useState<'ALL' | 'AUCTION' | 'INSTANT'>('ALL');

  useEffect(() => {
    if (userId) {
      driversApi.getById(userId).then((res) => {
        if (res.data?.carType) setDriverCarType(res.data.carType);
        if (res.data?.status) setDriverStatus(res.data.status);
      }).catch(() => {});
    }
  }, [userId]);

  const goOnline = async () => {
    try {
      await driversApi.updateStatus('Available');
      setDriverStatus('Available');
      toast.success('🟢 You are now Available to receive rides and place bids!');
    } catch {
      toast.error('Failed to update status');
    }
  };

  const fetchRides = useCallback(async () => {
    try {
      const res = await ridesApi.getAll('INITIATED');
      setRides(res.data);
    } catch {
      toast.error('Failed to load rides');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchRides();

    const unsubRides = wsService.subscribe('/topic/rides', (event: any) => {
      if (event.eventType === 'RIDE_CREATED') {
        setRides((prev) => {
          if (prev.find((r) => r.id === event.rideId)) return prev;
          const newRide: Ride = {
            id: event.rideId,
            passengerId: event.passengerId,
            pickupLocation: event.pickupLocation,
            destinationLocation: event.destinationLocation,
            pickupLatitude: event.pickupLatitude,
            pickupLongitude: event.pickupLongitude,
            destinationLatitude: event.destinationLatitude,
            destinationLongitude: event.destinationLongitude,
            estimatedDistanceKm: event.estimatedDistanceKm,
            baseFare: event.baseFare,
            status: 'INITIATED',
            rideType: 'AUCTION',
            vehicleType: event.vehicleType,
            createdAt: new Date().toISOString(),
            remainingSeconds: event.remainingSeconds ?? 90,
          };
          return [newRide, ...prev];
        });
        toast('🆕 New ride posted!', {
          style: { background: '#1E1E2E', color: '#F0F0FF', border: '1px solid rgba(108,99,255,0.3)' },
        });
      }
      if (event.eventType === 'AUCTION_MATCHED' || event.eventType === 'AUCTION_EXPIRED') {
        setRides((prev) => prev.filter((r) => r.id !== event.rideId));
      }
    });

    return () => { unsubRides(); };
  }, [fetchRides]);

  const submitBid = async (rideId: number) => {
    const amount = parseFloat(bidAmounts[rideId] ?? '');
    if (isNaN(amount) || amount <= 0) {
      toast.error('Enter a valid bid amount');
      return;
    }
    setSubmitting((s) => ({ ...s, [rideId]: true }));
    try {
      await bidsApi.submit({ rideId, amount });
      toast.success(`Bid of ₹${amount} placed!`);
      setBidAmounts((b) => ({ ...b, [rideId]: '' }));
    } catch (err: any) {
      const isNetworkDown = !err.response || err.code === 'ERR_NETWORK' || err.message?.includes('Network Error');
      const isProxyFailure = err.response?.status >= 500 && (typeof err.response?.data === 'string' && (err.response.data.includes('ECONNREFUSED') || err.response.data.includes('proxy')));
      const serverError = typeof err.response?.data === 'object' ? (err.response.data?.error || err.response.data?.message) : null;

      const msg = serverError
        || (isProxyFailure || isNetworkDown
            ? '🔴 Backend server unreachable (port 8080) — make sure Spring Boot is running!'
            : (typeof err.response?.data === 'string' ? err.response.data.slice(0, 100) : 'Bid failed'));
      toast.error(msg);
    } finally {
      setSubmitting((s) => ({ ...s, [rideId]: false }));
    }
  };

  const filtered = filter === 'ALL' ? rides : rides.filter((r) => r.rideType === filter);

  return (
    <div className="animate-fade">
      <div className="page-header">
        <div className="flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2">
              <h1 className="page-title">Live Ride Requests</h1>
              {driverCarType && (
                <span className="badge badge-primary" style={{ fontSize: 12, padding: '4px 10px' }}>
                  🚗 My Vehicle: {driverCarType}
                </span>
              )}
            </div>
            <p className="page-subtitle">{rides.length} rides available — only matching vehicle rides can receive your bids</p>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={fetchRides}>
            Refresh
          </button>
        </div>
      </div>

      <div className="page-body">
        {driverStatus.toLowerCase() !== 'available' && (
          <div style={{
            marginBottom: 16,
            padding: '12px 16px',
            borderRadius: 'var(--radius-md)',
            background: 'rgba(255, 171, 0, 0.12)',
            border: '1px solid rgba(255, 171, 0, 0.4)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
          }}>
            <div className="flex items-center gap-2" style={{ color: '#FFAB00', fontSize: 13, fontWeight: 500 }}>
              <span>⚠️</span>
              <span>
                Your current duty status is <strong>{driverStatus}</strong>. Drivers cannot place bids while Offline or Busy.
              </span>
            </div>
            <button
              className="btn btn-primary btn-sm"
              onClick={goOnline}
              style={{ padding: '6px 14px', fontSize: 12, whiteSpace: 'nowrap' }}
            >
              🟢 Go Available Now
            </button>
          </div>
        )}

        <div className="flex items-center gap-3 mb-4">
          <Filter size={14} className="text-secondary" />
          <div className="tabs">
            {(['ALL', 'AUCTION', 'INSTANT'] as const).map((f) => (
              <button
                key={f}
                className={`tab-btn ${filter === f ? 'active' : ''}`}
                onClick={() => setFilter(f)}
              >
                {f}
              </button>
            ))}
          </div>
        </div>

        {loading ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {[1, 2, 3].map((i) => (
              <div key={i} className="skeleton" style={{ height: 180, borderRadius: 'var(--radius-lg)' }} />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-icon">🚗</div>
            <div className="empty-state-title">No rides available</div>
            <div className="empty-state-desc">No live requests matching your vehicle category right now. Check back shortly.</div>
            <button className="btn btn-primary" onClick={fetchRides}>Refresh</button>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {filtered.map((ride) => (
              <RideCard
                key={ride.id}
                ride={ride}
                driverCarType={driverCarType}
                bidAmount={bidAmounts[ride.id] ?? ''}
                onBidChange={(v) => setBidAmounts((b) => ({ ...b, [ride.id]: v }))}
                onSubmit={() => submitBid(ride.id)}
                isSubmitting={!!submitting[ride.id]}
                onViewDetails={() => navigate(`/driver/rides/${ride.id}`)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

interface RideCardProps {
  ride: Ride;
  driverCarType?: string;
  bidAmount: string;
  onBidChange: (v: string) => void;
  onSubmit: () => void;
  isSubmitting: boolean;
  onViewDetails: () => void;
}

const RideCard: React.FC<RideCardProps> = ({ ride, driverCarType, bidAmount, onBidChange, onSubmit, isSubmitting, onViewDetails }) => {
  const [auctionData, setAuctionData] = useState<any>(null);
  const [remaining, setRemaining] = useState<number>(
    ride.remainingSeconds !== undefined && ride.remainingSeconds !== null ? Number(ride.remainingSeconds) : 90
  );

  useEffect(() => {

    const unsub = wsService.subscribe(`/topic/auction/${ride.id}`, (event: any) => {
      if (event.lowestBid) setAuctionData(event);
      if (event.remainingSeconds !== undefined) setRemaining(Number(event.remainingSeconds));
    });

    ridesApi.getAuction(ride.id).then((res) => {
      setAuctionData(res.data);
      if (res.data && res.data.remainingSeconds !== undefined && Number(res.data.remainingSeconds) > 0) {
        setRemaining(Number(res.data.remainingSeconds));
      } else {
        setRemaining(0);
      }
    }).catch(() => {
      setRemaining(0);
    });

    return () => unsub();
  }, [ride.id]);

  useEffect(() => {
    if (remaining <= 0) return;
    const interval = setInterval(() => setRemaining((r) => Math.max(0, r - 1)), 1000);
    return () => clearInterval(interval);
  }, [remaining]);

  const isUrgent = remaining > 0 && remaining <= 30;
  const currentLowest = auctionData?.lowestBid ?? auctionData?.currentLowestBid;
  const mins = Math.floor(remaining / 60);
  const secs = remaining % 60;
  const compatible = isVehicleCompatible(ride.vehicleType, driverCarType);

  return (
    <div className="ride-card animate-slide">
      <div className="ride-card-header">
        <div className="flex items-center gap-2">
          <span className="text-bold" style={{ fontSize: 15 }}>Ride #{ride.id}</span>
          <span className={`badge badge-${ride.rideType === 'INSTANT' ? 'matched' : 'initiated'}`}>
            {ride.rideType}
          </span>
          {ride.vehicleType && (
            <span className="badge badge-primary">
              {ride.vehicleType}
            </span>
          )}
        </div>
        <span className={`auction-timer ${isUrgent ? 'urgent' : ''}`}>
          <Clock size={12} />
          {remaining > 0 ? `${mins}:${secs.toString().padStart(2, '0')}` : 'EXPIRED'}
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
          <Navigation size={13} />
          {ride.estimatedDistanceKm?.toFixed(1) ?? '—'} km
        </span>
        <span className="ride-meta-item">
          <DollarSign size={13} />
          Base: <span className="text-gold text-bold">₹{ride.baseFare}</span>
        </span>
        {currentLowest && (
          <span className="ride-meta-item">
            🏆 Lowest: <span className="text-success text-bold text-mono">₹{currentLowest}</span>
          </span>
        )}
        {ride.createdAt && (
          <span className="ride-meta-item">
            <Clock size={13} />
            {formatDistanceToNow(new Date(ride.createdAt), { addSuffix: true })}
          </span>
        )}
      </div>

      {ride.rideType === 'AUCTION' && (
        <>
          {!compatible && driverCarType ? (
            <div style={{
              marginTop: 12,
              padding: '10px 14px',
              borderRadius: 'var(--radius-md)',
              background: 'rgba(255, 107, 107, 0.08)',
              border: '1px solid rgba(255, 107, 107, 0.25)',
              fontSize: 12,
              color: '#FF6B6B',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              lineHeight: 1.4,
            }}>
              <span>🚫</span>
              <span>
                Vehicle Mismatch: Passenger requested <strong>{ride.vehicleType || 'Any'}</strong>. Your vehicle is <strong>{driverCarType}</strong>. Only matching vehicle drivers can place bids.
              </span>
            </div>
          ) : remaining > 0 ? (
            <div className="bid-input-row">
              <div className="bid-input-wrap">
                <span className="currency">₹</span>
                <input
                  className="form-input"
                  type="number"
                  min="1"
                  placeholder={currentLowest && auctionData?.lowestDriverId && String(auctionData.lowestDriverId) !== 'NONE' ? String(Number(currentLowest) - 1) : String(ride.baseFare)}
                  value={bidAmount}
                  onChange={(e) => onBidChange(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && onSubmit()}
                  style={{ paddingLeft: 30, fontSize: 22, fontWeight: 700, color: '#F7B731', fontFamily: 'JetBrains Mono, monospace' }}
                />
              </div>
              <button
                className="btn btn-gold"
                onClick={onSubmit}
                disabled={isSubmitting || !bidAmount}
                style={{ minWidth: 100 }}
              >
                {isSubmitting ? <span className="spinner spinner-sm" /> : 'Place Bid'}
              </button>
            </div>
          ) : (
            <div style={{
              marginTop: 12,
              padding: '8px 12px',
              borderRadius: 'var(--radius-md)',
              background: 'rgba(255, 255, 255, 0.04)',
              border: '1px solid var(--border-subtle)',
              fontSize: 12,
              color: 'var(--text-muted)',
              textAlign: 'center',
            }}>
              ⏱️ Auction window has closed (Expired).
            </div>
          )}
        </>
      )}

      <button className="btn btn-ghost btn-sm w-full mt-2" onClick={onViewDetails}>
        View Details & Map
      </button>
    </div>
  );
};

export default LiveRidesPage;
