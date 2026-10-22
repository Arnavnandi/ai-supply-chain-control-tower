import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { classifyApiError } from '../utils/errorClassification';

export const RisksPage: React.FC = () => {
  const [risks, setRisks] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchRisks = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await axiosInstance.get('/risks');
      setRisks(res.data);
    } catch (err: any) {
      console.error('Failed to load risks:', err);
      const classified = classifyApiError(err, 'Failed to load operational risk alerts.');
      setError(classified.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRisks();
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-white">Supply Chain Operational Risk Monitor</h2>
        <p className="text-xs text-slate-400">Real-time risk scoring across stockouts, supplier delays, and warehouse capacity</p>
      </div>

      {loading ? (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800">
          <div className="flex flex-col items-center justify-center space-y-3 text-slate-400 text-xs">
            <RefreshCw className="w-6 h-6 animate-spin text-cyan-400" />
            <span>Loading real-time operational risk alerts...</span>
          </div>
        </div>
      ) : error ? (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4">
          <div className="max-w-md mx-auto space-y-3">
            <div className="flex items-center justify-center space-x-2 text-amber-400">
              <AlertTriangle className="w-5 h-5" />
              <h3 className="text-sm font-semibold">Unable to Load Operational Risks</h3>
            </div>
            <p className="text-xs text-slate-400 leading-relaxed">{error}</p>
            <button
              type="button"
              onClick={fetchRisks}
              className="inline-flex items-center space-x-1.5 px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold border border-slate-700 transition-all cursor-pointer"
            >
              <RefreshCw className="w-3.5 h-3.5 text-cyan-400" />
              <span>Retry</span>
            </button>
          </div>
        </div>
      ) : risks.length > 0 ? (
        <div className="space-y-4">
          {risks.map((r) => (
            <div key={r.id} className="glass-panel p-5 rounded-xl border border-slate-800 space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                    r.severityLevel === 'CRITICAL' ? 'bg-rose-950 text-rose-400 border border-rose-800' :
                    r.severityLevel === 'HIGH' ? 'bg-amber-950 text-amber-400 border border-amber-800' :
                    'bg-blue-950 text-blue-400 border border-blue-800'
                  }`}>
                    {r.severityLevel} SEVERITY
                  </span>
                  <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">{r.riskCategory}</span>
                </div>
                <span className="text-xs text-slate-500">{new Date(r.createdAt).toLocaleString()}</span>
              </div>

              <p className="text-sm text-slate-200 font-medium">{r.description}</p>

              {r.recommendationText && (
                <div className="p-3 rounded-lg bg-slate-900/80 border border-slate-800 text-xs text-cyan-300">
                  <span className="font-semibold text-white">AI Mitigation Recommendation: </span>
                  {r.recommendationText}
                </div>
              )}
            </div>
          ))}
        </div>
      ) : (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4">
          <div className="max-w-md mx-auto space-y-3">
            <h3 className="text-base font-bold text-white">No Active Operational Risk Alerts</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Operational risks are generated strictly when actual records violate safety parameters (inventory stockouts, supplier lead time delays, or warehouse capacity overflow). Current operational state is nominal or awaiting data import.
            </p>
            <a
              href="/import"
              className="inline-flex items-center px-4 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/20 transition-all"
            >
              Check Ingestion Pipeline
            </a>
          </div>
        </div>
      )}
    </div>
  );
};
