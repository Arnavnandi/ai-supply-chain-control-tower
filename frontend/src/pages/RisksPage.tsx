import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance';
import { AlertTriangle, RefreshCw, ShieldAlert, Sparkles, CheckCircle2 } from 'lucide-react';
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
      {/* Header and Refresh Control */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2.5">
            <h2 className="text-xl font-bold text-white tracking-tight flex items-center gap-2">
              <ShieldAlert className="w-5 h-5 text-cyan-400" />
              <span>Supply Chain Operational Risk Monitor</span>
            </h2>
            {!loading && (
              <span className="px-2.5 py-0.5 rounded-full text-xs font-mono font-semibold bg-rose-950/70 text-rose-400 border border-rose-800/60">
                {risks.length} Active Risks
              </span>
            )}
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Real-time multi-echelon risk scoring across stockouts, supplier variances, and warehouse capacity
          </p>
        </div>

        <button
          type="button"
          onClick={() => fetchRisks(true)}
          disabled={loading || refreshing}
          className="inline-flex items-center space-x-2 px-4 py-2.5 rounded-xl bg-slate-900/90 hover:bg-slate-800 text-slate-200 text-xs font-semibold border border-slate-700/80 hover:border-cyan-500/40 transition-all cursor-pointer disabled:opacity-50 self-start sm:self-auto shadow-md"
        >
          <RefreshCw className={`w-3.5 h-3.5 text-cyan-400 ${refreshing ? 'animate-spin' : ''}`} />
          <span>{refreshing ? 'Scanning Telemetry...' : 'Refresh Risk Radar'}</span>
        </button>
      </div>

      {loading ? (
        <div className="space-y-4">
          {Array.from({ length: 3 }).map((_, idx) => (
            <div key={idx} className="glass-panel p-5 rounded-2xl border border-slate-800 animate-pulse space-y-3">
              <div className="flex items-center space-x-3">
                <div className="h-5 w-28 bg-slate-800 rounded skeleton-shimmer" />
                <div className="h-5 w-24 bg-slate-800 rounded skeleton-shimmer" />
              </div>
              <div className="h-4 w-3/4 bg-slate-800 rounded skeleton-shimmer" />
              <div className="h-12 w-full bg-slate-800/60 rounded-xl skeleton-shimmer" />
            </div>
          ))}
        </div>
      ) : error ? (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4 shadow-2xl">
          <div className="max-w-md mx-auto space-y-3">
            <div className="flex items-center justify-center space-x-2 text-rose-400">
              <AlertTriangle className="w-5 h-5" />
              <h3 className="text-sm font-semibold">Unable to Load Operational Risks</h3>
            </div>
            <p className="text-xs text-slate-400 leading-relaxed">{error}</p>
            <button
              type="button"
              onClick={() => fetchRisks(false)}
              className="inline-flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold border border-slate-700 transition-all cursor-pointer shadow-md"
            >
              <RefreshCw className="w-3.5 h-3.5 text-cyan-400" />
              <span>Retry</span>
            </button>
          </div>
        </div>
      ) : risks.length > 0 ? (
        <div className="space-y-4">
          {risks.map((r) => (
            <div
              key={r.id}
              className="glass-panel p-5 rounded-2xl border border-slate-800/80 hover:border-slate-700/90 transition-all shadow-xl space-y-3"
            >
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div className="flex items-center space-x-2.5 flex-wrap gap-y-1">
                  <span
                    className={`px-2.5 py-0.5 rounded-full text-[11px] font-bold tracking-wider uppercase inline-flex items-center space-x-1 ${
                      r.severityLevel === 'CRITICAL'
                        ? 'bg-rose-950/90 text-rose-300 border border-rose-800/70 shadow-xs shadow-rose-900/40'
                        : r.severityLevel === 'HIGH'
                        ? 'bg-amber-950/90 text-amber-300 border border-amber-800/70 shadow-xs shadow-amber-900/40'
                        : 'bg-blue-950/90 text-blue-300 border border-blue-800/70'
                    }`}
                  >
                    <span>{r.severityLevel} SEVERITY</span>
                  </span>
                  <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider px-2 py-0.5 rounded bg-slate-900 border border-slate-800">
                    {r.riskCategory}
                  </span>
                  {r.source === 'LIVE_TELEMETRY' && (
                    <span className="inline-flex items-center gap-1 text-[10px] font-semibold text-cyan-300 bg-cyan-950/60 border border-cyan-800/60 px-2 py-0.5 rounded-md">
                      <Sparkles className="w-2.5 h-2.5 text-cyan-400" />
                      Live Telemetry Analysis
                    </span>
                  )}
                </div>
                {r.createdAt && (
                  <span className="text-[11px] text-slate-500 font-mono">
                    {new Date(r.createdAt).toLocaleString()}
                  </span>
                )}
              </div>

              <p className="text-sm text-slate-200 font-medium leading-relaxed">{r.description}</p>

              {r.recommendationText && (
                <div className="p-3.5 rounded-xl bg-cyan-950/30 border border-cyan-800/40 text-xs text-cyan-200 leading-relaxed space-y-1">
                  <div className="flex items-center space-x-1.5 text-cyan-400 font-semibold text-[11px] uppercase tracking-wider">
                    <Sparkles className="w-3 h-3" />
                    <span>AI Mitigation Recommendation</span>
                  </div>
                  <p className="text-slate-300">{r.recommendationText}</p>
                </div>
              )}
            </div>
          ))}
        </div>
      ) : (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4 shadow-2xl">
          <div className="max-w-md mx-auto space-y-3">
            <CheckCircle2 className="w-10 h-10 text-emerald-400 mx-auto" />
            <h3 className="text-base font-bold text-white">No Active Operational Risk Alerts</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Operational risks are generated strictly when actual records violate safety parameters (inventory stockouts, supplier lead time delays, or warehouse capacity overflow). Current operational state is nominal or awaiting data import.
            </p>
            <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
              <button
                type="button"
                onClick={() => fetchRisks(true)}
                disabled={refreshing}
                className="inline-flex items-center space-x-1.5 px-4 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold border border-slate-700 transition-all cursor-pointer shadow-md"
              >
                <RefreshCw className={`w-3.5 h-3.5 text-cyan-400 ${refreshing ? 'animate-spin' : ''}`} />
                <span>Refresh Risk Telemetry</span>
              </button>
              <Link
                to="/import"
                className="inline-flex items-center px-4 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/30 transition-all"
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
