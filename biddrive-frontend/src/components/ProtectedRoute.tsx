import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuthStore } from '../lib/store';

interface Props {
  allowedRoles?: Array<'DRIVER' | 'PASSENGER'>;
}

const ProtectedRoute: React.FC<Props> = ({ allowedRoles }) => {
  const { token, role } = useAuthStore();

  if (!token) return <Navigate to="/login" replace />;
  if (allowedRoles && role && !allowedRoles.includes(role)) {
    return <Navigate to={role === 'DRIVER' ? '/driver' : '/passenger'} replace />;
  }

  return <Outlet />;
};

export default ProtectedRoute;
