import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, MapPin, DollarSign, Clock, CheckCircle, XCircle, Navigation } from 'lucide-react';
import toast from 'react-hot-toast';
import { ridesApi, type Ride } from '../../lib/api';
import { useAuthStore } from '../../lib/store';
import RideMap from '../../components/RideMap';

const RideDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { userId, role } = useAuthStore();
  const [ride, setRide] = useState<Ride | null>(null);
  const [loading, setLoading] = useState(true);
  const [otpInput, setOtpInput] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  const rideId = Number(id);

  useEffect(() => {
    const fetch = async () => {
      try {
        const res = await ridesApi.getById(rideId);
        setRide(res.data);
      } catch {
        toast.error('Ride not found');
        navigate(-1);
      } finally {
        setLoading(false);
      }
    };
    fetch();
  }, [rideId]);

  const doAction = async (action: () => Promise<any>, successMsg: string) => {
    setActionLoading(true);
    try {
      const res = await action();
      setRide(res.data);
      toast.success(successMsg);
    } catch (err: any) {
      const msg = err.response?.data?.error ?? err.response?.data?.message ?? 'Action failed';
      toast.error(msg);
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 300 }}>
      <span className="spinner" />
    </div>
  );

  if (!ride) return null;

  const isDriver = role === 'DRIVER' && ride.driverId === userId;
  const isPassenger = role === 'PASSENGER' && ride.passengerId === userId;

  const statusSteps = ['INITIATED', 'MATCHED', 'ARRIVING', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED'];
  const currentStep = statusSteps.indexOf(ride.status);

  return (
    <div className="animate-fade">
      <div className="page-header">
        <div className="flex items-center gap-3">
          <button className="btn btn-ghost btn-icon" onClick={() => navigate(-1)}>
            <ArrowLeft size={16} />
          </button>
          <div>
            <h1 className="page-title" style={{ fontSize: 20 }}>Ride #{ride.id}</h1>
            <span className={`badge badge-${ride.status.toLowerCase()}`}>{ride.status}</span>
          </div>
        </div>
      </div>

      <div className="page-body" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 24 }}>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>

          <div className="card">
            <h3 style={{ marginBottom: 16, fontWeight: 700 }}>Trip Progress</h3>
            <div className="trip-progress">
              {statusSteps.slice(0, 5).map((step, i) => (
                <div key={step} className={`trip-step ${i < currentStep ? 'done' : i === currentStep ? 'active' : ''}`}>
                  {i < statusSteps.length - 2 && <div className="trip-connector" />}
                  <div className="trip-step-dot">
                    {i < currentStep ? <CheckCircle size={14} /> : i + 1}
                  </div>
                  <div className="trip-step-label" style={{ fontSize: 10, marginTop: 4 }}>
                    {step.replace('_', ' ')}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="card">
            <h3 style={{ marginBottom: 16, fontWeight: 700 }}>Route Details</h3>
            <div className="ride-route">
              <div className="ride-location">
                <span className="location-dot pickup" />
                <div>
                  <div style={{ fontWeight: 600 }}>{ride.pickupLocation}</div>
                  {ride.pickupLatitude && (
                    <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                      {ride.pickupLatitude.toFixed(4)}, {ride.pickupLongitude?.toFixed(4)}
                    </div>
                  )}
                </div>
              </div>
              <div className="ride-location">
                <span className="location-dot dest" />
                <div>
                  <div style={{ fontWeight: 600 }}>{ride.destinationLocation}</div>
                  {ride.destinationLatitude && (
                    <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                      {ride.destinationLatitude.toFixed(4)}, {ride.destinationLongitude?.toFixed(4)}
                    </div>
                  )}
                </div>
              </div>
            </div>
            <div className="flex items-center gap-4 mt-3" style={{ color: 'var(--text-secondary)', fontSize: 13 }}>
              {ride.estimatedDistanceKm && (
                <div className="flex items-center gap-2">
                  <Navigation size={13} />
                  {ride.estimatedDistanceKm.toFixed(2)} km
                </div>
              )}
              {ride.vehicleType && (
                <div className="flex items-center gap-2 font-bold text-primary">
                  🚕 {ride.vehicleType}
                </div>
              )}
            </div>
          </div>

          <div className="card">
            <h3 style={{ marginBottom: 16, fontWeight: 700 }}>Fare Breakdown</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <div className="flex justify-between">
                <span className="text-secondary">Base Fare</span>
                <span className="text-bold">₹{ride.baseFare}</span>
              </div>
              {ride.finalFare && (
                <div className="flex justify-between">
                  <span className="text-secondary">Final Agreed Fare</span>
                  <span className="fare-display">₹{ride.finalFare}</span>
                </div>
              )}
              {ride.finalFare && (
                <div className="flex justify-between" style={{ paddingTop: 8, borderTop: '1px solid var(--border-subtle)' }}>
                  <span className="text-secondary text-sm">Driver savings</span>
                  <span className="text-success text-sm">
                    ₹{(Number(ride.baseFare) - Number(ride.finalFare)).toFixed(2)} off
                  </span>
                </div>
              )}
            </div>
          </div>

          {ride.otp && isPassenger && ['MATCHED', 'ARRIVING', 'ARRIVED'].includes(ride.status) && (
            <div className="card" style={{ textAlign: 'center' }}>
              <h3 style={{ marginBottom: 4, fontWeight: 700 }}>Your Ride OTP</h3>
              <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 12 }}>Share this with your driver to start the trip</p>
              <div className="otp-display">
                {ride.otp.split('').map((digit, i) => (
                  <div key={i} className="otp-digit">{digit}</div>
                ))}
              </div>
            </div>
          )}

          {isDriver && (
            <div className="card">
              <h3 style={{ marginBottom: 16, fontWeight: 700 }}>Driver Actions</h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {ride.status === 'MATCHED' && (
                  <button className="btn btn-primary btn-full" onClick={() => doAction(() => ridesApi.markArriving(rideId), 'Marked as arriving!')} disabled={actionLoading}>
                    {actionLoading ? <span className="spinner spinner-sm" /> : '🚗 On My Way'}
                  </button>
                )}
                {(ride.status === 'ARRIVING' || ride.status === 'DRIVER_ARRIVING') && (
                  <button className="btn btn-primary btn-full" onClick={() => doAction(() => ridesApi.markArrived(rideId), 'Marked as arrived!')} disabled={actionLoading}>
                    {actionLoading ? <span className="spinner spinner-sm" /> : '📍 I Have Arrived'}
                  </button>
                )}
                <a
                  href={`https://www.google.com/maps/dir/?api=1&origin=${ride.pickupLatitude ?? ''},${ride.pickupLongitude ?? ''}&destination=${ride.destinationLatitude ?? ''},${ride.destinationLongitude ?? ''}&travelmode=driving`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="btn btn-secondary btn-full"
                  style={{ textDecoration: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: 10 }}
                >
                  🗺️ Turn-by-Turn Navigation (Google Maps)
                </a>
                {ride.status === 'ARRIVED' && (
                  <div>
                    <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 8 }}>Enter passenger OTP to start trip:</p>
                    <div className="flex gap-2">
                      <input
                        className="form-input"
                        placeholder="Enter 4-digit OTP"
                        value={otpInput}
                        maxLength={4}
                        onChange={(e) => setOtpInput(e.target.value)}
                      />
                      <button
                        className="btn btn-success"
                        onClick={() => doAction(() => ridesApi.startTrip(rideId, otpInput), 'Trip started!')}
                        disabled={actionLoading || otpInput.length < 4}
                      >
                        {actionLoading ? <span className="spinner spinner-sm" /> : 'Start'}
                      </button>
                    </div>
                  </div>
                )}
                {ride.status === 'IN_PROGRESS' && (
                  <button className="btn btn-success btn-full" onClick={() => doAction(() => ridesApi.complete(rideId), 'Ride completed! 🎉')} disabled={actionLoading}>
                    {actionLoading ? <span className="spinner spinner-sm" /> : '✅ Complete Trip'}
                  </button>
                )}
                {['MATCHED', 'ARRIVING', 'ARRIVED'].includes(ride.status) && (
                  <button className="btn btn-danger btn-full" onClick={() => doAction(() => ridesApi.cancel(rideId, 'Driver cancelled'), 'Ride cancelled')} disabled={actionLoading}>
                    <XCircle size={15} />
                    Cancel Ride
                  </button>
                )}
              </div>
            </div>
          )}
        </div>

        <div>
          <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
            <RideMap
              pickupLat={ride.pickupLatitude}
              pickupLng={ride.pickupLongitude}
              destLat={ride.destinationLatitude}
              destLng={ride.destinationLongitude}
              height={600}
            />
          </div>
        </div>
      </div>
    </div>
  );
};

export default RideDetailPage;
