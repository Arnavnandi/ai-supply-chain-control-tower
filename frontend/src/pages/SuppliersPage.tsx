import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { ShieldCheck, AlertTriangle, ShieldAlert } from 'lucide-react';

export const SuppliersPage: React.FC = () => {
  const [suppliers, setSuppliers] = useState<any[]>([]);
  const [analytics, setAnalytics] = useState<any>(null);

  useEffect(() => {
    const fetchSuppliers = async () => {
      try {
        const [supRes, analyticsRes] = await Promise.all([
          axiosInstance.get('/suppliers'),
          axiosInstance.get('/analytics/suppliers')
        ]);
        setSuppliers(supRes.data);
        setAnalytics(analyticsRes.data);
      } catch (err) {
        console.error(err);
      }
    };
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
              Avg OTIF: <span className="font-bold text-emerald-400">{analytics.averageSystemOtifPct}%</span>
            </div>
            <div className="px-3 py-1.5 rounded-lg bg-emerald-950/50 border border-emerald-800 text-emerald-300">
              Low Risk: <span className="font-bold">{analytics.lowRiskSuppliersCount}</span>
            </div>
            <div className="px-3 py-1.5 rounded-lg bg-amber-950/50 border border-amber-800 text-amber-300">
              Moderate: <span className="font-bold">{analytics.moderateRiskSuppliersCount}</span>
            </div>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {suppliers.map((s) => {
          const metric = getMetric(s.id);
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
                  <p className="text-base font-bold text-emerald-400 mt-0.5">{metric?.otifScorePct ?? s.deliveryPerformancePct}%</p>
                </div>
                <div className="p-3 rounded-lg bg-slate-900/60 border border-slate-800">
                  <span className="text-slate-400">Reliability</span>
                  <p className="text-base font-bold text-cyan-400 mt-0.5">{s.reliabilityScore}%</p>
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
    </div>
  );
};
