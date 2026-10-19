import React, { useEffect, useState } from 'react';
import { DollarSign, TrendingUp, CreditCard, Clock } from 'lucide-react';
import { walletApi, type DriverWallet } from '../../lib/api';

const WalletPage: React.FC = () => {
  const [wallet, setWallet] = useState<DriverWallet | null>(null);
  const [fareInfo, setFareInfo] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([walletApi.getMyWallet(), walletApi.getFareInfo()])
      .then(([w, f]) => {
        setWallet(w.data);
        setFareInfo(f.data);
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 300 }}>
      <span className="spinner" />
    </div>
  );

  return (
    <div className="animate-fade">
      <div className="page-header">
        <h1 className="page-title">My Wallet</h1>
        <p className="page-subtitle">Your earnings and payout information</p>
      </div>

      <div className="page-body">

        <div className="wallet-card" style={{ marginBottom: 24 }}>
          <div className="wallet-label">Available Balance</div>
          <div className="wallet-balance">
            ₹{wallet ? Number(wallet.availableBalance).toFixed(2) : '0.00'}
          </div>
          <div style={{ marginTop: 24, display: 'flex', gap: 32 }}>
            <div>
              <div className="wallet-label">Total Earned</div>
              <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--text-primary)', marginTop: 4 }}>
                ₹{wallet ? Number(wallet.totalEarnings).toFixed(2) : '0.00'}
              </div>
            </div>
            <div>
              <div className="wallet-label">Rides Completed</div>
              <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--text-primary)', marginTop: 4 }}>
                {wallet?.totalRides ?? 0}
              </div>
            </div>
          </div>
        </div>

        {fareInfo && (
          <div className="card" style={{ marginBottom: 24 }}>
            <h3 style={{ fontWeight: 700, marginBottom: 16 }}>Platform Commission</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div className="flex justify-between items-center">
                <span className="text-secondary">Commission Rate</span>
                <span className="badge badge-initiated">{fareInfo.platformCommissionPercent}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-secondary">You Keep</span>
                <span className="text-success text-bold">{fareInfo.note.split('Driver earns ')[1]?.split(' of')[0]}</span>
              </div>
              <div className="divider" />
              <div className="alert alert-info">
                <CreditCard size={16} />
                <span>{fareInfo.note}</span>
              </div>
            </div>
          </div>
        )}

        <div className="stats-grid">
          <div className="stat-card">
            <div className="stat-icon stat-icon-gold">
              <DollarSign size={22} />
            </div>
            <div className="stat-content">
              <div className="stat-value text-gold">
                ₹{wallet ? (Number(wallet.totalEarnings) / Math.max(wallet.totalRides, 1)).toFixed(0) : '0'}
              </div>
              <div className="stat-label">Avg per Ride</div>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-icon stat-icon-success">
              <TrendingUp size={22} />
            </div>
            <div className="stat-content">
              <div className="stat-value text-success">{wallet?.totalRides ?? 0}</div>
              <div className="stat-label">Total Rides</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default WalletPage;
