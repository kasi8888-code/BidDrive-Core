import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Clock, DollarSign, CheckCircle, XCircle, Star, AlertTriangle } from 'lucide-react';
import toast from 'react-hot-toast';
import { ridesApi, bidsApi, ratingsApi, type Ride, type Bid } from '../../lib/api';
import { wsService } from '../../lib/websocket';
import RideMap from '../../components/RideMap';

const CANCEL_REASONS = [
  { id: 'DRIVER_DELAYED', label: '⏱️ Driver is taking too much time to arrive (Delayed)', reasonText: 'Driver is taking too much time to arrive' },
  { id: 'SIMULATION_FAILED', label: '🧪 Driver simulation failed / unresponsive', reasonText: 'Driver unresponsive / simulation failed' },
  { id: 'WRONG_DIRECTION', label: '📍 Driver is not moving or going the wrong way', reasonText: 'Driver not moving or heading wrong way' },
  { id: 'DRIVER_ASKED', label: '🚗 Driver requested to cancel', reasonText: 'Driver requested to cancel' },
  { id: 'CHANGED_MIND', label: '🔄 My plans changed / Booked by mistake', reasonText: 'Passenger changed plans' },
  { id: 'OTHER', label: '✏️ Other reason (specify details below)', reasonText: '' },
];

const PassengerRideDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const rideId = Number(id);

  const [ride, setRide] = useState<Ride | null>(null);
  const [bids, setBids] = useState<Bid[]>([]);
  const [remaining, setRemaining] = useState(90);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [rating, setRating] = useState(0);
  const [ratingSubmitted, setRatingSubmitted] = useState(false);
  const [searchingInstant, setSearchingInstant] = useState(false);
  const [searchStep, setSearchStep] = useState('Connecting to top-rated drivers...');
  const [simulating, setSimulating] = useState(false);
  const [simulatingDriver, setSimulatingDriver] = useState(false);
  const [driverSimFailed, setDriverSimFailed] = useState(false);

  const [showCancelModal, setShowCancelModal] = useState(false);
  const [cancelReasonCategory, setCancelReasonCategory] = useState('DRIVER_DELAYED');
  const [customCancelReason, setCustomCancelReason] = useState('');

  const refreshRide = async () => {
    const res = await ridesApi.getById(rideId);
    setRide(res.data);
    return res.data as Ride;
  };

  useEffect(() => {
    const load = async () => {
      try {
        const [rideRes, bidsRes, auctionRes] = await Promise.all([
          ridesApi.getById(rideId),
          bidsApi.getByRide(rideId),
          ridesApi.getAuction(rideId),
        ]);
        setRide(rideRes.data);
        setBids(bidsRes.data.sort((a: Bid, b: Bid) => Number(a.amount) - Number(b.amount)));
        if (auctionRes.data?.remainingSeconds) {
          setRemaining(Math.max(0, Number(auctionRes.data.remainingSeconds)));
        } else if (rideRes.data?.rideType === 'AUCTION' && rideRes.data?.status === 'INITIATED') {
          setRemaining(90);
        }
      } finally {
        setLoading(false);
      }
    };
    load();

    const unsubAuction = wsService.subscribe(`/topic/auction/${rideId}`, (event: any) => {
      if (event.lowestBid !== undefined) {
        if (event.remainingSeconds !== undefined) setRemaining(Math.max(0, Number(event.remainingSeconds)));
      }
      if (event.eventType === 'NEW_BID') {
        setBids((prev) => {
          const updated = [...prev.filter((b) => b.id !== event.bidId)];
          const newBid: Bid = { id: event.bidId, rideId, driverId: event.driverId, amount: event.lowestBid, status: 'PENDING', createdAt: new Date().toISOString() };
          return [...updated, newBid].sort((a, b) => Number(a.amount) - Number(b.amount));
        });
      }
      if (event.eventType === 'AUCTION_MATCHED') {
        toast.success(`🎉 Matched! Your OTP: ${event.otp}`, { duration: 10000 });
        refreshRide();
      }
      if (event.eventType === 'AUCTION_EXPIRED') {
        toast('Auction ended — no bids received');
        refreshRide();
      }
      if (['DRIVER_ARRIVING', 'DRIVER_ARRIVED', 'TRIP_STARTED', 'RIDE_COMPLETED', 'TRIP_CANCELLED'].includes(event.eventType)) {
        refreshRide();
      }
    });

    return () => { unsubAuction(); };
  }, [rideId]);

  useEffect(() => {
    if (remaining <= 0) {
      if (ride?.status === 'INITIATED') {
        refreshRide();
      }
      return;
    }
    const t = setInterval(() => setRemaining((r) => Math.max(0, r - 1)), 1000);
    return () => clearInterval(t);
  }, [remaining, ride?.status]);

  const acceptBid = async (bidId: number) => {
    setActionLoading(true);
    try {
      await bidsApi.accept(bidId);
      toast.success('Bid accepted! Driver is on the way 🚗');
      refreshRide();
    } catch (err: any) {
      toast.error(err.response?.data?.message ?? 'Accept failed');
    } finally {
      setActionLoading(false);
    }
  };

  const cancelRide = async (reasonOverride?: string) => {
    setActionLoading(true);
    try {
      let finalReason = reasonOverride;
      if (!finalReason) {
        const item = CANCEL_REASONS.find(r => r.id === cancelReasonCategory);
        if (cancelReasonCategory === 'OTHER') {
          finalReason = customCancelReason.trim() || 'Passenger cancelled (other reason)';
        } else {
          finalReason = item?.reasonText || 'Driver is taking too much time to arrive';
          if (customCancelReason.trim()) {
            finalReason += ` (${customCancelReason.trim()})`;
          }
        }
      }
      const res = await ridesApi.cancel(rideId, finalReason);
      setRide(res.data);
      setShowCancelModal(false);
      setCustomCancelReason('');
      toast.success(`Ride cancelled: ${finalReason}`);
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Cancel failed');
    } finally {
      setActionLoading(false);
    }
  };

  const submitRating = async (score: number) => {
    if (!ride?.driverId) return;
    try {
      await ratingsApi.submit({ rideId, targetId: ride.driverId, score, raterRole: 'PASSENGER' });
      setRatingSubmitted(true);
      toast.success('Rating submitted! ⭐');
    } catch {
      toast.error('Rating failed');
    }
  };

  const handleRetryAuction = async () => {
    if (!ride) return;
    setActionLoading(true);
    try {
      const newFare = Number(ride.baseFare) + 50;
      await ridesApi.retryAuction(ride.id, newFare);
      toast.success(`Auction restarted with max fare ₹${newFare}`);
      refreshRide();
    } catch (e: any) {
      toast.error(e.response?.data || 'Failed to retry auction');
    } finally {
      setActionLoading(false);
    }
  };

  const handleConvertToInstant = async () => {
    if (!ride) return;
    setSearchingInstant(true);
    setSearchStep('Locating top-rated nearby drivers...');
    try {

      await new Promise((r) => setTimeout(r, 1200));
      setSearchStep('Checking vehicle category & driver rating...');
      await new Promise((r) => setTimeout(r, 1000));
      setSearchStep('Driver found! Securing ride & generating PIN...');
      await ridesApi.convertToInstant(ride.id);
      toast.success('🎉 Driver matched successfully!');
      await refreshRide();
    } catch (e: any) {
      const msg = e.response?.data?.message || (typeof e.response?.data === 'string' ? e.response?.data : 'No drivers available for Instant Ride');
      toast.error(msg);
    } finally {
      setSearchingInstant(false);
      setActionLoading(false);
    }
  };

  const handleSimulateBids = async () => {
    if (!ride) return;
    setSimulating(true);
    try {
      await ridesApi.simulateBids(ride.id);
      toast.success('🚗 Simulated driver placed a bid!');
    } catch (err: any) {
      toast.error(err.response?.data?.message ?? 'Simulation failed. Register at least 1 driver first.');
    } finally {
      setSimulating(false);
    }
  };

  const handleSimulateDriverStep = async () => {
    if (!ride) return;
    setSimulatingDriver(true);
    try {
      const res = await ridesApi.simulateDriverStep(ride.id);
      setRide(res.data);
      setDriverSimFailed(false);
      if (res.data.status === 'DRIVER_ARRIVING') toast.success('🚗 Driver is on the way to pickup!');
      else if (res.data.status === 'ARRIVED') toast.success('📍 Driver has arrived outside!');
      else if (res.data.status === 'IN_PROGRESS') toast.success('🚀 OTP verified! Trip has started.');
      else if (res.data.status === 'COMPLETED') toast.success('🎉 Ride completed! Please rate your driver.');
    } catch (err: any) {
      setDriverSimFailed(true);
      toast.error(err.response?.data?.message || 'Driver simulation failed. You can cancel this ride.');
    } finally {
      setSimulatingDriver(false);
    }
  };

  if (loading) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 300 }}>
      <span className="spinner" />
    </div>
  );
  if (!ride) return null;

  const isAuction = ride.rideType === 'AUCTION';
  const mins = Math.floor(remaining / 60);
  const secs = remaining % 60;
  const isUrgent = remaining > 0 && remaining <= 30;
  const showAuction = isAuction && ['INITIATED'].includes(ride.status);

  return (
    <div className="animate-fade">
      <div className="page-header">
        <div className="flex items-center gap-3">
          <button className="btn btn-ghost btn-icon" onClick={() => navigate(-1)}>
            <ArrowLeft size={16} />
          </button>
          <div>
            <h1 className="page-title" style={{ fontSize: 20 }}>Ride #{ride.id}</h1>
            <div className="flex items-center gap-2">
              <span className={`badge badge-${ride.status.toLowerCase()}`}>{ride.status}</span>
              {showAuction && (
                <span className={`auction-timer ${isUrgent ? 'urgent' : ''}`}>
                  <Clock size={12} />
                  {mins}:{secs.toString().padStart(2, '0')}
                </span>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="page-body" style={{ display: 'grid', gridTemplateColumns: '1fr 1.2fr', gap: 24 }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>

          <div className="card">
            <h3 style={{ fontWeight: 700, marginBottom: 14 }}>Your Route</h3>
            <div className="ride-route">
              <div className="ride-location">
                <span className="location-dot pickup" />
                <span style={{ fontWeight: 600 }}>{ride.pickupLocation}</span>
              </div>
              <div className="ride-location">
                <span className="location-dot dest" />
                <span style={{ fontWeight: 600 }}>{ride.destinationLocation}</span>
              </div>
            </div>
            <div className="ride-meta">
              <span className="ride-meta-item">
                <DollarSign size={13} />
                <span>Max fare: <span className="text-gold text-bold">₹{ride.baseFare}</span></span>
              </span>
              {ride.vehicleType && (
                <span className="ride-meta-item">
                  <span className="text-primary text-bold" style={{ fontSize: 13 }}>🚕 {ride.vehicleType}</span>
                </span>
              )}
              {ride.finalFare && (
                <span className="ride-meta-item">
                  <CheckCircle size={13} />
                  <span>Final: <span className="text-success text-bold">₹{ride.finalFare}</span></span>
                </span>
              )}
            </div>
          </div>

          {['MATCHED', 'DRIVER_ARRIVING', 'ARRIVING', 'ARRIVED', 'IN_PROGRESS'].includes(ride.status) && (
            <div className="card" style={{
              background: 'linear-gradient(135deg, rgba(20, 24, 40, 0.95), rgba(30, 36, 60, 0.95))',
              border: '1.5px solid rgba(0, 210, 106, 0.5)',
              boxShadow: '0 8px 32px rgba(0, 210, 106, 0.15)',
              padding: '20px',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div style={{
                    width: 52, height: 52, borderRadius: '50%',
                    background: 'linear-gradient(135deg, #00D26A, #00A854)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: 24, color: '#fff', fontWeight: 800,
                    boxShadow: '0 4px 14px rgba(0, 210, 106, 0.4)'
                  }}>
                    {ride.driverName ? ride.driverName.charAt(0).toUpperCase() : '👨‍✈️'}
                  </div>
                  <div>
                    <div style={{ fontWeight: 800, fontSize: 17, color: '#fff', display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span>{ride.driverName || (ride.driverId ? `Driver #${ride.driverId}` : 'Assigned Driver')}</span>
                      <span className="badge badge-success" style={{ fontSize: 11, padding: '2px 8px' }}>
                        ⭐ {ride.driverRating ?? '5.0'}
                      </span>
                    </div>
                    <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 2 }}>
                      🚗 {ride.driverCarType || ride.vehicleType || 'Cab'} • <strong style={{ color: '#F7B731' }}>{ride.driverLicensePlate || 'Verified'}</strong>
                    </div>
                  </div>
                </div>

                <div style={{ textAlign: 'right' }}>
                  <div style={{
                    fontSize: 12, fontWeight: 700, color: '#00D26A',
                    background: 'rgba(0, 210, 106, 0.15)', padding: '6px 12px', borderRadius: 20,
                    border: '1px solid rgba(0, 210, 106, 0.3)', display: 'inline-block'
                  }}>
                    🟢 {ride.driverEta || 'Arriving in 3 min'}
                  </div>
                </div>
              </div>

              {ride.otp && (
                <div style={{
                  background: 'rgba(0, 0, 0, 0.3)', borderRadius: 12, padding: '12px 16px',
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16,
                  border: '1px dashed rgba(247, 183, 49, 0.5)'
                }}>
                  <div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                      BOARDING VERIFICATION PIN
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Share with driver upon arrival</div>
                  </div>
                  <div
                    id="boarding-pin"
                    style={{
                      fontFamily: "'JetBrains Mono', monospace", fontSize: 24, fontWeight: 900,
                      color: '#F7B731', letterSpacing: 4, background: 'rgba(247, 183, 49, 0.1)',
                      padding: '4px 14px', borderRadius: 8, border: '1px solid #F7B731'
                    }}
                  >
                    {ride.otp}
                  </div>
                </div>
              )}

              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                {ride.driverPhone && (
                  <a
                    href={`tel:${ride.driverPhone}`}
                    className="btn btn-secondary btn-sm"
                    style={{ flex: 1, textDecoration: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                  >
                    📞 Call Driver
                  </a>
                )}
                <a
                  href={`https://www.google.com/maps/dir/?api=1&origin=${ride.pickupLatitude ?? ''},${ride.pickupLongitude ?? ''}&destination=${ride.destinationLatitude ?? ''},${ride.destinationLongitude ?? ''}&travelmode=driving`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="btn btn-secondary btn-sm"
                  style={{ flex: 1, textDecoration: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                >
                  🗺️ Google Maps
                </a>
                {['MATCHED', 'DRIVER_ARRIVING', 'ARRIVING', 'ARRIVED'].includes(ride.status) && (
                  <button
                    className="btn btn-secondary btn-sm"
                    onClick={() => {
                      setCancelReasonCategory('DRIVER_DELAYED');
                      setShowCancelModal(true);
                    }}
                    disabled={actionLoading}
                    style={{
                      flex: 1,
                      textDecoration: 'none',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: 6,
                      color: '#FF6B6B',
                      borderColor: 'rgba(255, 107, 107, 0.4)',
                      background: 'rgba(255, 107, 107, 0.1)',
                    }}
                  >
                    <XCircle size={14} />
                    Cancel Ride
                  </button>
                )}
              </div>

              <div style={{
                marginTop: 16,
                padding: '12px 14px',
                background: 'rgba(0, 0, 0, 0.4)',
                border: '1px dashed rgba(108, 99, 255, 0.5)',
                borderRadius: 12,
              }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                  <span style={{ fontSize: 11, fontWeight: 800, color: '#6C63FF', letterSpacing: 0.5, textTransform: 'uppercase' }}>
                    🧪 Dev Simulator • Driver Controls
                  </span>
                  <span className={`badge badge-${ride.status.toLowerCase()}`} style={{ fontSize: 10 }}>
                    {ride.status}
                  </span>
                </div>
                <div>
                  {ride.status === 'MATCHED' && (
                    <button
                      className="btn btn-primary btn-sm w-full"
                      onClick={handleSimulateDriverStep}
                      disabled={simulatingDriver}
                      style={{ width: '100%', padding: '9px 12px' }}
                    >
                      {simulatingDriver ? 'Processing...' : '🚗 Simulate: Driver Heading to Pickup (Arriving)'}
                    </button>
                  )}
                  {(ride.status === 'DRIVER_ARRIVING' || ride.status === 'ARRIVING') && (
                    <button
                      className="btn btn-primary btn-sm w-full"
                      onClick={handleSimulateDriverStep}
                      disabled={simulatingDriver}
                      style={{ width: '100%', padding: '9px 12px', background: '#F7B731', color: '#000' }}
                    >
                      {simulatingDriver ? 'Processing...' : '📍 Simulate: Driver Arrived at Pickup Spot'}
                    </button>
                  )}
                  {ride.status === 'ARRIVED' && (
                    <button
                      className="btn btn-success btn-sm w-full"
                      onClick={handleSimulateDriverStep}
                      disabled={simulatingDriver}
                      style={{ width: '100%', padding: '9px 12px' }}
                    >
                      {simulatingDriver ? 'Processing...' : `🔑 Simulate: Driver Enters OTP (${ride.otp}) & Starts Trip`}
                    </button>
                  )}
                  {ride.status === 'IN_PROGRESS' && (
                    <button
                      className="btn btn-success btn-sm w-full"
                      onClick={handleSimulateDriverStep}
                      disabled={simulatingDriver}
                      style={{ width: '100%', padding: '9px 12px', background: 'linear-gradient(135deg, #00D26A, #00A854)' }}
                    >
                      {simulatingDriver ? 'Processing...' : '🏁 Simulate: Reached Destination & Complete Trip'}
                    </button>
                  )}
                </div>

                {driverSimFailed && (
                  <div style={{
                    marginTop: 10,
                    padding: '10px 12px',
                    background: 'rgba(255, 71, 87, 0.12)',
                    border: '1px solid rgba(255, 71, 87, 0.35)',
                    borderRadius: 8,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 10,
                  }}>
                    <div style={{ fontSize: 12, color: '#FF6B6B' }}>
                      ⚠️ Driver taking too long or simulation failed?
                    </div>
                    <button
                      className="btn btn-danger btn-sm"
                      onClick={() => {
                        setCancelReasonCategory('DRIVER_DELAYED');
                        setShowCancelModal(true);
                      }}
                      style={{ padding: '4px 10px', fontSize: 11, whiteSpace: 'nowrap' }}
                    >
                      Cancel Ride
                    </button>
                  </div>
                )}

                <div style={{ fontSize: 10, color: 'var(--text-muted)', marginTop: 6, textAlign: 'center' }}>
                  Simulates driver mobile actions directly on this screen without requiring a second phone.
                </div>
              </div>
            </div>
          )}

          {showAuction && (
            <div className="card" style={{
              background: 'linear-gradient(135deg, rgba(108,99,255,0.12), rgba(247,183,49,0.08))',
              border: '1.5px solid rgba(247,183,49,0.4)',
              boxShadow: '0 8px 32px rgba(247,183,49,0.15)',
              padding: '22px',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div style={{
                    width: 44, height: 44, borderRadius: '50%',
                    background: 'rgba(247,183,49,0.15)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    border: '2px solid #F7B731',
                    fontSize: 20,
                  }}>
                    📡
                  </div>
                  <div>
                    <div style={{ fontWeight: 800, fontSize: 16, color: '#fff', display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span>REVERSE AUCTION</span>
                      <span className="badge badge-warning" style={{ fontSize: 10, padding: '2px 8px' }}>LIVE</span>
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                      Broadcasting to drivers within 5 km
                    </div>
                  </div>
                </div>

                <div style={{ textAlign: 'right' }}>
                  <div style={{
                    fontSize: 32,
                    fontWeight: 900,
                    fontFamily: "'JetBrains Mono', monospace",
                    color: isUrgent ? '#FF4757' : '#F7B731',
                    textShadow: isUrgent ? '0 0 15px rgba(255,71,87,0.6)' : '0 0 15px rgba(247,183,49,0.4)',
                    lineHeight: 1,
                  }}>
                    {mins}:{secs.toString().padStart(2, '0')}
                  </div>
                  <div style={{ fontSize: 10, color: isUrgent ? '#FF4757' : 'var(--text-muted)', fontWeight: 700, marginTop: 4, letterSpacing: 0.5 }}>
                    {isUrgent ? '⚠️ CLOSING SOON' : 'AUCTION WINDOW (90s)'}
                  </div>
                </div>
              </div>

              <div style={{
                width: '100%',
                height: 8,
                background: 'rgba(255,255,255,0.08)',
                borderRadius: 10,
                overflow: 'hidden',
                marginBottom: 14,
              }}>
                <div style={{
                  height: '100%',
                  width: `${Math.min(100, Math.max(0, (remaining / 90) * 100))}%`,
                  background: isUrgent
                    ? 'linear-gradient(90deg, #FF6B6B, #FF4757)'
                    : 'linear-gradient(90deg, #6C63FF, #F7B731)',
                  borderRadius: 10,
                  transition: 'width 1s linear',
                  boxShadow: isUrgent ? '0 0 10px #FF4757' : '0 0 10px #F7B731',
                }} />
              </div>

              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(3, 1fr)',
                gap: 10,
                background: 'rgba(0,0,0,0.25)',
                padding: '10px 14px',
                borderRadius: 'var(--radius-md)',
                fontSize: 12,
              }}>
                <div>
                  <div style={{ color: 'var(--text-muted)', fontSize: 11 }}>Max Fare Ceiling</div>
                  <div style={{ fontWeight: 700, fontSize: 14, color: '#fff' }}>₹{ride.baseFare}</div>
                </div>
                <div>
                  <div style={{ color: 'var(--text-muted)', fontSize: 11 }}>Lowest Bid</div>
                  <div style={{ fontWeight: 700, fontSize: 14, color: bids.length > 0 ? '#00D26A' : '#F7B731' }}>
                    {bids.length > 0 ? `₹${bids[0].amount}` : 'Waiting for bids...'}
                  </div>
                </div>
                <div>
                  <div style={{ color: 'var(--text-muted)', fontSize: 11 }}>Competing Drivers</div>
                  <div style={{ fontWeight: 700, fontSize: 14, color: '#6C63FF' }}>{bids.length} drivers</div>
                </div>
              </div>

              <div style={{ marginTop: 14, display: 'flex', flexDirection: 'column', gap: 8 }}>
                <button
                  className="btn btn-gold btn-sm w-full"
                  style={{ width: '100%', padding: '11px 14px', fontSize: 13, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                  onClick={handleConvertToInstant}
                  disabled={actionLoading || searchingInstant}
                >
                  ⚡ Don't want to wait? Switch to Instant Ride (Ola/Rapido style)
                </button>
                <button
                  className="btn btn-ghost btn-sm"
                  style={{ width: '100%', padding: '8px 12px', fontSize: 12, opacity: 0.85, border: '1px dashed var(--border-subtle)' }}
                  onClick={handleSimulateBids}
                  disabled={simulating}
                >
                  {simulating ? 'Simulating...' : '🧪 Simulate Competing Driver Bid (Test Tool)'}
                </button>
              </div>
            </div>
          )}

          {/* Live Bids */}
          {showAuction && (
            <div className="card">
              <div className="flex items-center justify-between mb-3">
                <h3 style={{ fontWeight: 700 }}>Live Bids</h3>
                <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{bids.length} bids</span>
              </div>
              {bids.length === 0 ? (
                <div className="empty-state" style={{ padding: '24px 0' }}>
                  <div style={{ fontSize: 28, marginBottom: 8 }}>⏳</div>
                  <div className="text-secondary">Waiting for drivers to bid...</div>
                </div>
              ) : (
                <div className="bid-list">
                  {bids.map((bid, i) => (
                    <div key={bid.id} className={`bid-item ${bid.status === 'ACCEPTED' ? 'winner' : ''}`}>
                      <div className={`bid-rank ${i === 0 ? 'first' : ''}`}>{i + 1}</div>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontWeight: 700, fontSize: 16, color: '#F7B731' }}>₹{bid.amount}</div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Driver #{bid.driverId}</div>
                      </div>
                      {bid.status === 'ACCEPTED' ? (
                        <span className="badge badge-matched"><CheckCircle size={11} /> Accepted</span>
                      ) : (
                        <button
                          className="btn btn-success btn-sm"
                          onClick={() => acceptBid(bid.id)}
                          disabled={actionLoading}
                        >
                          Accept
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Expired Auction Options - Prominent Ola/Rapido Style */}
          {(ride.status === 'EXPIRED' || (isAuction && remaining <= 0 && ride.status === 'INITIATED')) && (
            <div className="card" style={{
              background: 'linear-gradient(135deg, rgba(30, 20, 25, 0.95), rgba(40, 20, 30, 0.95))',
              borderColor: 'var(--danger-glow)', borderWidth: 1.5,
              boxShadow: '0 8px 32px rgba(255, 71, 87, 0.2)', padding: '24px'
            }}>
              <div style={{ textAlign: 'center', marginBottom: 18 }}>
                <div style={{ fontSize: 36, marginBottom: 8 }}>⏰</div>
                <h3 style={{ color: '#FF4757', fontWeight: 800, fontSize: 20, marginBottom: 6 }}>Auction Window Ended</h3>
                <p style={{ fontSize: 13, color: 'var(--text-secondary)', maxWidth: 360, margin: '0 auto' }}>
                  No drivers accepted your ride at the base fare of <strong>₹{ride.baseFare}</strong>.
                </p>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <button
                  className="btn btn-gold btn-lg"
                  onClick={handleConvertToInstant}
                  disabled={actionLoading || searchingInstant}
                  style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: 14 }}
                >
                  ⚡ Book Normal Ride (Instant Match like Ola / Rapido)
                </button>

                <button
                  className="btn btn-primary"
                  onClick={handleRetryAuction}
                  disabled={actionLoading}
                  style={{ padding: 12 }}
                >
                  🚀 Raise Fare by ₹50 (to ₹{Number(ride.baseFare) + 50}) & Restart Auction
                </button>

                <button
                  className="btn btn-ghost btn-sm"
                  onClick={() => {
                    setCancelReasonCategory('CHANGED_MIND');
                    setShowCancelModal(true);
                  }}
                  disabled={actionLoading}
                  style={{ color: 'var(--text-muted)' }}
                >
                  Cancel Ride
                </button>
              </div>
            </div>
          )}

          {/* Cancel Option for Active Rides (INITIATED, MATCHED, DRIVER_ARRIVING, ARRIVING, ARRIVED) */}
          {['INITIATED', 'MATCHED', 'DRIVER_ARRIVING', 'ARRIVING', 'ARRIVED'].includes(ride.status) && (
            <button
              className="btn btn-danger"
              onClick={() => {
                setCancelReasonCategory('DRIVER_DELAYED');
                setShowCancelModal(true);
              }}
              disabled={actionLoading}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 8,
                padding: '12px 16px',
                fontSize: 14,
                fontWeight: 700,
                boxShadow: '0 4px 14px rgba(255, 71, 87, 0.25)',
              }}
            >
              <XCircle size={16} />
              Cancel Ride (Specify Reason)
            </button>
          )}

          {/* Dedicated Cancelled Ride State Card */}
          {ride.status === 'CANCELLED' && (
            <div className="card" style={{
              background: 'linear-gradient(135deg, rgba(30, 20, 25, 0.95), rgba(40, 20, 30, 0.95))',
              border: '1.5px solid rgba(255, 71, 87, 0.4)',
              boxShadow: '0 8px 32px rgba(255, 71, 87, 0.2)',
              padding: '24px',
              textAlign: 'center',
            }}>
              <div style={{
                width: 56, height: 56, borderRadius: '50%',
                background: 'rgba(255, 71, 87, 0.15)',
                color: '#FF4757',
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                marginBottom: 14, border: '1px solid rgba(255, 71, 87, 0.3)'
              }}>
                <XCircle size={32} />
              </div>
              <h3 style={{ fontSize: 20, fontWeight: 800, color: '#FF4757', marginBottom: 6 }}>
                Ride Cancelled
              </h3>
              <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 12 }}>
                Cancelled by: <strong style={{ color: '#fff' }}>{ride.cancelledBy || 'Passenger'}</strong>
              </div>
              {ride.cancellationReason && (
                <div style={{
                  background: 'rgba(0, 0, 0, 0.35)',
                  border: '1px dashed rgba(255, 71, 87, 0.4)',
                  borderRadius: 10,
                  padding: '10px 16px',
                  fontSize: 13,
                  color: '#F7B731',
                  marginBottom: 20,
                  display: 'inline-block',
                }}>
                  Reason: <strong>{ride.cancellationReason}</strong>
                </div>
              )}
              <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
                <button className="btn btn-primary btn-sm" onClick={() => navigate('/passenger/book')}>
                  🚕 Book Another Ride
                </button>
                <button className="btn btn-secondary btn-sm" onClick={() => navigate('/passenger/my-rides')}>
                  📋 View My Rides
                </button>
              </div>
            </div>
          )}

          {/* Rating */}
          {ride.status === 'COMPLETED' && !ratingSubmitted && ride.driverId && (
            <div className="card" style={{ textAlign: 'center' }}>
              <h3 style={{ fontWeight: 700, marginBottom: 4 }}>Rate Your Driver</h3>
              <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 16 }}>How was your experience?</p>
              <div style={{ display: 'flex', gap: 8, justifyContent: 'center' }}>
                {[1, 2, 3, 4, 5].map((s) => (
                  <button
                    key={s}
                    onClick={() => { setRating(s); submitRating(s); }}
                    style={{
                      background: 'none', border: 'none', cursor: 'pointer',
                      fontSize: 28, filter: s <= rating ? 'none' : 'grayscale(1)',
                      transition: 'transform 0.1s',
                    }}
                    onMouseEnter={(e) => { (e.target as any).style.transform = 'scale(1.3)'; setRating(s); }}
                    onMouseLeave={(e) => { (e.target as any).style.transform = 'scale(1)'; }}
                  >
                    ⭐
                  </button>
                ))}
              </div>
            </div>
          )}
          {ratingSubmitted && (
            <div className="alert alert-success">
              <CheckCircle size={16} />
              Thanks for rating! Your feedback helps improve the platform.
            </div>
          )}
        </div>

        {/* Map with Live Driver Location Marker */}
        <div>
          <div className="card" style={{ padding: 0, overflow: 'hidden', position: 'sticky', top: 20 }}>
            <RideMap
              pickupLat={ride.pickupLatitude}
              pickupLng={ride.pickupLongitude}
              destLat={ride.destinationLatitude}
              destLng={ride.destinationLongitude}
              driverLat={ride.driverLatitude ?? (ride.pickupLatitude && ['MATCHED', 'DRIVER_ARRIVING', 'ARRIVING', 'ARRIVED', 'IN_PROGRESS'].includes(ride.status) ? ride.pickupLatitude + 0.007 : undefined)}
              driverLng={ride.driverLongitude ?? (ride.pickupLongitude && ['MATCHED', 'DRIVER_ARRIVING', 'ARRIVING', 'ARRIVED', 'IN_PROGRESS'].includes(ride.status) ? ride.pickupLongitude + 0.007 : undefined)}
              height={600}
            />
          </div>
        </div>
      </div>

      {/* Realistic Searching Radar Overlay for Normal Ride Booking */}
      {searchingInstant && (
        <div style={{
          position: 'fixed', inset: 0, zIndex: 1000,
          background: 'rgba(10, 12, 20, 0.88)', backdropFilter: 'blur(10px)',
          display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
          padding: 24, textAlign: 'center'
        }}>
          <div style={{
            position: 'relative', width: 110, height: 110, marginBottom: 28,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <div style={{
              position: 'absolute', inset: 0, borderRadius: '50%',
              background: 'radial-gradient(circle, rgba(0,210,106,0.3) 0%, rgba(0,210,106,0) 70%)',
              animation: 'ping 1.5s cubic-bezier(0, 0, 0.2, 1) infinite',
            }} />
            <div style={{
              width: 80, height: 80, borderRadius: '50%',
              background: 'linear-gradient(135deg, #00D26A, #00A854)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 36, boxShadow: '0 0 35px rgba(0, 210, 106, 0.6)',
              zIndex: 1,
            }}>
              🚕
            </div>
          </div>

          <h2 style={{ fontSize: 24, fontWeight: 900, color: '#fff', marginBottom: 10, letterSpacing: -0.5 }}>
            Booking Normal Ride
          </h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: 15, maxWidth: 360, minHeight: 44, margin: '0 auto 20px' }}>
            {searchStep}
          </p>

          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 8,
            background: 'rgba(255, 255, 255, 0.06)', padding: '8px 16px', borderRadius: 20,
            border: '1px solid rgba(255, 255, 255, 0.1)', fontSize: 13, color: 'var(--text-muted)'
          }}>
            <div className="status-dot active" />
            <span>Scanning top-rated drivers in 5 km radius</span>
          </div>
        </div>
      )}

      {/* Cancellation Reason Modal */}
      {showCancelModal && (
        <div style={{
          position: 'fixed', inset: 0, zIndex: 1100,
          background: 'rgba(10, 12, 20, 0.85)', backdropFilter: 'blur(8px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          padding: 20,
        }}>
          <div style={{
            background: 'var(--bg-card, #1A1D2D)',
            border: '1.5px solid rgba(255, 71, 87, 0.4)',
            borderRadius: 20,
            padding: '28px',
            maxWidth: 500,
            width: '100%',
            boxShadow: '0 24px 64px rgba(0, 0, 0, 0.7)',
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
            animation: 'fadeIn 0.15s ease-out',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div style={{
                width: 44, height: 44, borderRadius: '50%',
                background: 'rgba(255, 71, 87, 0.15)',
                color: '#FF4757',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                border: '1px solid rgba(255, 71, 87, 0.3)',
                flexShrink: 0
              }}>
                <XCircle size={24} />
              </div>
              <div>
                <h3 style={{ fontSize: 18, fontWeight: 800, color: '#fff', margin: 0 }}>
                  Cancel Ride #{ride.id}
                </h3>
                <p style={{ fontSize: 12, color: 'var(--text-secondary)', margin: '4px 0 0 0' }}>
                  Please tell us why you want to cancel. Drivers are notified immediately.
                </p>
              </div>
            </div>

            {/* Predefined Reason Options */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 4 }}>
              {CANCEL_REASONS.map((r) => {
                const isSelected = cancelReasonCategory === r.id;
                return (
                  <div
                    key={r.id}
                    onClick={() => setCancelReasonCategory(r.id)}
                    style={{
                      padding: '10px 14px',
                      borderRadius: 10,
                      background: isSelected ? 'rgba(255, 71, 87, 0.15)' : 'rgba(255, 255, 255, 0.03)',
                      border: isSelected ? '1.5px solid #FF4757' : '1px solid rgba(255, 255, 255, 0.08)',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      transition: 'all 0.15s ease',
                    }}
                  >
                    <input
                      type="radio"
                      name="cancelReason"
                      checked={isSelected}
                      onChange={() => setCancelReasonCategory(r.id)}
                      style={{ cursor: 'pointer', accentColor: '#FF4757' }}
                    />
                    <span style={{ fontSize: 13, fontWeight: isSelected ? 700 : 500, color: isSelected ? '#fff' : 'var(--text-secondary)' }}>
                      {r.label}
                    </span>
                  </div>
                );
              })}
            </div>

            {/* Custom Notes / Specific Reason Input */}
            <div>
              <label style={{ fontSize: 12, color: 'var(--text-muted)', display: 'block', marginBottom: 6 }}>
                {cancelReasonCategory === 'OTHER' ? 'Specify your reason (Required):' : 'Additional details (Optional):'}
              </label>
              <textarea
                value={customCancelReason}
                onChange={(e) => setCustomCancelReason(e.target.value)}
                placeholder={cancelReasonCategory === 'OTHER' ? 'e.g. Driver stopped answering, car breakdown, wrong address...' : 'e.g. Waited more than 10 mins without driver moving...'}
                rows={2}
                style={{
                  width: '100%',
                  background: 'rgba(0, 0, 0, 0.3)',
                  border: '1px solid rgba(255, 255, 255, 0.12)',
                  borderRadius: 8,
                  padding: '8px 12px',
                  color: '#fff',
                  fontSize: 13,
                  outline: 'none',
                  resize: 'none',
                  boxSizing: 'border-box'
                }}
              />
            </div>

            {/* Warning note */}
            <div style={{
              fontSize: 11,
              color: 'var(--text-muted)',
              background: 'rgba(255, 255, 255, 0.03)',
              padding: '8px 12px',
              borderRadius: 6,
              borderLeft: '3px solid #FF4757'
            }}>
              ⚠️ Cancelling will immediately release the driver and update your trip status to Cancelled.
            </div>

            {/* Action Buttons */}
            <div style={{ display: 'flex', gap: 10, marginTop: 4 }}>
              <button
                className="btn btn-secondary"
                onClick={() => setShowCancelModal(false)}
                disabled={actionLoading}
                style={{ flex: 1, padding: 11 }}
              >
                Keep Ride
              </button>
              <button
                className="btn btn-danger"
                onClick={() => cancelRide()}
                disabled={actionLoading || (cancelReasonCategory === 'OTHER' && !customCancelReason.trim())}
                style={{ flex: 1.3, padding: 11, fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
              >
                <XCircle size={15} />
                {actionLoading ? 'Cancelling...' : 'Confirm Cancellation'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PassengerRideDetail;
