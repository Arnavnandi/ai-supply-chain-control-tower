import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { ShieldCheck, AlertTriangle, ShieldAlert, RefreshCw } from 'lucide-react';
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
        <span className="flex items-center space-x-1 px-2.5 py-1 rounded-full bg-emerald-950/80 border border-emerald-800 text-emerald-400 text-xs font-bold">
          <ShieldCheck className="w-3.5 h-3.5" />
          <span>Preferred Low Risk</span>
        </span>
      );
    }
    if (riskClass === 'MODERATE_RISK') {
      return (
        <span className="flex items-center space-x-1 px-2.5 py-1 rounded-full bg-amber-950/80 border border-amber-800 text-amber-400 text-xs font-bold">
          <AlertTriangle className="w-3.5 h-3.5" />
          <span>Moderate Risk</span>
        </span>
      );
    }
    return (
      <span className="flex items-center space-x-1 px-2.5 py-1 rounded-full bg-rose-950/80 border border-rose-800 text-rose-400 text-xs font-bold">
        <ShieldAlert className="w-3.5 h-3.5" />
        <span>Critical Risk</span>
      </span>
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-white">Supplier Performance & OTIF Index</h2>
          <p className="text-xs text-slate-400">Evaluate supplier lead time variance, OTIF scores, and vendor risk matrix</p>
        </div>
        {analytics && (
          <div className="flex items-center space-x-3 text-xs font-mono">
            <div className="px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-slate-300">
              Avg OTIF: <span className="font-bold text-emerald-400">
                {analytics.averageSystemOtifPct != null ? `${analytics.averageSystemOtifPct}%` : 'N/A (No Shipments)'}
              </span>
            </div>
            <div className="px-3 py-1.5 rounded-lg bg-emerald-950/50 border border-emerald-800 text-emerald-300">
              Low Risk: <span className="font-bold">{analytics.lowRiskSuppliersCount || 0}</span>
            </div>
            <div className="px-3 py-1.5 rounded-lg bg-amber-950/50 border border-amber-800 text-amber-300">
              Moderate: <span className="font-bold">{analytics.moderateRiskSuppliersCount || 0}</span>
            </div>
          </div>
        )}
      </div>

      {loading ? (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800">
          <div className="flex flex-col items-center justify-center space-y-3 text-slate-400 text-xs">
            <RefreshCw className="w-6 h-6 animate-spin text-cyan-400" />
            <span>Loading supplier directory and performance metrics...</span>
          </div>
        </div>
      ) : error ? (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4">
          <div className="max-w-md mx-auto space-y-3">
            <div className="flex items-center justify-center space-x-2 text-amber-400">
              <AlertTriangle className="w-5 h-5" />
              <h3 className="text-sm font-semibold">Unable to Load Suppliers</h3>
            </div>
            <p className="text-xs text-slate-400 leading-relaxed">{error}</p>
            <button
              type="button"
              onClick={fetchSuppliers}
              className="inline-flex items-center space-x-1.5 px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold border border-slate-700 transition-all cursor-pointer"
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
              <div key={s.id} className="glass-panel p-5 rounded-xl border border-slate-800 space-y-4">
                <div className="flex items-start justify-between">
                  <div>
                    <span className="text-xs font-mono text-cyan-400 font-semibold">{s.code}</span>
                    <h3 className="text-lg font-bold text-white">{s.name}</h3>
                    <p className="text-xs text-slate-400">{s.country} • Contact: {s.contactPerson} ({s.email})</p>
                  </div>
                  {renderBadge(metric?.riskClassification)}
                </div>

                <div className="grid grid-cols-3 gap-3 pt-2 border-t border-slate-800/80 text-xs">
                  <div className="p-3 rounded-lg bg-slate-900/60 border border-slate-800">
                    <span className="text-slate-400">OTIF Score</span>
                    <p className="text-base font-bold text-emerald-400 mt-0.5">
                      {otifVal != null ? `${otifVal}%` : 'N/A'}
                    </p>
                  </div>
                  <div className="p-3 rounded-lg bg-slate-900/60 border border-slate-800">
                    <span className="text-slate-400">Reliability</span>
                    <p className="text-base font-bold text-cyan-400 mt-0.5">
                      {s.reliabilityScore != null ? `${s.reliabilityScore}%` : 'N/A'}
                    </p>
                  </div>
                <div className="p-3 rounded-lg bg-slate-900/60 border border-slate-800">
                  <span className="text-slate-400">Lead Time</span>
                  <p className="text-base font-bold text-slate-200 mt-0.5">{s.averageLeadTimeDays}d (±{s.leadTimeVarianceDays}d)</p>
                </div>
              </div>

              {metric?.contractedSkus && metric.contractedSkus.length > 0 && (
                <div className="pt-1 flex items-center space-x-1.5 flex-wrap gap-y-1">
                  <span className="text-[10px] text-slate-400 uppercase font-semibold mr-1">Contracted SKUs:</span>
                  {metric.contractedSkus.slice(0, 4).map((sku: string) => (
                    <span key={sku} className="px-2 py-0.5 rounded bg-slate-800/80 text-slate-300 font-mono text-[10px] border border-slate-700">
                      {sku}
                    </span>
                  ))}
                  {metric.contractedSkus.length > 4 && (
                    <span className="text-[10px] text-slate-400 font-mono">+{metric.contractedSkus.length - 4} more</span>
                  )}
                </div>
              )}
            </div>
          );
        })}
        </div>
      ) : (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4">
          <div className="max-w-md mx-auto space-y-3">
            <h3 className="text-base font-bold text-white">No Registered Suppliers Found</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Upload suppliers.csv via the Ingestion Pipeline to evaluate vendor reliability, baseline lead times, and on-time delivery performance.
            </p>
            <a
              href="/import"
              className="inline-flex items-center px-4 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/20 transition-all"
            >
              Upload suppliers.csv
            </a>
          </div>
        </div>
      )}
    </div>
  );
};
