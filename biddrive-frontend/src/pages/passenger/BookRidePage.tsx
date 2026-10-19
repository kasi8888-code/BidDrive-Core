import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { MapPin, ArrowLeft, Users, CheckCircle2, AlertCircle, RefreshCw } from 'lucide-react';
import toast from 'react-hot-toast';
import { ridesApi, type CreateRidePayload } from '../../lib/api';
import RideMap from '../../components/RideMap';

const LOCATIONS = [
  { label: 'Vit University Block-A, Melkottai', lat: 12.8406, lng: 80.1534 },
  { label: 'Station E Tambaram Railway Station Rd, Tambaram', lat: 12.9250, lng: 80.1170 },
  { label: 'SRM Kattankulathur Dental College And Hospital', lat: 12.8230, lng: 80.0440 },
  { label: 'Thaiyur koman nagar Thirupporur', lat: 12.7930, lng: 80.1830 },
  { label: 'T. Nagar Shopping Hub', lat: 13.0418, lng: 80.2341 },
  { label: 'Guindy Metro Station', lat: 13.0067, lng: 80.2026 },
  { label: 'OMR - Thoraipakkam IT Expressway', lat: 12.9398, lng: 80.2347 },
  { label: 'Chennai Airport Domestic Terminal', lat: 12.9941, lng: 80.1709 },
];

const VEHICLES = [
  { id: 'bike', name: 'Bike', icon: '🏍️', capacity: 1, multiplier: 0.62, eta: '4 min away • Drop 11:01 pm', description: 'Quick Bike rides' },
  { id: 'scooty', name: 'Scooty', icon: '🛵', capacity: 1, multiplier: 0.63, eta: '4 min away • Drop 11:01 pm', description: 'Comfortable scooter rides' },
  { id: 'auto', name: 'Auto', icon: '🛺', capacity: 3, multiplier: 0.95, eta: '4 min away • Drop 11:03 pm', description: 'Hassle-free Auto rides' },
  { id: 'economy', name: 'Cab Economy', icon: '🚗', capacity: 4, multiplier: 1.0, eta: '5 min away • Drop 11:04 pm', description: 'Pocket-friendly AC cabs' },
  { id: 'priority', name: 'Cab Priority', icon: '⚡', capacity: 4, multiplier: 1.18, eta: '5 min away • Drop 11:04 pm', description: 'Priority Pickup' },
];

