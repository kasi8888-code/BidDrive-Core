import React, { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Search, MapPin, Clock, ArrowLeft, Users, CheckCircle2,
  AlertCircle, RefreshCw, Menu, Heart, Compass, Shield,
  Phone, Star, ChevronRight, X, Zap, LogOut, Car, Bike, Package
} from 'lucide-react';
import { ridesApi, bidsApi, type Ride, type Bid } from '../../lib/api';
import { useAuthStore } from '../../lib/store';
import { wsService } from '../../lib/websocket';
import RideMap from '../../components/RideMap';
import toast from 'react-hot-toast';

interface LocationItem {
  id: string;
  label: string;
  subtitle: string;
  lat: number;
  lng: number;
}

const PREDEFINED_LOCATIONS: LocationItem[] = [
  {
    id: 'loc-vit',
    label: 'Vit University Block-A, Melkottai',
    subtitle: 'Vandalur-Kelambakkam Rd, Chennai',
    lat: 12.8406,
    lng: 80.1534,
  },
  {
    id: 'loc-tambaram',
    label: 'Station E Tambaram Railway Station Rd',
    subtitle: 'Railway Station Road, Tambaram, Chennai',
    lat: 12.9250,
    lng: 80.1170,
  },
  {
    id: 'loc-srm',
    label: 'SRM Kattankulathur Dental College And Hospital',
    subtitle: 'Potheri, SRM Nagar, Kattankulathur',
    lat: 12.8230,
    lng: 80.0440,
  },
  {
    id: 'loc-thaiyur',
    label: 'Thaiyur koman nagar Thirupporur',
    subtitle: 'Old Mahabalipuram Rd, Chengalpattu',
    lat: 12.7930,
    lng: 80.1830,
  },
  {
    id: 'loc-omr',
    label: 'OMR - Thoraipakkam IT Corridor',
    subtitle: 'Rajiv Gandhi Salai, Chennai',
    lat: 12.9398,
    lng: 80.2347,
  },
  {
    id: 'loc-airport',
    label: 'Chennai Airport Domestic Terminal',
    subtitle: 'Meenambakkam, Chennai',
    lat: 12.9941,
    lng: 80.1709,
  },
  {
    id: 'loc-guindy',
    label: 'Guindy Metro & Industrial Estate',
    subtitle: 'Guindy, Chennai',
    lat: 13.0067,
    lng: 80.2026,
  },
  {
    id: 'loc-tnagar',
    label: 'T. Nagar Ranganathan Shopping Hub',
    subtitle: 'T. Nagar, Chennai',
    lat: 13.0418,
    lng: 80.2341,
  },
];

interface VehicleCategory {
  id: string;
  name: string;
  icon: string;
  capacity: number;
  desc: string;
  eta: string;
  defaultFare: number;
  multiplier: number;
  badge?: string;
}

const VEHICLES: VehicleCategory[] = [
  {
    id: 'bike',
    name: 'Bike',
    icon: '🏍️',
    capacity: 1,
    desc: 'Quick Bike rides',
    eta: '4 min away • Drop 11:01 pm',
    defaultFare: 198,
    multiplier: 0.62,
  },
  {
    id: 'scooty',
    name: 'Scooty',
    icon: '🛵',
    capacity: 1,
    desc: 'Comfortable scooter rides',
    eta: '4 min away • Drop 11:01 pm',
    defaultFare: 199,
    multiplier: 0.63,
  },
  {
    id: 'auto',
    name: 'Auto',
    icon: '🛺',
    capacity: 3,
    desc: 'Hassle-free Auto rides',
    eta: '4 min away • Drop 11:03 pm',
    defaultFare: 299,
    multiplier: 0.95,
    badge: 'Preferred',
  },
  {
    id: 'economy',
    name: 'Cab Economy',
    icon: '🚗',
    capacity: 4,
    desc: 'Pocket-friendly AC cabs',
    eta: '5 min away • Drop 11:04 pm',
    defaultFare: 312,
    multiplier: 1.0,
  },
  {
    id: 'priority',
    name: 'Cab Priority',
    icon: '⚡',
    capacity: 4,
    desc: 'Priority Pickup',
    eta: '5 min away • Drop 11:04 pm',
    defaultFare: 366,
    multiplier: 1.18,
  },
];

