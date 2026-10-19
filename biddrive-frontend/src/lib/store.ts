import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { AuthResponse } from './api';
import { wsService } from './websocket';

interface AuthState {
  token: string | null;
  role: 'DRIVER' | 'PASSENGER' | null;
  userId: number | null;
  email: string | null;
  setAuth: (res: AuthResponse) => void;
  logout: () => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      role: null,
      userId: null,
      email: null,
      setAuth: (res: AuthResponse) => {
        localStorage.setItem('bd_token', res.token);
        set({ token: res.token, role: res.role, userId: res.userId, email: res.email });
        wsService.connect(res.token);
      },
      logout: () => {
        localStorage.removeItem('bd_token');
        wsService.disconnect();
        set({ token: null, role: null, userId: null, email: null });
      },
      isAuthenticated: () => !!get().token,
    }),
    {
      name: 'bd_auth',
      partialize: (state) => ({
        token: state.token,
        role: state.role,
        userId: state.userId,
        email: state.email,
      }),
    }
  )
);
