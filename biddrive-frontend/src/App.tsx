import React, { useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';

import LoginPage from './pages/LoginPage';
import DriverLayout from './layouts/DriverLayout';
import PassengerLayout from './layouts/PassengerLayout';
import ProtectedRoute from './components/ProtectedRoute';

import DriverDashboard from './pages/driver/DriverDashboard';
import LiveRidesPage from './pages/driver/LiveRidesPage';
import RideDetailPage from './pages/driver/RideDetailPage';
import DriverBidsPage from './pages/driver/DriverBidsPage';
import WalletPage from './pages/driver/WalletPage';
import DriverSettingsPage from './pages/driver/DriverSettingsPage';

import PassengerDashboard from './pages/passenger/PassengerDashboard';
import BookRidePage from './pages/passenger/BookRidePage';
import MyRidesPage from './pages/passenger/MyRidesPage';
import PassengerRideDetail from './pages/passenger/PassengerRideDetail';
import TrackRidePage from './pages/passenger/TrackRidePage';

import RatingsPage from './pages/shared/RatingsPage';
import MapShowcasePage from './pages/shared/MapShowcasePage';

import { useAuthStore } from './lib/store';
import { wsService } from './lib/websocket';

const App: React.FC = () => {
  const { token } = useAuthStore();

  useEffect(() => {
    if (token && !wsService.isConnected()) {
      wsService.connect(token);
    }
  }, [token]);

  return (
    <BrowserRouter>

      <Toaster
        position="top-right"
        gutter={8}
        toastOptions={{
          style: {
            background: '#1E1E2E',
            color: '#F0F0FF',
            border: '1px solid rgba(108,99,255,0.25)',
            borderRadius: '12px',
            fontSize: '14px',
            fontFamily: 'Inter, sans-serif',
            boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
          },
          success: {
            iconTheme: { primary: '#43E97B', secondary: '#0A0A0F' },
          },
          error: {
            iconTheme: { primary: '#FF6B6B', secondary: '#0A0A0F' },
          },
        }}
      />

      <Routes>

        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<Navigate to="/login" replace />} />

        <Route element={<ProtectedRoute allowedRoles={['DRIVER']} />}>
          <Route element={<DriverLayout />}>
            <Route path="/driver" element={<DriverDashboard />} />
            <Route path="/driver/rides" element={<LiveRidesPage />} />
            <Route path="/driver/rides/:id" element={<RideDetailPage />} />
            <Route path="/driver/bids" element={<DriverBidsPage />} />
            <Route path="/driver/wallet" element={<WalletPage />} />
            <Route path="/driver/ratings" element={<RatingsPage />} />
            <Route path="/driver/map" element={<MapShowcasePage />} />
            <Route path="/driver/settings" element={<DriverSettingsPage />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['PASSENGER']} />}>
          <Route element={<PassengerLayout />}>
            <Route path="/passenger" element={<PassengerDashboard />} />
            <Route path="/passenger/book" element={<BookRidePage />} />
            <Route path="/passenger/rides" element={<MyRidesPage />} />
            <Route path="/passenger/rides/:id" element={<PassengerRideDetail />} />
            <Route path="/passenger/track" element={<TrackRidePage />} />
            <Route path="/passenger/ratings" element={<RatingsPage />} />
            <Route path="/passenger/map" element={<MapShowcasePage />} />
            <Route path="/passenger/settings" element={<PassengerDashboard />} />
          </Route>
        </Route>

        <Route path="/map" element={<MapShowcasePage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  );
};

export default App;
