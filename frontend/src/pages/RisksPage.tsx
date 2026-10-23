import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance';
import { AlertTriangle, RefreshCw, ShieldAlert, Sparkles } from 'lucide-react';
import { classifyApiError } from '../utils/errorClassification';

export interface DisplayRiskItem {
  id: string | number;
  severityLevel: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | string;
  riskCategory: string;
  description: string;
  recommendationText?: string;
  createdAt?: string;
  source: 'LIVE_TELEMETRY' | 'PERSISTED_ALERT';
}

export const RisksPage: React.FC = () => {
  const [risks, setRisks] = useState<DisplayRiskItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchRisks = async (isManualRefresh = false) => {
    if (isManualRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    setError(null);

    try {
      // Query both dynamic operational risks and persisted historical alerts
      const [intelSettled, persistedSettled] = await Promise.allSettled([
        axiosInstance.get('/intelligence/risks'),
        axiosInstance.get('/risks'),
      ]);

      const items: DisplayRiskItem[] = [];
      const seenIds = new Set<string>();

      // 1. Process dynamic operational risks from live database telemetry
      if (intelSettled.status === 'fulfilled' && intelSettled.value.data?.riskItems) {
        const liveItems = intelSettled.value.data.riskItems;
        for (const r of liveItems) {
          const itemKey = `live-${r.id || r.title}`;
          if (!seenIds.has(itemKey)) {
            seenIds.add(itemKey);
            items.push({
              id: itemKey,
              severityLevel: (r.severity || 'HIGH').toUpperCase(),
              riskCategory: r.category || 'OPERATIONAL',
              description: r.problemDetected
                ? `${r.problemDetected}${r.dataCause ? ` — ${r.dataCause}` : ''}`
                : r.title || 'Operational anomaly detected',
              recommendationText: r.actionRecommended,
              source: 'LIVE_TELEMETRY',
            });
          }
        }
      }

      // 2. Process persisted alerts from relational database
      if (persistedSettled.status === 'fulfilled' && Array.isArray(persistedSettled.value.data)) {
        for (const p of persistedSettled.value.data) {
          const itemKey = `alert-${p.id}`;
          if (!seenIds.has(itemKey)) {
            seenIds.add(itemKey);
            items.push({
              id: itemKey,
              severityLevel: (p.severityLevel || 'HIGH').toUpperCase(),
              riskCategory: p.riskCategory || 'OPERATIONAL',
              description: p.description,
              recommendationText: p.recommendationText,
              createdAt: p.createdAt,
              source: 'PERSISTED_ALERT',
            });
          }
        }
      }

      // Determine error state: never show a fake empty result if a request failed
      const intelError = intelSettled.status === 'rejected' ? intelSettled.reason : null;
      const persistedError = persistedSettled.status === 'rejected' ? persistedSettled.reason : null;

      if (intelError || persistedError) {
        const primaryError = intelError || persistedError;
        const classified = classifyApiError(primaryError, 'Failed to load operational risk alerts.');

        // If auth failed, or if no items were recovered from either endpoint, surface the error
        if (classified.isAuthError || items.length === 0) {
          console.error('Failed to load operational risks:', primaryError);
          setError(classified.message);
          return;
        }

        // If partial data was recovered, log warning but show available items
        console.warn('Partial telemetry degradation when loading operational risks:', primaryError);
      }

      setRisks(items);
      setError(null);
    } catch (err: any) {
      console.error('Unexpected error loading risks:', err);
      const classified = classifyApiError(err, 'Failed to load operational risk alerts.');
      setError(classified.message);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchRisks(false);
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <ShieldAlert className="w-5 h-5 text-cyan-400" />
            <span>Supply Chain Operational Risk Monitor</span>
          </h2>
          <p className="text-xs text-slate-400">
            Real-time risk scoring across stockouts, supplier delays, and warehouse capacity
          </p>
        </div>

        <button
          type="button"
          onClick={() => fetchRisks(true)}
          disabled={loading || refreshing}
          className="inline-flex items-center space-x-2 px-3.5 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-200 text-xs font-semibold border border-slate-800 hover:border-slate-700 transition-all cursor-pointer disabled:opacity-50 self-start sm:self-auto"
        >
          <RefreshCw className={`w-3.5 h-3.5 text-cyan-400 ${refreshing ? 'animate-spin' : ''}`} />
          <span>{refreshing ? 'Scanning Telemetry...' : 'Refresh Risks'}</span>
        </button>
      </div>

      {loading ? (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800">
          <div className="flex flex-col items-center justify-center space-y-3 text-slate-400 text-xs">
            <RefreshCw className="w-6 h-6 animate-spin text-cyan-400" />
            <span>Scanning real-time operational risk alerts across stockouts, suppliers, and shipments...</span>
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
              onClick={() => fetchRisks(false)}
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
                  <span
                    className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                      r.severityLevel === 'CRITICAL'
                        ? 'bg-rose-950 text-rose-400 border border-rose-800'
                        : r.severityLevel === 'HIGH'
                        ? 'bg-amber-950 text-amber-400 border border-amber-800'
                        : 'bg-blue-950 text-blue-400 border border-blue-800'
                    }`}
                  >
                    {r.severityLevel} SEVERITY
                  </span>
                  <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    {r.riskCategory}
                  </span>
                  {r.source === 'LIVE_TELEMETRY' && (
                    <span className="inline-flex items-center gap-1 text-[10px] font-medium text-cyan-400 bg-cyan-950/60 border border-cyan-800/60 px-2 py-0.5 rounded-md">
                      <Sparkles className="w-2.5 h-2.5" />
                      Live Analysis
                    </span>
                  )}
                </div>
                {r.createdAt && (
                  <span className="text-xs text-slate-500">
                    {new Date(r.createdAt).toLocaleString()}
                  </span>
                )}
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
            <div className="flex flex-wrap items-center justify-center gap-3 pt-1">
              <button
                type="button"
                onClick={() => fetchRisks(true)}
                disabled={refreshing}
                className="inline-flex items-center space-x-1.5 px-4 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold border border-slate-700 transition-all cursor-pointer"
              >
                <RefreshCw className={`w-3.5 h-3.5 text-cyan-400 ${refreshing ? 'animate-spin' : ''}`} />
                <span>Refresh Risk Telemetry</span>
              </button>
              <Link
                to="/import"
                className="inline-flex items-center px-4 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/20 transition-all"
              >
                Check Ingestion Pipeline
              </Link>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

