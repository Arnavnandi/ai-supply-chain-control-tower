import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance';
import { ShieldCheck, AlertTriangle, ShieldAlert, RefreshCw, Mail, Globe } from 'lucide-react';
import { classifyApiError } from '../utils/errorClassification';

export const SuppliersPage: React.FC = () => {
  const [suppliers, setSuppliers] = useState<any[]>([]);
  const [analytics, setAnalytics] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchSuppliers = async () => {
    setLoading(true);
    setError(null);
    try {
      const [supRes, analyticsRes] = await Promise.all([
        axiosInstance.get('/suppliers'),
        axiosInstance.get('/analytics/suppliers')
      ]);
      setSuppliers(supRes.data);
      setAnalytics(analyticsRes.data);
    } catch (err: any) {
      console.error('Failed to load suppliers:', err);
      const classified = classifyApiError(err, 'Failed to load supplier records and performance metrics.');
      setError(classified.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSuppliers();
  }, []);

  const getMetric = (supplierId: number) => {
    if (!analytics?.supplierMetrics) return null;
    return analytics.supplierMetrics.find((m: any) => m.supplierId === supplierId);
  };

  const renderBadge = (riskClass?: string) => {
    if (riskClass === 'PREFERRED_LOW_RISK') {
      return (
        <span className="flex items-center space-x-1 px-2.5 py-1 rounded-full bg-emerald-950/80 border border-emerald-800/70 text-emerald-300 text-xs font-bold shadow-xs shadow-emerald-950/40">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
          <span>Preferred Low Risk</span>
        </span>
      );
    }
    if (riskClass === 'MODERATE_RISK') {
      return (
        <span className="flex items-center space-x-1 px-2.5 py-1 rounded-full bg-amber-950/80 border border-amber-800/70 text-amber-300 text-xs font-bold shadow-xs shadow-amber-950/40">
          <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />
          <span>Moderate Risk</span>
        </span>
      );
    }
    return (
      <span className="flex items-center space-x-1 px-2.5 py-1 rounded-full bg-rose-950/80 border border-rose-800/70 text-rose-300 text-xs font-bold shadow-xs shadow-rose-950/40">
        <ShieldAlert className="w-3.5 h-3.5 text-rose-400" />
        <span>Critical Risk</span>
      </span>
    );
  };

  return (
    <div className="space-y-6">
      {/* Header and Telemetry */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2.5">
            <h2 className="text-xl font-bold text-white tracking-tight">Supplier Performance & OTIF Index</h2>
            {!loading && (
              <span className="px-2.5 py-0.5 rounded-full text-xs font-mono font-semibold bg-cyan-950/70 text-cyan-400 border border-cyan-800/60">
                {suppliers.length} Vendors
              </span>
            )}
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Supplier lead time variance, OTIF scores, and vendor resiliency matrix
          </p>
        </div>

        {analytics && (
          <div className="flex items-center space-x-2.5 text-xs font-mono flex-wrap gap-y-2">
            <div className="px-3.5 py-1.5 rounded-xl bg-slate-900/90 border border-slate-800 text-slate-300 shadow-inner">
              Avg OTIF:{' '}
              <span className="font-bold text-emerald-400">
                {analytics.averageSystemOtifPct != null ? `${analytics.averageSystemOtifPct}%` : 'N/A (No Shipments)'}
              </span>
            </div>
            <div className="px-3.5 py-1.5 rounded-xl bg-emerald-950/50 border border-emerald-800/60 text-emerald-300">
              Low Risk: <span className="font-bold">{analytics.lowRiskSuppliersCount || 0}</span>
            </div>
            <div className="px-3.5 py-1.5 rounded-xl bg-amber-950/50 border border-amber-800/60 text-amber-300">
              Moderate: <span className="font-bold">{analytics.moderateRiskSuppliersCount || 0}</span>
            </div>
          </div>
        )}
      </div>

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {Array.from({ length: 4 }).map((_, idx) => (
            <div key={idx} className="glass-panel p-5 rounded-2xl border border-slate-800 animate-pulse space-y-4">
              <div className="h-5 w-40 bg-slate-800 rounded skeleton-shimmer" />
              <div className="h-4 w-60 bg-slate-800 rounded skeleton-shimmer" />
              <div className="grid grid-cols-3 gap-3">
                <div className="h-14 bg-slate-800 rounded skeleton-shimmer" />
                <div className="h-14 bg-slate-800 rounded skeleton-shimmer" />
                <div className="h-14 bg-slate-800 rounded skeleton-shimmer" />
              </div>
            </div>
          ))}
        </div>
      ) : error ? (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4 shadow-2xl">
          <div className="max-w-md mx-auto space-y-3">
            <div className="flex items-center justify-center space-x-2 text-rose-400">
              <AlertTriangle className="w-5 h-5" />
              <h3 className="text-sm font-semibold">Unable to Load Suppliers</h3>
            </div>
            <p className="text-xs text-slate-400 leading-relaxed">{error}</p>
            <button
              type="button"
              onClick={fetchSuppliers}
              className="inline-flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold border border-slate-700 transition-all cursor-pointer shadow-md"
            >
              <RefreshCw className="w-3.5 h-3.5 text-cyan-400" />
              <span>Retry</span>
            </button>
          </div>
        </div>
      ) : suppliers.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {suppliers.map((s) => {
            const metric = getMetric(s.id);
            const otifVal = metric?.otifScorePct ?? s.deliveryPerformancePct;
            return (
              <div
                key={s.id}
                className="glass-panel p-5 rounded-2xl border border-slate-800/80 hover:border-slate-700/90 transition-all shadow-xl space-y-4"
              >
                <div className="flex items-start justify-between">
                  <div>
                    <span className="text-xs font-mono font-semibold px-2 py-0.5 rounded bg-cyan-950/60 border border-cyan-800/50 text-cyan-400">
                      {s.code}
                    </span>
                    <h3 className="text-lg font-bold text-white mt-1.5 tracking-tight">{s.name}</h3>
                    <p className="text-xs text-slate-400 flex items-center space-x-1.5 mt-0.5">
                      <Globe className="w-3 h-3 text-slate-500" />
                      <span>{s.country}</span>
                      <span className="text-slate-600">•</span>
                      <Mail className="w-3 h-3 text-slate-500" />
                      <span>{s.contactPerson} ({s.email})</span>
                    </p>
                  </div>
                  {renderBadge(metric?.riskClassification)}
                </div>

                <div className="grid grid-cols-3 gap-3 pt-2 border-t border-slate-800/80 text-xs">
                  <div className="p-3 rounded-xl bg-slate-900/70 border border-slate-800">
                    <span className="text-slate-400 text-[11px] font-medium">OTIF Score</span>
                    <p className="text-base font-bold font-mono text-emerald-400 mt-0.5">
                      {otifVal != null ? `${otifVal}%` : 'N/A'}
                    </p>
                  </div>
                  <div className="p-3 rounded-xl bg-slate-900/70 border border-slate-800">
                    <span className="text-slate-400 text-[11px] font-medium">Reliability</span>
                    <p className="text-base font-bold font-mono text-cyan-400 mt-0.5">
                      {s.reliabilityScore != null ? `${s.reliabilityScore}%` : 'N/A'}
                    </p>
                  </div>
                  <div className="p-3 rounded-xl bg-slate-900/70 border border-slate-800">
                    <span className="text-slate-400 text-[11px] font-medium">Lead Time</span>
                    <p className="text-base font-bold font-mono text-slate-200 mt-0.5">
                      {s.averageLeadTimeDays}d <span className="text-slate-500 text-xs font-normal">±{s.leadTimeVarianceDays}d</span>
                    </p>
                  </div>
                </div>

                {metric?.contractedSkus && metric.contractedSkus.length > 0 && (
                  <div className="pt-2 border-t border-slate-800/60 flex items-center space-x-1.5 flex-wrap gap-y-1">
                    <span className="text-[10px] text-slate-400 uppercase font-semibold mr-1">Contracted SKUs:</span>
                    {metric.contractedSkus.slice(0, 4).map((sku: string) => (
                      <span
                        key={sku}
                        className="px-2 py-0.5 rounded bg-slate-900 text-slate-300 font-mono text-[10px] border border-slate-700/80"
                      >
                        {sku}
                      </span>
                    ))}
                    {metric.contractedSkus.length > 4 && (
                      <span className="text-[10px] text-slate-400 font-mono">
                        +{metric.contractedSkus.length - 4} more
                      </span>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      ) : (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4 shadow-2xl">
          <div className="max-w-md mx-auto space-y-3">
            <h3 className="text-base font-bold text-white">No Registered Suppliers Found</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Upload suppliers.csv via the Ingestion Pipeline to evaluate vendor reliability, baseline lead times, and on-time delivery performance.
            </p>
            <Link
              to="/import"
              className="inline-flex items-center px-4 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/30 transition-all"
            >
              Upload suppliers.csv
            </Link>
          </div>
        </div>
      )}
    </div>
  );
};
