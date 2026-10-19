import React from 'react';
import { useAuthStore } from '../../lib/store';
import { Star } from 'lucide-react';

const RatingsPage: React.FC = () => {
  const { role } = useAuthStore();

  return (
    <div className="animate-fade">
      <div className="page-header">
        <h1 className="page-title">Ratings & Reviews</h1>
        <p className="page-subtitle">Your reputation on the BidDrive platform</p>
      </div>
      <div className="page-body">
        <div className="card" style={{ textAlign: 'center', padding: 48 }}>
          <div style={{ fontSize: 64, marginBottom: 16 }}>⭐</div>
          <h2 style={{ fontSize: 22, fontWeight: 800, marginBottom: 8 }}>Your Ratings</h2>
          <p style={{ color: 'var(--text-secondary)', marginBottom: 24, maxWidth: 360, margin: '0 auto 24px' }}>
            {role === 'DRIVER'
              ? 'Ratings from passengers appear here after each completed trip.'
              : 'Your driver ratings and reviews will appear here.'}
          </p>
          <div style={{ display: 'flex', gap: 8, justifyContent: 'center', marginBottom: 24 }}>
            {[1, 2, 3, 4, 5].map((s) => (
              <Star key={s} size={32} fill="#F7B731" color="#F7B731" />
            ))}
          </div>
          <div className="alert alert-info" style={{ maxWidth: 400, margin: '0 auto', textAlign: 'left' }}>
            <Star size={16} />
            Complete more rides to build your reputation and unlock higher earnings.
          </div>
        </div>
      </div>
    </div>
  );
};

export default RatingsPage;