const BookRidePage: React.FC = () => {
  const navigate = useNavigate();

  const [pickup, setPickup] = useState(LOCATIONS[0]);
  const [dest, setDest] = useState<{ label: string; lat: number; lng: number } | null>(null);
  const [selecting, setSelecting] = useState<'pickup' | 'dest'>('dest');

  const [estimate, setEstimate] = useState<{ distanceKm: number; suggestedBaseFare: number } | null>(null);
  const [estimating, setEstimating] = useState(false);
  const [selectedVehicle, setSelectedVehicle] = useState<string>('auto');
  const [loading, setLoading] = useState(false);
  const [showLocationList, setShowLocationList] = useState(false);
  const [searchingInstant, setSearchingInstant] = useState(false);
  const [searchStepText, setSearchStepText] = useState('');
  const [searchFailed, setSearchFailed] = useState(false);
  const [searchErrorMsg, setSearchErrorMsg] = useState('');

  useEffect(() => {
    if (pickup.lat && dest?.lat) {
      setEstimating(true);
      setShowLocationList(false);
      ridesApi.estimate({
        pickupLat: pickup.lat,
        pickupLng: pickup.lng,
        destinationLat: dest.lat,
        destinationLng: dest.lng,
      }).then((res) => {
        setEstimate(res.data);
      }).catch(() => {
        toast.error('Could not estimate fare');
      }).finally(() => setEstimating(false));
    } else {
      setEstimate(null);
      if (!showLocationList) setShowLocationList(true);
    }
  }, [pickup.lat, dest?.lat]);

  const handleBook = async (mode: 'AUCTION' | 'INSTANT') => {
    if (!pickup.lat || !dest?.lat || !estimate) {
      toast.error('Please select both locations');
      return;
    }

    const vehicle = VEHICLES.find(v => v.id === selectedVehicle);
    const fare = Math.round(estimate.suggestedBaseFare * (vehicle?.multiplier || 1.0));

    const payload: CreateRidePayload = {
      pickupLocation: pickup.label,
      destinationLocation: dest.label,
      pickupLatitude: pickup.lat,
      pickupLongitude: pickup.lng,
      destinationLatitude: dest.lat,
      destinationLongitude: dest.lng,
      baseFare: fare,
      rideType: mode,
      vehicleType: vehicle?.name,
    };

    if (mode === 'INSTANT') {
      setSearchingInstant(true);
      setSearchFailed(false);
      setSearchErrorMsg('');
      setLoading(true);

      try {
        setSearchStepText(`Scanning 5.0 km radius around ${pickup.label}...`);
        await new Promise(r => setTimeout(r, 600));

        setSearchStepText(`Pinging active ${vehicle?.name || 'drivers'} via Redis Geo Index...`);
        await new Promise(r => setTimeout(r, 600));

        setSearchStepText(`Evaluating nearest driver rating & ETA...`);
        const res = await ridesApi.createInstant(payload);

        setSearchStepText('Driver matched! Confirming trip...');
        await new Promise(r => setTimeout(r, 350));

        toast.success('Driver Confirmed! 🚀');
        navigate(`/passenger/rides/${res.data.id}`);
      } catch (err: any) {
        setSearchFailed(true);
        const errorText = err.response?.data?.error || err.response?.data?.message || 'No nearby drivers found within 5.0 km. All drivers are offline or busy.';
        setSearchErrorMsg(errorText);
      } finally {
        setLoading(false);
      }
    } else {
      setLoading(true);
      try {
        const res = await ridesApi.create(payload);
        toast.success('Auction is live 🚀');
        navigate(`/passenger/rides/${res.data.id}`);
      } catch (err: any) {
        toast.error(err.response?.data?.error || err.response?.data?.message || 'Failed to create ride');
      } finally {
        setLoading(false);
      }
    }
  };

  const getVehicleFare = (multiplier: number) => {
    if (!estimate) return 0;
    return Math.round(estimate.suggestedBaseFare * multiplier);
  };

  return (
    <div style={{ position: 'relative', width: '100%', height: 'calc(100vh - 60px)', display: 'flex', flexDirection: 'column', overflow: 'hidden', background: 'var(--bg-base)' }}>

      <div style={{ position: 'absolute', inset: 0, zIndex: 0 }}>
        <RideMap
          pickupLat={pickup.lat}
          pickupLng={pickup.lng}
          destLat={dest?.lat}
          destLng={dest?.lng}
          height="100%"
        />
      </div>

      <div style={{ position: 'absolute', top: 16, left: 16, right: 16, zIndex: 10, maxWidth: 600, margin: '0 auto' }}>
        <button
          onClick={() => navigate('/passenger')}
          style={{ width: 40, height: 40, background: 'var(--bg-elevated)', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 16, color: 'var(--text-primary)', border: '1px solid var(--border-subtle)', cursor: 'pointer' }}
        >
          <ArrowLeft size={20} />
        </button>

        <div className="card" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>

          <div
            style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 12, borderRadius: 12, border: selecting === 'pickup' ? '2px solid var(--brand-primary)' : '2px solid transparent', background: selecting === 'pickup' ? 'rgba(108,99,255,0.1)' : 'var(--bg-base)', cursor: 'pointer', transition: 'all 0.2s' }}
            onClick={() => { setSelecting('pickup'); setShowLocationList(true); }}
          >
            <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#43E97B' }} />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 10, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 4 }}>Pickup</div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {pickup.label || 'Search pickup location'}
              </div>
            </div>
          </div>

          <div
            style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 12, borderRadius: 12, border: selecting === 'dest' ? '2px solid var(--brand-primary)' : '2px solid transparent', background: selecting === 'dest' ? 'rgba(108,99,255,0.1)' : 'var(--bg-base)', cursor: 'pointer', transition: 'all 0.2s' }}
             onClick={() => { setSelecting('dest'); setShowLocationList(true); }}
          >
            <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#FF6B6B' }} />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 10, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 4 }}>Destination</div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {dest?.label || 'Where are you going?'}
              </div>
            </div>
          </div>
        </div>

        {showLocationList && (
           <div className="card" style={{ marginTop: 8, padding: 0, maxHeight: '40vh', overflowY: 'auto' }}>
             {LOCATIONS.filter(l => (selecting === 'pickup' ? l.label !== dest?.label : l.label !== pickup.label)).map(loc => (
               <div
                 key={loc.label}
                 style={{ display: 'flex', alignItems: 'center', gap: 16, padding: 16, borderBottom: '1px solid var(--border-subtle)', cursor: 'pointer', transition: 'background 0.2s' }}
                 onMouseEnter={e => (e.currentTarget.style.background = 'var(--bg-elevated)')}
                 onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                 onClick={() => {
                   if (selecting === 'pickup') {
                     setPickup(loc);
                     if (!dest) setSelecting('dest');
                   } else {
                     setDest(loc);
                     setShowLocationList(false);
                   }
                 }}
               >
                 <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'var(--bg-elevated)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)' }}>
                   <MapPin size={16} />
                 </div>
                 <div>
                   <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{loc.label}</div>
                   <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Chennai, Tamil Nadu</div>
                 </div>
               </div>
             ))}
           </div>
        )}
      </div>

      <div
        style={{
          position: 'absolute', bottom: 0, left: 0, right: 0, background: 'var(--bg-card)', borderTopLeftRadius: 24, borderTopRightRadius: 24, boxShadow: '0 -8px 32px rgba(0,0,0,0.5)', transition: 'transform 0.3s cubic-bezier(0.16, 1, 0.3, 1)', zIndex: 20, display: 'flex', flexDirection: 'column', maxHeight: '60vh', borderTop: '1px solid var(--border-subtle)',
          transform: estimate && !showLocationList ? 'translateY(0)' : 'translateY(120%)'
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 12, paddingBottom: 8 }}>
          <div style={{ width: 48, height: 6, background: 'var(--border-strong)', borderRadius: 999 }} />
        </div>

        {estimate && (
          <>
            <div style={{ padding: '0 20px 8px', textAlign: 'center' }}>
              <span className="badge badge-success" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '4px 12px', fontSize: 12 }}>
                <div className="status-dot active" />
                Total with ₹10 Cancellation fee
              </span>
            </div>

            <div style={{ flex: 1, overflowY: 'auto', padding: '0 16px 16px', display: 'flex', flexDirection: 'column', gap: 12 }}>
              {VEHICLES.map((vehicle) => {
                const isSelected = selectedVehicle === vehicle.id;
                return (
                  <div
                    key={vehicle.id}
                    onClick={() => setSelectedVehicle(vehicle.id)}
                    style={{ position: 'relative', display: 'flex', alignItems: 'center', padding: 12, borderRadius: 16, border: isSelected ? '2px solid var(--brand-primary)' : '2px solid transparent', background: isSelected ? 'rgba(108,99,255,0.1)' : 'var(--bg-base)', cursor: 'pointer', transition: 'all 0.2s' }}
                  >

                    <div style={{ width: 56, height: 56, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 32, marginRight: 12 }}>
                      {vehicle.icon}
                    </div>

                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span style={{ fontWeight: 800, color: 'var(--text-primary)' }}>{vehicle.name}</span>
                        <span style={{ display: 'flex', alignItems: 'center', fontSize: 12, color: 'var(--text-secondary)', fontWeight: 600 }}>
                          <Users size={12} style={{ marginRight: 2 }} /> {vehicle.capacity}
                        </span>
                      </div>
                      <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>
                        {vehicle.eta} away • {vehicle.description}
                      </div>
                    </div>

                    <div style={{ textAlign: 'right' }}>
                      <div style={{ fontWeight: 800, color: 'var(--text-primary)', fontSize: 18 }}>
                        ₹{getVehicleFare(vehicle.multiplier)}
                      </div>
                      {isSelected && (
                         <div style={{ position: 'absolute', right: 12, top: 12, color: 'var(--brand-primary)' }}>
                           <CheckCircle2 size={18} fill="currentColor" style={{ color: 'var(--bg-base)' }} />
                         </div>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>

            <div style={{ padding: 16, background: 'var(--bg-surface)', borderTop: '1px solid var(--border-subtle)', display: 'flex', gap: 12, paddingBottom: 32 }}>
              <button
                onClick={() => handleBook('AUCTION')}
                disabled={loading}
                className="btn btn-ghost"
                style={{ flex: 1, borderColor: 'var(--brand-primary)', color: 'var(--brand-primary)', padding: 14, fontSize: 14, opacity: loading ? 0.5 : 1 }}
              >
                {loading ? 'Processing...' : 'Bargain & Book'}
              </button>
              <button
                onClick={() => handleBook('INSTANT')}
                disabled={loading}
                className="btn btn-gold"
                style={{ flex: 1, padding: 14, fontSize: 14, opacity: loading ? 0.5 : 1 }}
              >
                {loading ? 'Processing...' : 'Book now'}
              </button>
            </div>
          </>
        )}
      </div>

      {searchingInstant && (
        <div
          data-testid="radar-searching-overlay"
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 9999,
            background: 'rgba(10, 14, 26, 0.85)',
            backdropFilter: 'blur(8px)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 24,
            animation: 'fadeIn 0.3s ease-out'
          }}
        >
          {!searchFailed ? (
            <div style={{
              background: 'var(--bg-card)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 24,
              padding: 36,
              maxWidth: 440,
              width: '100%',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              textAlign: 'center',
              boxShadow: '0 24px 64px rgba(0,0,0,0.6)',
              position: 'relative'
            }}>

              <div style={{
                position: 'relative',
                width: 140,
                height: 140,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                marginBottom: 28,
              }}>
                <div className="radar-ring" style={{ width: 130, height: 130 }} />
                <div className="radar-ring radar-ring-delay-1" style={{ width: 130, height: 130 }} />
                <div className="radar-ring radar-ring-delay-2" style={{ width: 130, height: 130 }} />
                <div className="radar-sweep-beam" />
                <div style={{
                  position: 'relative',
                  zIndex: 2,
                  width: 58,
                  height: 58,
                  borderRadius: '50%',
                  background: 'var(--brand-primary)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 28,
                  boxShadow: '0 0 24px rgba(108, 99, 255, 0.7)'
                }}>
                  {VEHICLES.find(v => v.id === selectedVehicle)?.icon || '🚗'}
                </div>
              </div>

              <div style={{ fontSize: 13, textTransform: 'uppercase', letterSpacing: 1.5, fontWeight: 700, color: 'var(--brand-primary)', marginBottom: 6 }}>
                Ola / Rapido Smart Dispatch
              </div>
              <h3 style={{ fontSize: 20, fontWeight: 800, color: 'var(--text-primary)', marginBottom: 12 }}>
                Searching Nearby Drivers...
              </h3>

              <div style={{
                background: 'var(--bg-base)',
                borderRadius: 12,
                padding: '12px 16px',
                width: '100%',
                fontSize: 13,
                color: 'var(--text-secondary)',
                border: '1px solid var(--border-subtle)',
                marginBottom: 20,
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                justifyContent: 'center'
              }}>
                <div className="status-dot active" />
                <span style={{ fontWeight: 600 }}>{searchStepText}</span>
              </div>

              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                Comparing Redis 5.0 km Geohash coordinates & driver ratings
              </div>
            </div>
          ) : (
            <div
              data-testid="no-drivers-modal"
              style={{
                background: 'var(--bg-card)',
                border: '1px solid rgba(239, 68, 68, 0.3)',
                borderRadius: 24,
                padding: 36,
                maxWidth: 440,
                width: '100%',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                textAlign: 'center',
                boxShadow: '0 24px 64px rgba(0,0,0,0.6)',
                position: 'relative'
              }}
            >
              <div style={{
                width: 64,
                height: 64,
                borderRadius: '50%',
                background: 'rgba(239, 68, 68, 0.15)',
                color: 'var(--accent-red, #ef4444)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                marginBottom: 20,
                border: '1px solid rgba(239, 68, 68, 0.3)'
              }}>
                <AlertCircle size={36} />
              </div>

              <h3 style={{ fontSize: 20, fontWeight: 800, color: 'var(--text-primary)', marginBottom: 8 }}>
                No Nearby Drivers Found
              </h3>

              <p style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.5, marginBottom: 20 }}>
                {searchErrorMsg || `All nearby ${VEHICLES.find(v => v.id === selectedVehicle)?.name || ''} drivers are currently offline or busy within 5.0 km of ${pickup.label}.`}
              </p>

              <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 10 }}>
                <button
                  onClick={() => {
                    setSearchingInstant(false);
                    setSearchFailed(false);
                    handleBook('AUCTION');
                  }}
                  className="btn btn-gold"
                  style={{ width: '100%', padding: 14, fontSize: 14, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}
                >
                  <span>⚡ Switch to Reverse Auction (Drivers Bid)</span>
                </button>

                <button
                  onClick={() => handleBook('INSTANT')}
                  className="btn btn-ghost"
                  style={{ width: '100%', padding: 12, fontSize: 14, borderColor: 'var(--border-strong)', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}
                >
                  <RefreshCw size={16} />
                  <span>Try Searching Again</span>
                </button>

                <button
                  onClick={() => {
                    setSearchingInstant(false);
                    setSearchFailed(false);
                  }}
                  style={{
                    background: 'transparent',
                    border: 'none',
                    color: 'var(--text-muted)',
                    fontSize: 13,
                    cursor: 'pointer',
                    padding: 8,
                    marginTop: 4
                  }}
                >
                  Cancel and change pickup
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default BookRidePage;
