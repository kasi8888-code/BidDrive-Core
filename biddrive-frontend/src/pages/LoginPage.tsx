import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Car, User, Eye, EyeOff, ArrowRight, Zap } from 'lucide-react';
import toast from 'react-hot-toast';
import { authApi } from '../lib/api';
import { useAuthStore } from '../lib/store';

type Mode = 'login' | 'register';
type Role = 'PASSENGER' | 'DRIVER';

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const { setAuth } = useAuthStore();

  const [mode, setMode] = useState<Mode>('login');
  const [role, setRole] = useState<Role>('PASSENGER');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const [form, setForm] = useState({
    name: '', email: '', password: '', phoneNumber: '',
    licensePlate: '', carType: '',
  });

  const [errors, setErrors] = useState<Record<string, string>>({});

  const setField = (key: string, val: string) => {
    setForm((f) => ({ ...f, [key]: val }));
    setErrors((e) => ({ ...e, [key]: '' }));
  };

  const validate = () => {
    const e: Record<string, string> = {};
    if (mode === 'register' && !form.name) e.name = 'Name is required';
    if (!form.email || !/\S+@\S+\.\S+/.test(form.email)) e.email = 'Valid email is required';
    if (!form.password || form.password.length < 8) e.password = 'Minimum 8 characters';
    if (mode === 'register') {
      const cleanPhone = form.phoneNumber.trim().replace(/[-\s]/g, '');
      if (!cleanPhone) {
        e.phoneNumber = 'Phone number is required';
      } else if (!/^\d{10}$/.test(cleanPhone)) {
        e.phoneNumber = 'Phone number must be exactly 10 digits';
      }
    }
    if (mode === 'register' && role === 'DRIVER') {
      const cleanPlate = form.licensePlate.trim().replace(/[-\s]/g, '').toUpperCase();
      const PLATE_REGEX = /^(?:[A-Z]{2}\d{2}[A-Z]{2}(?!0000)\d{4}|\d{2}BH(?!0000)\d{4}[A-Z]{2})$/;
      if (!cleanPlate) {
        e.licensePlate = 'License plate is required';
      } else if (cleanPlate.length !== 10) {
        e.licensePlate = 'Number plate must have exactly 10 characters (e.g. TN72AB1234)';
      } else if (!PLATE_REGEX.test(cleanPlate)) {
        e.licensePlate = 'Invalid number plate format. Must be a valid plate (e.g. TN72AB1234)';
      }
      if (!form.carType) e.carType = 'Car type is required';
    }
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    setLoading(true);

    try {
      const cleanPhone = form.phoneNumber.trim().replace(/[-\s]/g, '');
      const cleanPlate = form.licensePlate.trim().replace(/[-\s]/g, '').toUpperCase();
      const res = mode === 'register'
        ? await authApi.register({
            ...form,
            role,
            phoneNumber: cleanPhone,
            licensePlate: role === 'DRIVER' ? cleanPlate : undefined,
            carType: role === 'DRIVER' ? form.carType : undefined,
          })
        : await authApi.login({ email: form.email, password: form.password });

      setAuth(res.data);
      toast.success(`Welcome to BidDrive! 🎉`);
      navigate(res.data.role === 'DRIVER' ? '/driver' : '/passenger');
    } catch (err: any) {
      const errData = err.response?.data;
      const msg =
        (typeof errData === 'string' ? errData : null) ??
        errData?.error ??
        errData?.message ??
        (err.response ? 'Authentication failed' : 'Cannot connect to backend server. Please verify the backend is running.');
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleQuickDemo = async (demoRole: 'PASSENGER' | 'DRIVER') => {
    setMode('login');
    const demoEmail = demoRole === 'PASSENGER' ? 'passenger.test@demo.com' : 'driver.test@demo.com';
    const demoPassword = 'Password123!';
    setForm((f) => ({ ...f, email: demoEmail, password: demoPassword }));
    setLoading(true);
    try {
      let res;
      try {
        res = await authApi.login({ email: demoEmail, password: demoPassword });
      } catch {

        if (demoRole === 'PASSENGER') {
          await authApi.register({
            name: 'Rahul Sharma',
            email: demoEmail,
            password: demoPassword,
            phoneNumber: '9876543210',
            role: 'PASSENGER',
          });
        } else {
          await authApi.register({
            name: 'Rajesh Auto',
            email: demoEmail,
            password: demoPassword,
            phoneNumber: '9876543211',
            role: 'DRIVER',
            carType: 'Auto',
            licensePlate: 'KA05BD1234',
          });
        }
        res = await authApi.login({ email: demoEmail, password: demoPassword });
      }
      setAuth(res.data);
      toast.success(`Logged in as ${demoRole === 'PASSENGER' ? 'Passenger (Rahul Sharma)' : 'Driver (Rajesh Auto)'}! 🎉`);
      navigate(res.data.role === 'DRIVER' ? '/driver' : '/passenger');
    } catch {
      toast.error('Demo login failed. Please ensure backend and Redis are running.');
    } finally {
      setLoading(false);
    }
  };

  const VEHICLE_TYPES = [
    'Bike',
    'Scooty',
    'Auto',
    'Hatchback',
    'Sedan',
    'SUV',
  ];

  return (
    <div className="auth-page">

      <div style={{
        position: 'absolute', width: 800, height: 800,
        background: 'radial-gradient(circle, rgba(108,99,255,0.07) 0%, transparent 70%)',
        top: '50%', left: '50%', transform: 'translate(-50%,-50%)',
        pointerEvents: 'none',
      }} />

      <div className="auth-card animate-pop">

        <div className="auth-logo">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, marginBottom: 8 }}>
            <div style={{
              width: 48, height: 48, borderRadius: 'var(--radius-md)',
              background: 'var(--gradient-primary)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: 'var(--shadow-glow)',
            }}>
              <Zap size={24} color="#fff" />
            </div>
          </div>
          <div className="auth-logo-text">BidDrive</div>
          <div className="auth-logo-sub">The reverse-bidding ride platform</div>
        </div>

        <div className="auth-tabs">
          <button className={`auth-tab ${mode === 'login' ? 'active' : ''}`} onClick={() => setMode('login')}>
            Sign In
          </button>
          <button className={`auth-tab ${mode === 'register' ? 'active' : ''}`} onClick={() => setMode('register')}>
            Register
          </button>
        </div>

        {mode === 'register' && (
          <div style={{ display: 'flex', gap: 10, marginBottom: 24 }}>
            {(['PASSENGER', 'DRIVER'] as Role[]).map((r) => (
              <button
                key={r}
                type="button"
                onClick={() => setRole(r)}
                style={{
                  flex: 1, padding: '12px', borderRadius: 'var(--radius-md)',
                  background: role === r ? (r === 'DRIVER' ? 'rgba(67,233,123,0.1)' : 'rgba(108,99,255,0.1)') : 'var(--bg-elevated)',
                  border: `1px solid ${role === r ? (r === 'DRIVER' ? 'rgba(67,233,123,0.4)' : 'rgba(108,99,255,0.4)') : 'var(--border-default)'}`,
                  color: role === r ? (r === 'DRIVER' ? '#43E97B' : '#6C63FF') : 'var(--text-secondary)',
                  display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6,
                  cursor: 'pointer', transition: 'all 0.2s', fontFamily: 'inherit',
                }}
              >
                {r === 'PASSENGER' ? <User size={20} /> : <Car size={20} />}
                <span style={{ fontSize: 12, fontWeight: 700 }}>{r}</span>
              </button>
            ))}
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {mode === 'register' && (
            <div className="form-group">
              <label className="form-label">Full Name</label>
              <input
                className={`form-input ${errors.name ? 'error' : ''}`}
                placeholder="John Doe"
                value={form.name}
                onChange={(e) => setField('name', e.target.value)}
              />
              {errors.name && <span className="form-error">{errors.name}</span>}
            </div>
          )}

          <div className="form-group">
            <label className="form-label">Email Address</label>
            <input
              className={`form-input ${errors.email ? 'error' : ''}`}
              type="email"
              placeholder="you@example.com"
              value={form.email}
              onChange={(e) => setField('email', e.target.value)}
            />
            {errors.email && <span className="form-error">{errors.email}</span>}
          </div>

          <div className="form-group">
            <label className="form-label">Password</label>
            <div style={{ position: 'relative' }}>
              <input
                className={`form-input ${errors.password ? 'error' : ''}`}
                type={showPassword ? 'text' : 'password'}
                placeholder="Min. 8 characters"
                value={form.password}
                onChange={(e) => setField('password', e.target.value)}
                style={{ paddingRight: 44 }}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', display: 'flex' }}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            {errors.password && <span className="form-error">{errors.password}</span>}
          </div>

          {mode === 'register' && (
            <>
              <div className="form-group">
                <label className="form-label">Phone Number (10 digits)</label>
                <input
                  className={`form-input ${errors.phoneNumber ? 'error' : ''}`}
                  placeholder="9876543210"
                  maxLength={10}
                  value={form.phoneNumber}
                  onChange={(e) => setField('phoneNumber', e.target.value.replace(/\D/g, ''))}
                />
                {errors.phoneNumber && <span className="form-error">{errors.phoneNumber}</span>}
              </div>

              {role === 'DRIVER' && (
                <>
                  <div className="form-group">
                    <label className="form-label">Vehicle Number / License Plate (10 characters)</label>
                    <input
                      className={`form-input ${errors.licensePlate ? 'error' : ''}`}
                      placeholder="TN72AB1234"
                      maxLength={10}
                      value={form.licensePlate}
                      onChange={(e) => setField('licensePlate', e.target.value.replace(/[^a-zA-Z0-9]/g, '').toUpperCase())}
                    />
                    {errors.licensePlate && <span className="form-error">{errors.licensePlate}</span>}
                  </div>
                  <div className="form-group">
                    <label className="form-label">Vehicle Type</label>
                    <select
                      className={`form-input form-select ${errors.carType ? 'error' : ''}`}
                      value={form.carType}
                      onChange={(e) => setField('carType', e.target.value)}
                    >
                      <option value="">Select your vehicle</option>
                      {VEHICLE_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                    </select>
                    {errors.carType && <span className="form-error">{errors.carType}</span>}
                  </div>
                </>
              )}
            </>
          )}

          <button
            type="submit"
            className="btn btn-primary btn-lg btn-full mt-2"
            disabled={loading}
          >
            {loading ? (
              <span className="spinner spinner-sm" />
            ) : (
              <>
                {mode === 'login' ? 'Sign In' : 'Create Account'}
                <ArrowRight size={18} />
              </>
            )}
          </button>
        </form>

        <div style={{ marginTop: 24, paddingTop: 18, borderTop: '1px solid var(--border-subtle)' }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.5, textAlign: 'center', marginBottom: 12 }}>
            ⚡ Mentor & Showcase 1-Click Demo Access
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <button
              type="button"
              onClick={() => handleQuickDemo('PASSENGER')}
              disabled={loading}
              className="btn btn-secondary btn-sm"
              style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, fontSize: 12, padding: '10px 8px', borderRadius: 10 }}
            >
              <User size={15} color="#6C63FF" />
              <span>Demo Passenger</span>
            </button>
            <button
              type="button"
              onClick={() => handleQuickDemo('DRIVER')}
              disabled={loading}
              className="btn btn-secondary btn-sm"
              style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, fontSize: 12, padding: '10px 8px', borderRadius: 10 }}
            >
              <Car size={15} color="#43E97B" />
              <span>Demo Driver</span>
            </button>
          </div>
        </div>

        <p style={{ textAlign: 'center', marginTop: 16, fontSize: 13, color: 'var(--text-muted)' }}>
          {mode === 'login' ? "Don't have an account?" : 'Already have an account?'}
          {' '}
          <button
            onClick={() => setMode(mode === 'login' ? 'register' : 'login')}
            style={{ background: 'none', border: 'none', color: 'var(--brand-primary)', fontWeight: 600, cursor: 'pointer', fontSize: 13 }}
          >
            {mode === 'login' ? 'Register' : 'Sign In'}
          </button>
        </p>
      </div>
    </div>
  );
};

export default LoginPage;
