import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';

export const RisksPage: React.FC = () => {
  const [risks, setRisks] = useState<any[]>([]);

  useEffect(() => {
    const fetchRisks = async () => {
      try {
        const res = await axiosInstance.get('/risks');
        setRisks(res.data);
      } catch (err) {
        console.error(err);
      }
    };
    fetchRisks();
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-white">Supply Chain Operational Risk Monitor</h2>
        <p className="text-xs text-slate-400">Real-time risk scoring across stockouts, supplier delays, and warehouse capacity</p>
      </div>

      {risks.length > 0 ? (
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