const PassengerDashboard: React.FC = () => {
  const navigate = useNavigate();
  const { userId, email, logout } = useAuthStore();

  const [pickup, setPickup] = useState<LocationItem>(PREDEFINED_LOCATIONS[0]);
  const [dest, setDest] = useState<LocationItem | null>(null);
  const [selectedVehicle, setSelectedVehicle] = useState<string>('auto');
  const [estimate, setEstimate] = useState<{ distanceKm: number; suggestedBaseFare: number } | null>(null);

  const [showSearchModal, setShowSearchModal] = useState(false);
  const [searchTarget, setSearchTarget] = useState<'pickup' | 'dest'>('dest');
  const [searchQuery, setSearchQuery] = useState('');
  const [showDrawer, setShowDrawer] = useState(false);
  const [activeTab, setActiveTab] = useState<'home' | 'parcel' | 'electric' | 'wallet'>('home');

  const [myRides, setMyRides] = useState<Ride[]>([]);
  const [activeRide, setActiveRide] = useState<Ride | null>(null);
  const [bidsList, setBidsList] = useState<Bid[]>([]);
  const [remainingSeconds, setRemainingSeconds] = useState<number>(90);

  const [bookingLoading, setBookingLoading] = useState(false);
  const [actionSimulating, setActionSimulating] = useState(false);

  const loadRides = async () => {
    try {
      const res = await ridesApi.getAll();
      const mine = res.data.filter((r: Ride) => r.passengerId === userId);
      setMyRides(mine.sort((a: Ride, b: Ride) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));

      const ongoing = mine.find((r: Ride) =>
        ['INITIATED', 'MATCHED', 'DRIVER_ARRIVING', 'ARRIVING', 'ARRIVED', 'IN_PROGRESS'].includes(r.status)
      );
      setActiveRide(ongoing || null);

      if (ongoing && ongoing.status === 'INITIATED') {
        loadBids(ongoing.id);
      }
    } catch (e) {
      console.error('Could not load rides', e);
    }
  };

  const loadBids = async (rideId: number) => {
    try {
      const res = await bidsApi.getByRide(rideId);
      setBidsList(res.data.sort((a: Bid, b: Bid) => a.amount - b.amount));
    } catch (e) {
      console.error('Could not load bids', e);
    }
  };

  useEffect(() => {
    loadRides();

    const unsub = wsService.subscribe('/user/queue/notifications', (event: any) => {
      if (event.eventType === 'AUCTION_MATCHED') {
        toast.success(`🎉 Driver Matched! OTP: ${event.otp}`, { duration: 6000 });
        loadRides();
      } else if (event.eventType === 'NEW_BID') {
        toast('New driver bid received! 🏷️', { icon: '⚡' });
        if (activeRide?.id) loadBids(activeRide.id);
      } else if (event.eventType === 'DRIVER_ARRIVING') {
        toast('Driver is on the way! 🚗');
        loadRides();
      } else if (event.eventType === 'DRIVER_ARRIVED') {
        toast('Driver has arrived at pickup! 📍');
        loadRides();
      } else if (event.eventType === 'TRIP_STARTED') {
        toast.success('Trip started! Have a safe ride 🚀');
        loadRides();
      } else if (event.eventType === 'RIDE_COMPLETED') {
        toast.success('You have arrived at your destination! ✨');
        loadRides();
      }
    });

    return () => unsub();
  }, [userId]);

  useEffect(() => {
    if (!activeRide) return;
    const interval = setInterval(() => {
      if (activeRide.status === 'INITIATED') {
        ridesApi.getById(activeRide.id).then((res) => {
          setActiveRide(res.data);
          if (res.data.remainingSeconds !== undefined) {
            setRemainingSeconds(res.data.remainingSeconds);
          }
          if (res.data.status !== 'INITIATED') {
            loadRides();
          }
        }).catch(() => {});
        loadBids(activeRide.id);
      } else if (['MATCHED', 'DRIVER_ARRIVING', 'ARRIVING', 'ARRIVED', 'IN_PROGRESS'].includes(activeRide.status)) {
        ridesApi.getById(activeRide.id).then((res) => {
          setActiveRide(res.data);
        }).catch(() => {});
      }
    }, 2500);

    return () => clearInterval(interval);
  }, [activeRide?.id, activeRide?.status]);

  useEffect(() => {
    if (pickup.lat && dest?.lat) {
      ridesApi.estimate({
        pickupLat: pickup.lat,
        pickupLng: pickup.lng,
        destinationLat: dest.lat,
        destinationLng: dest.lng,
      }).then((res) => {
        setEstimate(res.data);
      }).catch(() => {
        setEstimate(null);
      });
    } else {
      setEstimate(null);
    }
  }, [pickup.lat, dest?.lat]);

  const handleBook = async (mode: 'AUCTION' | 'INSTANT') => {
    if (!dest) {
      toast.error('Please choose a destination first');
      return;
    }

    const vehicle = VEHICLES.find((v) => v.id === selectedVehicle) || VEHICLES[2];
    const calculatedFare = estimate
      ? Math.round(estimate.suggestedBaseFare * vehicle.multiplier)
      : vehicle.defaultFare;

    setBookingLoading(true);
    try {
      const payload = {
        pickupLocation: pickup.label,
        destinationLocation: dest.label,
        pickupLatitude: pickup.lat,
        pickupLongitude: pickup.lng,
        destinationLatitude: dest.lat,
        destinationLongitude: dest.lng,
        baseFare: calculatedFare,
        rideType: mode,
        vehicleType: vehicle.name,
      };

      if (mode === 'INSTANT') {
        toast.loading('Matching nearest driver on Redis Geo radar...', { id: 'dispatch' });
        const res = await ridesApi.createInstant(payload);
        toast.success('Driver Matched Instantly! 🚀', { id: 'dispatch' });
        setActiveRide(res.data);
        setDest(null);
      } else {
        const res = await ridesApi.create(payload);
        toast.success('Live Reverse Auction Started! Drivers bidding now 🏷️');
        setActiveRide(res.data);
        setRemainingSeconds(90);
        setDest(null);
      }
      loadRides();
    } catch (err: any) {
      toast.dismiss('dispatch');
      const msg = err.response?.data?.error || err.response?.data?.message || err.message || 'Ride creation failed';
      toast.error(msg);
    } finally {
      setBookingLoading(false);
    }
  };

  const handleCancelActiveRide = async () => {
    if (!activeRide) return;
    try {
      await ridesApi.cancel(activeRide.id, 'Cancelled by passenger');
      toast.success('Ride cancelled');
      setActiveRide(null);
      loadRides();
    } catch (e: any) {
      toast.error('Could not cancel ride: ' + (e.response?.data?.error || e.message));
    }
  };

  const handleAcceptBid = async (bidId: number) => {
    try {
      await bidsApi.accept(bidId);
      toast.success('Bid accepted! Driver is arriving 🚗');
      loadRides();
    } catch (e: any) {
      toast.error('Could not accept bid');
    }
  };

  const handleSimulateBids = async () => {
    if (!activeRide) return;
    setActionSimulating(true);
    try {
      await ridesApi.simulateBids(activeRide.id);
      toast.success('Simulated driver underbid submitted! ⚡');
      loadBids(activeRide.id);
    } catch (e: any) {
      toast.error('Simulation failed');
    } finally {
      setActionSimulating(false);
    }
  };

  const handleSimulateDriverStep = async () => {
    if (!activeRide) return;
    setActionSimulating(true);
    try {
      const res = await ridesApi.simulateDriverStep(activeRide.id);
      setActiveRide(res.data);
      toast.success(`Advanced trip state to: ${res.data.status} 🚀`);
      loadRides();
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'Could not advance driver state');
    } finally {
      setActionSimulating(false);
    }
  };

  const filteredLocations = useMemo(() => {
    if (!searchQuery.trim()) return PREDEFINED_LOCATIONS;
    const q = searchQuery.toLowerCase();
    return PREDEFINED_LOCATIONS.filter(
      (l) => l.label.toLowerCase().includes(q) || l.subtitle.toLowerCase().includes(q)
    );
  }, [searchQuery]);

  const lowestBid = bidsList.length > 0 ? bidsList[0] : null;

  return (
    <div style={{ position: 'relative', width: '100%', height: 'calc(100vh - 60px)', overflow: 'hidden', background: '#0F172A', display: 'flex', flexDirection: 'column' }}>

      {/* ── MAP CONTAINER ── */}
      <div style={{ position: 'absolute', inset: 0, zIndex: 0 }}>
        <RideMap
          pickupLat={pickup.lat}
          pickupLng={pickup.lng}
          destLat={dest?.lat}
          destLng={dest?.lng}
          driverLat={activeRide?.driverLatitude}
          driverLng={activeRide?.driverLongitude}
          showNearbyCluster={!dest && !activeRide}
          height="100%"
        />
      </div>

      {/* ── TOP FLOATING HEADER ── */}
      <div style={{ position: 'absolute', top: 16, left: 16, right: 16, zIndex: 10, display: 'flex', justifyContent: 'space-between', alignItems: 'center', pointerEvents: 'none' }}>
        <button
          onClick={() => setShowDrawer(true)}
          style={{
            pointerEvents: 'auto',
            width: 44,
            height: 44,
            borderRadius: '50%',
            background: '#ffffff',
            boxShadow: '0 4px 16px rgba(0,0,0,0.25)',
            border: 'none',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#0F172A',
            cursor: 'pointer',
          }}
          title="Menu"
        >
          <Menu size={22} />
        </button>

        {/* Current Pickup Pill (Matching Screenshot 3) */}
        {!dest && (
          <div
            onClick={() => {
              setSearchTarget('pickup');
              setShowSearchModal(true);
            }}
            style={{
              pointerEvents: 'auto',
              background: '#ffffff',
              borderRadius: 24,
              padding: '8px 16px',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              boxShadow: '0 4px 16px rgba(0,0,0,0.2)',
              cursor: 'pointer',
              maxWidth: '65%',
            }}
          >
            <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#10B981', flexShrink: 0 }} />
            <span style={{ fontSize: 13, fontWeight: 700, color: '#0F172A', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {pickup.label.split(',')[0]}
            </span>
            <ChevronRight size={14} color="#64748B" />
          </div>
        )}

        {/* Floating Right Action: Recenter or Fav */}
        <div style={{ display: 'flex', gap: 8, pointerEvents: 'auto' }}>
          <button
            onClick={() => {
              setPickup(PREDEFINED_LOCATIONS[0]);
              toast.success('Recentered to VIT Chennai');
            }}
            style={{
              width: 44,
              height: 44,
              borderRadius: '50%',
              background: '#ffffff',
              boxShadow: '0 4px 16px rgba(0,0,0,0.25)',
              border: 'none',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#0284C7',
              cursor: 'pointer',
            }}
            title="Recenter Location"
          >
            <Compass size={22} />
          </button>
        </div>
      </div>

      {/* ── BACK BUTTON & DESTINATION PILL WHEN ROUTE ACTIVE ── */}
      {dest && !activeRide && (
        <div style={{ position: 'absolute', top: 16, left: 16, right: 16, zIndex: 15, display: 'flex', alignItems: 'center', gap: 12 }}>
          <button
            onClick={() => setDest(null)}
            style={{
              width: 42,
              height: 42,
              borderRadius: '50%',
              background: '#ffffff',
              border: 'none',
              boxShadow: '0 4px 14px rgba(0,0,0,0.25)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#0F172A',
              cursor: 'pointer',
            }}
          >
            <ArrowLeft size={20} />
          </button>

          <div
            style={{
              flex: 1,
              background: '#ffffff',
              borderRadius: 24,
              padding: '10px 16px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              boxShadow: '0 4px 14px rgba(0,0,0,0.2)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, overflow: 'hidden' }}>
              <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#EF4444', flexShrink: 0 }} />
              <span style={{ fontSize: 13, fontWeight: 700, color: '#0F172A', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {dest.label.split(',')[0]}
              </span>
            </div>
            <span style={{ fontSize: 11, fontWeight: 700, color: '#10B981', background: '#ECFDF5', padding: '3px 8px', borderRadius: 12 }}>
              {estimate ? `${estimate.distanceKm.toFixed(1)} km` : 'Routing'}
            </span>
          </div>
        </div>
      )}

      {/* ── BOTTOM SHEETS ── */}

      {/* 1. HOME STATE BOTTOM SHEET (Matching Screenshot 3) */}
      {!dest && !activeRide && (
        <div
          style={{
            position: 'absolute',
            bottom: 0,
            left: 0,
            right: 0,
            zIndex: 20,
            background: '#ffffff',
            borderTopLeftRadius: 28,
            borderTopRightRadius: 28,
            boxShadow: '0 -8px 32px rgba(0,0,0,0.18)',
            display: 'flex',
            flexDirection: 'column',
            maxHeight: '75vh',
            overflow: 'hidden',
          }}
        >
          {/* Drag Handle */}
          <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 10, paddingBottom: 6 }}>
            <div style={{ width: 44, height: 5, borderRadius: 10, background: '#CBD5E1' }} />
          </div>

          <div style={{ padding: '0 20px 16px', overflowY: 'auto' }}>

            {/* "Where are you going?" Search Bar */}
            <div
              onClick={() => {
                setSearchTarget('dest');
                setShowSearchModal(true);
              }}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                background: '#F8FAFC',
                border: '1.5px solid #E2E8F0',
                borderRadius: 999,
                padding: '14px 20px',
                marginTop: 8,
                marginBottom: 16,
                cursor: 'pointer',
                boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
                transition: 'all 0.2s',
              }}
            >
              <Search size={20} color="#0F172A" />
              <span style={{ fontSize: 16, fontWeight: 700, color: '#0F172A' }}>
                Where are you going?
              </span>
            </div>

            {/* Quick Service Categories (Matching Screenshot 3: Outstation, Rentals, Bike, Parcel) */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10, marginBottom: 20 }}>
              {[
                { id: 'outstation', label: 'Outstation', icon: '🚖', bg: '#EFF6FF', color: '#1D4ED8' },
                { id: 'rentals', label: 'Rentals', icon: '⏱️', bg: '#ECFDF5', color: '#047857' },
                { id: 'bike', label: 'Bike', icon: '🏍️', bg: '#FEF3C7', color: '#B45309' },
                { id: 'parcel', label: 'Parcel', icon: '📦', bg: '#F1F5F9', color: '#475569' },
              ].map((svc) => (
                <div
                  key={svc.id}
                  onClick={() => {
                    setSelectedVehicle(svc.id === 'bike' ? 'bike' : 'auto');
                    setSearchTarget('dest');
                    setShowSearchModal(true);
                  }}
                  style={{
                    background: '#F8FAFC',
                    border: '1px solid #E2E8F0',
                    borderRadius: 16,
                    padding: '12px 8px',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: 6,
                    cursor: 'pointer',
                    transition: 'transform 0.15s',
                  }}
                >
                  <div style={{ width: 44, height: 44, borderRadius: '50%', background: svc.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 22 }}>
                    {svc.icon}
                  </div>
                  <span style={{ fontSize: 12, fontWeight: 700, color: '#334155' }}>{svc.label}</span>
                </div>
              ))}
            </div>

            {/* Recent Destinations List (Exact replica from Screenshot 3) */}
            <div style={{ borderTop: '1px solid #F1F5F9', paddingTop: 12 }}>
              <div style={{ fontSize: 11, fontWeight: 800, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 8 }}>
                Recent Locations
              </div>

              {PREDEFINED_LOCATIONS.slice(1, 4).map((loc) => (
                <div
                  key={loc.id}
                  onClick={() => setDest(loc)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 14,
                    padding: '12px 0',
                    borderBottom: '1px solid #F1F5F9',
                    cursor: 'pointer',
                  }}
                >
                  <div style={{ width: 34, height: 34, borderRadius: '50%', background: '#F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#64748B' }}>
                    <Clock size={16} />
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {loc.label}
                    </div>
                    <div style={{ fontSize: 12, color: '#64748B', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {loc.subtitle}
                    </div>
                  </div>
                  <ChevronRight size={18} color="#94A3B8" />
                </div>
              ))}
            </div>

            {/* Recent Completed Ride Banner (Matching Screenshot 3) */}
            <div
              style={{
                marginTop: 16,
                background: '#F8FAFC',
                border: '1px solid #E2E8F0',
                borderRadius: 18,
                padding: '14px 16px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <div>
                <div style={{ fontSize: 13, fontWeight: 800, color: '#0F172A' }}>Rate your ride with BASKAR H</div>
                <div style={{ fontSize: 11, color: '#64748B', marginTop: 2 }}>Your Tuesday ride to Velichal, Keelakottiyur</div>
              </div>
              <div style={{ display: 'flex', gap: 3 }}>
                {[1, 2, 3, 4, 5].map((star) => (
                  <Star key={star} size={16} fill="#FBBF24" color="#FBBF24" style={{ cursor: 'pointer' }} />
                ))}
              </div>
            </div>

            {/* Bottom Navigation Tabs (Matching Screenshot 3) */}
            <div style={{ display: 'flex', justifyContent: 'space-around', borderTop: '1px solid #F1F5F9', marginTop: 18, paddingTop: 10 }}>
              <div onClick={() => setActiveTab('home')} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4, cursor: 'pointer', color: activeTab === 'home' ? '#0F172A' : '#94A3B8' }}>
                <MapPin size={20} color={activeTab === 'home' ? '#0F172A' : '#94A3B8'} />
                <span style={{ fontSize: 11, fontWeight: 700 }}>Home</span>
              </div>
              <div onClick={() => navigate('/passenger/rides')} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4, cursor: 'pointer', color: '#94A3B8' }}>
                <Clock size={20} />
                <span style={{ fontSize: 11, fontWeight: 700 }}>My Rides</span>
              </div>
              <div onClick={() => { setSelectedVehicle('auto'); setSearchTarget('dest'); setShowSearchModal(true); }} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4, cursor: 'pointer', color: '#94A3B8' }}>
                <Zap size={20} />
                <span style={{ fontSize: 11, fontWeight: 700 }}>Electric</span>
              </div>
              <div onClick={() => navigate('/passenger/ratings')} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4, cursor: 'pointer', color: '#94A3B8' }}>
                <Star size={20} />
                <span style={{ fontSize: 11, fontWeight: 700 }}>Ratings</span>
              </div>
            </div>

          </div>
        </div>
      )}

      {/* 2. BOOKING SHEET (Matching Screenshots 1 & 2) */}
      {dest && !activeRide && (
        <div
          style={{
            position: 'absolute',
            bottom: 0,
            left: 0,
            right: 0,
            zIndex: 20,
            background: '#ffffff',
            borderTopLeftRadius: 28,
            borderTopRightRadius: 28,
            boxShadow: '0 -8px 36px rgba(0,0,0,0.25)',
            display: 'flex',
            flexDirection: 'column',
            maxHeight: '68vh',
            animation: 'slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1)',
          }}
        >
          {/* Drag Handle */}
          <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 10, paddingBottom: 4 }}>
            <div style={{ width: 44, height: 5, borderRadius: 10, background: '#CBD5E1' }} />
          </div>

          {/* Cancellation Policy Green Pill (Matching Screenshot 1 & 4) */}
          <div style={{ textAlign: 'center', padding: '4px 0 8px' }}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, background: '#ECFDF5', color: '#047857', padding: '4px 12px', borderRadius: 999, fontSize: 12, fontWeight: 700 }}>
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#10B981' }} />
              Total with ₹10 Cancellation fee
            </span>
          </div>

          {/* Vehicle List */}
          <div style={{ flex: 1, overflowY: 'auto', padding: '0 16px', display: 'flex', flexDirection: 'column', gap: 8 }}>
            {VEHICLES.map((vehicle) => {
              const isSelected = selectedVehicle === vehicle.id;
              const calculatedFare = estimate
                ? Math.round(estimate.suggestedBaseFare * vehicle.multiplier)
                : vehicle.defaultFare;

              return (
                <div
                  key={vehicle.id}
                  onClick={() => setSelectedVehicle(vehicle.id)}
                  style={{
                    position: 'relative',
                    display: 'flex',
                    alignItems: 'center',
                    padding: '12px 14px',
                    borderRadius: 16,
                    border: isSelected ? '2px solid #0284C7' : '1.5px solid #F1F5F9',
                    background: isSelected ? '#F0F9FF' : '#ffffff',
                    cursor: 'pointer',
                    transition: 'all 0.15s ease-out',
                  }}
                >
                  {/* Vehicle Icon */}
                  <div style={{ width: 50, height: 50, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 32, marginRight: 12 }}>
                    {vehicle.icon}
                  </div>

                  {/* Details */}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span style={{ fontWeight: 800, fontSize: 16, color: '#0F172A' }}>{vehicle.name}</span>
                      <span style={{ display: 'inline-flex', alignItems: 'center', fontSize: 12, fontWeight: 600, color: '#64748B' }}>
                        <Users size={12} style={{ marginRight: 2 }} /> {vehicle.capacity}
                      </span>
                      {vehicle.badge && (
                        <span style={{ fontSize: 10, fontWeight: 800, background: '#FEF3C7', color: '#B45309', padding: '2px 6px', borderRadius: 6 }}>
                          {vehicle.badge}
                        </span>
                      )}
                    </div>
                    <div style={{ fontSize: 12, color: '#64748B', marginTop: 2 }}>
                      {vehicle.desc}
                    </div>
                    <div style={{ fontSize: 11, fontWeight: 600, color: '#94A3B8', marginTop: 2 }}>
                      {vehicle.eta}
                    </div>
                  </div>

                  {/* Price */}
                  <div style={{ textAlign: 'right', paddingLeft: 10 }}>
                    <div style={{ fontSize: 20, fontWeight: 900, color: '#0F172A' }}>
                      ₹{calculatedFare}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Payment Method Bar (Matching Screenshots 1 & 2) */}
          <div style={{ padding: '10px 20px', borderTop: '1px solid #F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#FAFAFA' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer' }}>
              <div style={{ width: 24, height: 24, borderRadius: '50%', background: '#DCFCE7', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#16A34A', fontSize: 12, fontWeight: 800 }}>
                💵
              </div>
              <div>
                <span style={{ fontSize: 13, fontWeight: 800, color: '#0F172A' }}>Cash</span>
                <span style={{ fontSize: 11, color: '#64748B', marginLeft: 6 }}>Pay directly to driver</span>
              </div>
            </div>
            <ChevronRight size={16} color="#94A3B8" />
          </div>

          {/* Dual Action Buttons: "Bargain & Book" and "Book now" (Matching Screenshots 1 & 2) */}
          <div style={{ padding: '14px 16px 28px', background: '#ffffff', borderTop: '1px solid #F1F5F9', display: 'flex', gap: 12 }}>
            <button
              onClick={() => handleBook('AUCTION')}
              disabled={bookingLoading}
              style={{
                flex: 1,
                padding: '16px 8px',
                borderRadius: 999,
                border: '1.5px solid #059669',
                background: '#ffffff',
                color: '#059669',
                fontSize: 15,
                fontWeight: 800,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 6,
              }}
            >
              {bookingLoading ? 'Processing...' : 'Bargain & Book'}
            </button>

            <button
              onClick={() => handleBook('INSTANT')}
              disabled={bookingLoading}
              style={{
                flex: 1,
                padding: '16px 8px',
                borderRadius: 999,
                border: 'none',
                background: '#FBBF24',
                color: '#0F172A',
                fontSize: 15,
                fontWeight: 800,
                boxShadow: '0 4px 14px rgba(251, 191, 36, 0.45)',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 6,
              }}
            >
              {bookingLoading ? 'Processing...' : 'Book now'}
            </button>
          </div>
        </div>
      )}

      {/* 3. ACTIVE RIDE FLOATING SHEET (AUCTION or MATCHED or IN-TRANSIT) */}
      {activeRide && (
        <div
          style={{
            position: 'absolute',
            bottom: 0,
            left: 0,
            right: 0,
            zIndex: 30,
            background: '#ffffff',
            borderTopLeftRadius: 28,
            borderTopRightRadius: 28,
            boxShadow: '0 -8px 36px rgba(0,0,0,0.3)',
            display: 'flex',
            flexDirection: 'column',
            maxHeight: '75vh',
            overflowY: 'auto',
            padding: '20px 20px 32px',
          }}
        >
          {/* Auction State */}
          {activeRide.status === 'INITIATED' ? (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <div>
                  <span style={{ fontSize: 11, fontWeight: 800, color: '#0284C7', textTransform: 'uppercase', letterSpacing: 1 }}>
                    Live Reverse Auction #{activeRide.id}
                  </span>
                  <h3 style={{ fontSize: 18, fontWeight: 900, color: '#0F172A', marginTop: 2 }}>
                    Drivers Competing on Price
                  </h3>
                </div>

                <div style={{ background: '#FEF3C7', border: '1.5px solid #F59E0B', borderRadius: 999, padding: '6px 14px', display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#F59E0B' }} />
                  <span style={{ fontSize: 14, fontWeight: 900, color: '#92400E' }}>{remainingSeconds}s</span>
                </div>
              </div>

              <div style={{ background: '#F8FAFC', borderRadius: 16, padding: '12px 16px', border: '1px solid #E2E8F0', marginBottom: 16 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#334155' }}>
                  <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#10B981' }} />
                  <span style={{ fontWeight: 600 }}>{activeRide.pickupLocation}</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#334155', marginTop: 6 }}>
                  <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#EF4444' }} />
                  <span style={{ fontWeight: 600 }}>{activeRide.destinationLocation}</span>
                </div>
                <div style={{ fontSize: 12, color: '#64748B', marginTop: 8 }}>
                  Starting Base: <strong>₹{activeRide.baseFare}</strong> • Vehicle: <strong>{activeRide.vehicleType || 'Any'}</strong>
                </div>
              </div>

              {/* Lowest Bid Spotlight */}
              {lowestBid ? (
                <div style={{ background: '#ECFDF5', border: '1.5px solid #10B981', borderRadius: 16, padding: 16, marginBottom: 16 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <div style={{ fontSize: 11, fontWeight: 800, color: '#047857', textTransform: 'uppercase' }}>Current Best Bid</div>
                      <div style={{ fontSize: 28, fontWeight: 900, color: '#065F46' }}>₹{lowestBid.amount}</div>
                      <div style={{ fontSize: 11, color: '#059669' }}>
                        You save ₹{(activeRide.baseFare - lowestBid.amount).toFixed(0)}!
                      </div>
                    </div>
                    <button
                      onClick={() => handleAcceptBid(lowestBid.id)}
                      style={{ background: '#10B981', color: '#ffffff', border: 'none', borderRadius: 12, padding: '10px 18px', fontSize: 14, fontWeight: 800, cursor: 'pointer' }}
                    >
                      Accept ₹{lowestBid.amount}
                    </button>
                  </div>
                </div>
              ) : (
                <div style={{ textAlign: 'center', padding: '20px 0', color: '#64748B' }}>
                  <div style={{ fontSize: 24, marginBottom: 6 }}>📡</div>
                  <div style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>Broadcasting to nearby drivers...</div>
                  <div style={{ fontSize: 12 }}>Bids will stream in real-time as drivers underbid</div>
                </div>
              )}

              {/* Bids List */}
              {bidsList.length > 0 && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16, maxHeight: 160, overflowY: 'auto' }}>
                  {bidsList.map((bid, idx) => (
                    <div key={bid.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#F8FAFC', padding: '10px 14px', borderRadius: 12, border: '1px solid #E2E8F0' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span style={{ fontSize: 13, fontWeight: 700, color: '#0F172A' }}>Driver #{bid.driverId}</span>
                        {idx === 0 && <span style={{ fontSize: 10, fontWeight: 800, color: '#047857', background: '#ECFDF5', padding: '2px 6px', borderRadius: 6 }}>Lowest</span>}
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        <span style={{ fontSize: 16, fontWeight: 800, color: '#0F172A' }}>₹{bid.amount}</span>
                        <button
                          onClick={() => handleAcceptBid(bid.id)}
                          style={{ background: '#0F172A', color: '#ffffff', border: 'none', borderRadius: 8, padding: '6px 12px', fontSize: 12, fontWeight: 700, cursor: 'pointer' }}
                        >
                          Accept
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Actions & Simulation helper */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <button
                  onClick={handleSimulateBids}
                  disabled={actionSimulating}
                  style={{ width: '100%', padding: '12px', borderRadius: 12, background: '#EFF6FF', border: '1px solid #BFDBFE', color: '#1D4ED8', fontWeight: 700, fontSize: 13, cursor: 'pointer' }}
                >
                  ⚡ Simulate Driver Underbid
                </button>
                <button
                  onClick={handleCancelActiveRide}
                  style={{ width: '100%', padding: '12px', borderRadius: 12, background: '#ffffff', border: '1px solid #FCA5A5', color: '#DC2626', fontWeight: 700, fontSize: 13, cursor: 'pointer' }}
                >
                  Cancel Auction
                </button>
              </div>
            </div>
          ) : (
            /* Matched / In-Transit State */
            <div>
              {/* Trip Status Pill */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                <div>
                  <span style={{ fontSize: 11, fontWeight: 800, color: '#10B981', background: '#ECFDF5', padding: '4px 10px', borderRadius: 999, textTransform: 'uppercase' }}>
                    {activeRide.status}
                  </span>
                  <h3 style={{ fontSize: 18, fontWeight: 900, color: '#0F172A', marginTop: 6 }}>
                    {activeRide.status === 'MATCHED' && 'Driver Confirmed & Approaching'}
                    {activeRide.status === 'DRIVER_ARRIVING' && 'Driver is Arriving at Pickup'}
                    {activeRide.status === 'ARRIVED' && 'Driver has Arrived!'}
                    {activeRide.status === 'IN_PROGRESS' && 'Trip In Progress'}
                    {activeRide.status === 'COMPLETED' && 'Ride Completed'}
                  </h3>
                </div>

                <div style={{ textAlign: 'right' }}>
                  <span style={{ fontSize: 11, color: '#64748B' }}>Agreed Fare</span>
                  <div style={{ fontSize: 20, fontWeight: 900, color: '#0F172A' }}>₹{activeRide.finalFare || activeRide.baseFare}</div>
                </div>
              </div>

              {/* OTP CARD - Crucial for Uber/Rapido */}
              {activeRide.otp && ['MATCHED', 'DRIVER_ARRIVING', 'ARRIVING', 'ARRIVED'].includes(activeRide.status) && (
                <div style={{ background: '#F0F9FF', border: '2px dashed #0284C7', borderRadius: 16, padding: '14px 20px', textAlign: 'center', marginBottom: 16 }}>
                  <div style={{ fontSize: 11, fontWeight: 800, color: '#0369A1', textTransform: 'uppercase', letterSpacing: 1 }}>START TRIP OTP</div>
                  <div style={{ fontSize: 32, fontWeight: 900, letterSpacing: 8, color: '#0F172A', margin: '4px 0' }}>
                    {activeRide.otp}
                  </div>
                  <div style={{ fontSize: 11, color: '#64748B' }}>Share this code with your driver before boarding</div>
                </div>
              )}

              {/* Driver & Vehicle Card */}
              <div style={{ background: '#F8FAFC', borderRadius: 18, padding: 16, border: '1px solid #E2E8F0', display: 'flex', alignItems: 'center', gap: 14, marginBottom: 16 }}>
                <div style={{ width: 52, height: 52, borderRadius: '50%', background: '#FDE68A', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 24, fontWeight: 800 }}>
                  👨🏽‍✈️
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span style={{ fontWeight: 800, fontSize: 15, color: '#0F172A' }}>
                      {activeRide.driverName || 'BASKAR H'}
                    </span>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 2, fontSize: 11, fontWeight: 700, color: '#B45309', background: '#FEF3C7', padding: '2px 6px', borderRadius: 6 }}>
                      ★ {activeRide.driverRating || '4.93'}
                    </span>
                  </div>
                  <div style={{ fontSize: 12, fontWeight: 600, color: '#475569', marginTop: 2 }}>
                    {activeRide.driverCarType || activeRide.vehicleType || 'Auto'} • {activeRide.driverLicensePlate || 'TN 09 AB 1234'}
                  </div>
                </div>
                <a
                  href={`tel:${activeRide.driverPhone || '9876543210'}`}
                  style={{ width: 40, height: 40, borderRadius: '50%', background: '#10B981', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#ffffff' }}
                >
                  <Phone size={18} />
                </a>
              </div>

              {/* Simulation Demo Buttons */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <button
                  onClick={handleSimulateDriverStep}
                  disabled={actionSimulating}
                  style={{ width: '100%', padding: '14px', borderRadius: 14, background: '#0F172A', color: '#ffffff', fontWeight: 800, fontSize: 14, border: 'none', cursor: 'pointer' }}
                >
                  ⚡ Advance Driver Trip (Demo Driver Step)
                </button>

                {activeRide.status !== 'COMPLETED' && (
                  <button
                    onClick={handleCancelActiveRide}
                    style={{ width: '100%', padding: '12px', borderRadius: 12, background: '#ffffff', border: '1px solid #FCA5A5', color: '#DC2626', fontWeight: 700, fontSize: 13, cursor: 'pointer' }}
                  >
                    Cancel Ride
                  </button>
                )}
                {activeRide.status === 'COMPLETED' && (
                  <button
                    onClick={() => { setActiveRide(null); setDest(null); }}
                    style={{ width: '100%', padding: '14px', borderRadius: 14, background: '#10B981', color: '#ffffff', fontWeight: 800, fontSize: 14, border: 'none', cursor: 'pointer' }}
                  >
                    Book Another Ride ✨
                  </button>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* ── SEARCH MODAL OVERLAY ── */}
      {showSearchModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 999,
            background: 'rgba(15, 23, 42, 0.65)',
            backdropFilter: 'blur(6px)',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'flex-end',
          }}
        >
          <div
            style={{
              background: '#ffffff',
              borderTopLeftRadius: 28,
              borderTopRightRadius: 28,
              padding: '20px 20px 32px',
              maxHeight: '85vh',
              display: 'flex',
              flexDirection: 'column',
              boxShadow: '0 -8px 32px rgba(0,0,0,0.3)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
              <h3 style={{ fontSize: 18, fontWeight: 800, color: '#0F172A' }}>
                {searchTarget === 'pickup' ? 'Select Pickup Location' : 'Select Destination'}
              </h3>
              <button
                onClick={() => setShowSearchModal(false)}
                style={{ width: 36, height: 36, borderRadius: '50%', background: '#F1F5F9', border: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#475569', cursor: 'pointer' }}
              >
                <X size={18} />
              </button>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 10, background: '#F8FAFC', border: '1.5px solid #CBD5E1', borderRadius: 14, padding: '12px 14px', marginBottom: 16 }}>
              <Search size={18} color="#64748B" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search station, college, street in Chennai..."
                autoFocus
                style={{ flex: 1, border: 'none', background: 'transparent', outline: 'none', fontSize: 14, color: '#0F172A', fontWeight: 600 }}
              />
            </div>

            <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 4 }}>
              {filteredLocations.map((loc) => (
                <div
                  key={loc.id}
                  onClick={() => {
                    if (searchTarget === 'pickup') {
                      setPickup(loc);
                    } else {
                      setDest(loc);
                    }
                    setShowSearchModal(false);
                    setSearchQuery('');
                  }}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '12px 10px',
                    borderRadius: 12,
                    cursor: 'pointer',
                    transition: 'background 0.15s',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.background = '#F8FAFC')}
                  onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                >
                  <div style={{ width: 34, height: 34, borderRadius: '50%', background: searchTarget === 'pickup' ? '#DCFCE7' : '#FEE2E2', display: 'flex', alignItems: 'center', justifyContent: 'center', color: searchTarget === 'pickup' ? '#16A34A' : '#DC2626' }}>
                    <MapPin size={16} />
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>{loc.label}</div>
                    <div style={{ fontSize: 12, color: '#64748B' }}>{loc.subtitle}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* ── SIDE DRAWER (MENU) ── */}
      {showDrawer && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 1000,
            background: 'rgba(15, 23, 42, 0.6)',
            display: 'flex',
          }}
          onClick={() => setShowDrawer(false)}
        >
          <div
            style={{
              width: 300,
              height: '100%',
              background: '#ffffff',
              boxShadow: '4px 0 24px rgba(0,0,0,0.25)',
              display: 'flex',
              flexDirection: 'column',
              padding: '24px 20px',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* User Profile Header */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, paddingBottom: 20, borderBottom: '1px solid #F1F5F9' }}>
              <div style={{ width: 48, height: 48, borderRadius: '50%', background: '#6C63FF', color: '#ffffff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18, fontWeight: 800 }}>
                {email ? email.charAt(0).toUpperCase() : 'P'}
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 16, fontWeight: 800, color: '#0F172A', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {email ? email.split('@')[0] : 'Passenger'}
                </div>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#6C63FF', textTransform: 'uppercase' }}>
                  Passenger Mode
                </div>
              </div>
            </div>

            {/* Nav Links */}
            <div style={{ flex: 1, paddingTop: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
              <div
                onClick={() => { setShowDrawer(false); navigate('/passenger/rides'); }}
                style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 12, borderRadius: 12, cursor: 'pointer', color: '#334155', fontWeight: 600 }}
              >
                <Clock size={18} />
                <span>My Rides History</span>
              </div>
              <div
                onClick={() => { setShowDrawer(false); navigate('/passenger/ratings'); }}
                style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 12, borderRadius: 12, cursor: 'pointer', color: '#334155', fontWeight: 600 }}
              >
                <Star size={18} />
                <span>My Ratings</span>
              </div>
              <div
                onClick={() => {
                  toast('Support 24/7: Call 1800-BID-DRIVE');
                }}
                style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 12, borderRadius: 12, cursor: 'pointer', color: '#334155', fontWeight: 600 }}
              >
                <Shield size={18} />
                <span>Safety & Support</span>
              </div>
            </div>

            {/* Logout Button */}
            <button
              onClick={() => {
                logout();
                navigate('/login');
              }}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '14px',
                borderRadius: 14,
                border: '1px solid #FCA5A5',
                background: '#FEF2F2',
                color: '#DC2626',
                fontWeight: 700,
                cursor: 'pointer',
                justifyContent: 'center',
              }}
            >
              <LogOut size={16} />
              <span>Log Out</span>
            </button>
          </div>
        </div>
      )}

    </div>
  );
};

export default PassengerDashboard;
