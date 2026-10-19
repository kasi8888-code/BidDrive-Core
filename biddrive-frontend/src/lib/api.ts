import axios from 'axios';

const BASE_URL = '';

export const api = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('bd_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.clear();
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

export const authApi = {
  register: (data: RegisterPayload) => api.post('/api/auth/register', data),
  login: (data: LoginPayload) => api.post('/api/auth/login', data),
};

export const ridesApi = {
  create: (data: CreateRidePayload) => api.post('/api/rides', data),
  createInstant: (data: CreateRidePayload) => api.post('/api/rides/instant', data),
  getAll: (status?: string) => api.get('/api/rides', { params: status ? { status } : {} }),
  getById: (id: number) => api.get(`/api/rides/${id}`),
  getAuction: (id: number) => api.get(`/api/rides/${id}/auction`),
  estimate: (p: EstimateParams) => api.get('/api/rides/estimate', { params: p }),
  markArriving: (id: number) => api.put(`/api/rides/${id}/arriving`),
  markArrived: (id: number) => api.put(`/api/rides/${id}/arrived`),
  startTrip: (id: number, otp: string) => api.post(`/api/rides/${id}/start`, { otp }),
  complete: (id: number) => api.put(`/api/rides/${id}/complete`),
  cancel: (id: number, reason?: string) => api.put(`/api/rides/${id}/cancel`, { cancellationReason: reason }),
  delete: (id: number) => api.delete(`/api/rides/${id}`),
  retryAuction: (id: number, newBaseFare?: number) => api.post(`/api/rides/${id}/retry-auction`, null, { params: { newBaseFare } }),
  convertToInstant: (id: number) => api.post(`/api/rides/${id}/convert-to-instant`),
  simulateBids: (id: number) => api.post(`/api/rides/${id}/simulate-bids`),
  simulateDriverStep: (id: number) => api.post(`/api/rides/${id}/simulate-driver-step`),
};

export const bidsApi = {
  submit: (data: BidPayload) => api.post('/api/bids', {
    rideId: data.rideId,
    bidAmount: data.bidAmount ?? data.amount,
    amount: data.amount ?? data.bidAmount,
  }),
  accept: (bidId: number) => api.put(`/api/bids/${bidId}/accept`),
  getAll: () => api.get('/api/bids'),
  getByRide: (rideId: number) => api.get(`/api/bids/ride/${rideId}`),
};

export const driversApi = {
  updateLocation: (lat: number, lng: number) =>
    api.put('/api/drivers/location', { latitude: lat, longitude: lng }),
  updateStatus: (status: string) =>
    api.put('/api/drivers/status', null, { params: { status } }),
  getNearby: (lat: number, lng: number, radius = 5) =>
    api.get('/api/drivers/nearby', { params: { latitude: lat, longitude: lng, radiusKm: radius } }),
  getById: (id: number) => api.get(`/api/drivers/${id}`),
};

export const walletApi = {
  getMyWallet: () => api.get('/api/wallet/my'),
  getFareInfo: () => api.get('/api/wallet/fare-info'),
};

export const ratingsApi = {
  submit: (data: { rideId: number; targetId: number; score: number; comment?: string; raterRole: string }) =>
    api.post('/api/ratings', data),
};

export interface RegisterPayload {
  name: string;
  email: string;
  password: string;
  phoneNumber: string;
  role: 'DRIVER' | 'PASSENGER';
  licensePlate?: string;
  carType?: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  role: 'DRIVER' | 'PASSENGER';
  userId: number;
  email: string;
}

export interface CreateRidePayload {
  pickupLocation: string;
  destinationLocation: string;
  pickupLatitude: number;
  pickupLongitude: number;
  destinationLatitude: number;
  destinationLongitude: number;
  baseFare: number;
  rideType?: 'AUCTION' | 'INSTANT';
  vehicleType?: string;
}

export interface EstimateParams {
  pickupLat: number;
  pickupLng: number;
  destinationLat: number;
  destinationLng: number;
}

export interface BidPayload {
  rideId: number;
  amount: number;
  bidAmount?: number;
}

export interface Ride {
  id: number;
  passengerId: number;
  driverId?: number;
  pickupLocation: string;
  destinationLocation: string;
  pickupLatitude?: number;
  pickupLongitude?: number;
  destinationLatitude?: number;
  destinationLongitude?: number;
  estimatedDistanceKm?: number;
  baseFare: number;
  finalFare?: number;
  status: string;
  rideType: 'AUCTION' | 'INSTANT';
  vehicleType?: string;
  otp?: string;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
  cancelledAt?: string;
  cancellationReason?: string;
  cancelledBy?: string;
  lowestBid?: number;
  myBid?: number;
  remainingSeconds?: number;
  driverName?: string;
  driverPhone?: string;
  driverCarType?: string;
  driverLicensePlate?: string;
  driverRating?: number;
  driverLatitude?: number;
  driverLongitude?: number;
  driverEta?: string;
}

export interface Bid {
  id: number;
  rideId: number;
  driverId: number;
  amount: number;
  status: string;
  createdAt: string;
}

export interface Driver {
  id: number;
  name: string;
  email: string;
  phoneNumber: string;
  carType: string;
  licensePlate: string;
  rating: number;
  status: string;
  preferenceMode: string;
  currentLatitude?: number;
  currentLongitude?: number;
}

export interface DriverWallet {
  id: number;
  driverId: number;
  totalEarnings: number;
  availableBalance: number;
  totalRides: number;
}

export const WS_URL = `${window.location.protocol}//${window.location.host}/ws`;
