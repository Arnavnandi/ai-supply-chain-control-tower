import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance';
import { Truck, AlertTriangle, RefreshCw } from 'lucide-react';
import { classifyApiError } from '../utils/errorClassification';

export const ShipmentsPage: React.FC = () => {
  const [shipments, setShipments] = useState<any[]>([]);
  const [analytics, setAnalytics] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchShipments = async () => {
    setLoading(true);
    setError(null);
    try {
      const [shipRes, analyticsRes] = await Promise.all([
        axiosInstance.get('/shipments'),
        axiosInstance.get('/analytics/logistics')
      ]);
      setShipments(shipRes.data);
      setAnalytics(analyticsRes.data);
    } catch (err: any) {
      console.error('Failed to load shipments:', err);
      const classified = classifyApiError(err, 'Failed to load shipment logistics.');
      setError(classified.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchShipments();
  }, []);

  return (
    <div className="space-y-6">
      {/* Header and Telemetry */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2.5">
            <h2 className="text-xl font-bold text-white tracking-tight">Logistics & Active Shipment Congestion</h2>
            {!loading && (
              <span className="px-2.5 py-0.5 rounded-full text-xs font-mono font-semibold bg-cyan-950/70 text-cyan-400 border border-cyan-800/60">
                {shipments.length} Active
              </span>
            )}
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Transit tracking across global suppliers, freight carriers, and regional distribution hubs
          </p>
        </div>

        {analytics && (
          <div className="flex items-center space-x-2.5 text-xs font-mono flex-wrap gap-y-2">
            <div className="px-3.5 py-1.5 rounded-xl bg-slate-900/90 border border-slate-800 text-slate-300 shadow-inner">
              Total Shipments: <span className="font-bold text-cyan-400">{analytics.totalShipments}</span>
            </div>
            <div className="px-3.5 py-1.5 rounded-xl bg-rose-950/50 border border-rose-800/60 text-rose-300">
              Active Delayed: <span className="font-bold">{analytics.activeDelayedShipments}</span>
            </div>
            <div className="px-3.5 py-1.5 rounded-xl bg-amber-950/50 border border-amber-800/60 text-amber-300">
              System Avg Delay: <span className="font-bold">{analytics.averageDelayDaysSystem != null ? `${analytics.averageDelayDaysSystem} days` : 'N/A'}</span>
            </div>
          </div>
        )}
      </div>

      {/* Carrier Performance Cards */}
      {analytics?.carrierMetrics && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {analytics.carrierMetrics.map((c: any) => (
            <div key={c.carrierName} className="glass-panel p-4 rounded-xl border border-slate-800 space-y-2.5 shadow-md">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-2">
                  <div className="p-1.5 rounded-lg bg-cyan-950/70 border border-cyan-800/50">
                    <Truck className="w-4 h-4 text-cyan-400" />
                  </div>
                  <span className="text-sm font-bold text-white tracking-tight">{c.carrierName}</span>
                </div>
                <span className="text-xs font-mono px-2 py-0.5 rounded bg-cyan-950/60 text-cyan-300 border border-cyan-800/50 font-bold">
                  {c.onTimePerformancePct}% On-Time
                </span>
              </div>
              <div className="flex items-center justify-between text-xs text-slate-400 pt-1 border-t border-slate-800/60">
                <span>Shipments: <strong className="text-slate-200">{c.totalShipments}</strong> ({c.delayedShipments} delayed)</span>
                <span>Avg Delay: <strong className="text-amber-400">{c.averageDelayDays}d</strong></span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Congested Transit Bottlenecks */}
      {analytics?.topCongestedRoutes && analytics.topCongestedRoutes.length > 0 && (
        <div className="glass-panel p-5 rounded-xl border border-amber-500/30 bg-slate-900/60 space-y-3 shadow-lg">
          <div className="flex items-center space-x-2 text-xs font-bold text-amber-400 uppercase tracking-wider">
            <AlertTriangle className="w-4 h-4 text-amber-400" />
            <span>Top Congested Transit Bottlenecks</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
            {analytics.topCongestedRoutes.map((r: any, idx: number) => (
              <div key={idx} className="p-3.5 rounded-xl bg-slate-950/70 border border-slate-800/80 flex items-center justify-between">
                <div>
                  <p className="font-bold text-white text-xs">{r.origin} ➔ {r.destination}</p>
                  <p className="text-slate-400 text-[11px] mt-0.5 font-mono">
                    Total: {r.shipmentCount} | Delayed: <span className="text-rose-400">{r.delayedCount}</span>
                  </p>
                </div>
                <span className={`px-2.5 py-1 rounded-full text-[10px] font-bold ${
                  r.congestionLevel === 'HIGH' ? 'bg-rose-950/80 text-rose-300 border border-rose-800/60 shadow-xs shadow-rose-900/30' : 'bg-amber-950/80 text-amber-300 border border-amber-800/60'
                }`}>
                  {r.congestionLevel} (Avg +{r.averageDelayDays}d)
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Main Table: MUST preserve exact class structure for AuditFixesBatch2.test.js */}
      <div className="glass-panel rounded-xl overflow-hidden border border-slate-800">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="bg-slate-900/80 text-xs font-semibold uppercase tracking-wider text-slate-400 border-b border-slate-800">
              <tr>
                <th className="px-6 py-4">Tracking Code</th>
                <th className="px-6 py-4">Supplier</th>
                <th className="px-6 py-4">Origin ➔ Destination</th>
                <th className="px-6 py-4">Carrier</th>
                <th className="px-6 py-4">Estimated Delivery</th>
                <th className="px-6 py-4">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {loading ? (
                Array.from({ length: 5 }).map((_, idx) => (
                  <tr key={idx} className="animate-pulse">
                    <td className="px-6 py-4"><div className="h-4 w-28 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-36 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-40 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-24 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-24 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-20 bg-slate-800 rounded skeleton-shimmer" /></td>
                  </tr>
                ))
              ) : error ? (
                <tr>
                  <td colSpan={6} className="text-center py-12 px-4">
                    <div className="max-w-md mx-auto space-y-3">
                      <div className="flex items-center justify-center space-x-2 text-rose-400">
                        <AlertTriangle className="w-5 h-5" />
                        <p className="text-sm font-semibold">Unable to Load Shipments</p>
                      </div>
                      <p className="text-xs text-slate-400">{error}</p>
                      <button
                        type="button"
                        onClick={fetchShipments}
                        className="inline-flex items-center space-x-1.5 px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold border border-slate-700 transition-all cursor-pointer"
                      >
                        <RefreshCw className="w-3.5 h-3.5 text-cyan-400" />
                        <span>Retry</span>
                      </button>
                    </div>
                  </td>
                </tr>
              ) : shipments.length === 0 ? (
                <tr>
                  <td colSpan={6} className="text-center py-12 px-4">
                    <div className="max-w-md mx-auto space-y-3">
                      <p className="text-sm font-semibold text-slate-300">No active shipment freight records found.</p>
                      <p className="text-xs text-slate-500">
                        Upload shipments.csv via the Ingestion Pipeline to track freight carrier performance, transit routes, and delay congestion.
                      </p>
                      <Link
                        to="/import"
                        className="inline-flex items-center px-4 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-md transition-all"
                      >
                        Upload shipments.csv
                      </Link>
                    </div>
                  </td>
                </tr>
              ) : (
                shipments.map((s) => (
                  <tr key={s.id} className="hover:bg-slate-800/40 transition-colors">
                    <td className="px-6 py-4 font-mono text-cyan-400 font-semibold">
                      <div className="flex items-center space-x-2">
                        <span>{s.trackingCode}</span>
                        {s.isDemo && (
                          <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-950/80 border border-amber-500/40 text-amber-400">
                            Demo Data
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-6 py-4 text-white font-medium">{s.supplierName}</td>
                    <td className="px-6 py-4 text-xs text-slate-400">{s.origin} ➔ {s.destination}</td>
                    <td className="px-6 py-4 text-slate-300">{s.carrierName}</td>
                    <td className="px-6 py-4 font-mono text-xs text-slate-300">{s.estimatedDeliveryDate}</td>
                    <td className="px-6 py-4">
                      <span className={`px-2.5 py-1 rounded-full text-xs font-bold inline-flex items-center space-x-1 ${
                        s.status === 'DELAYED' ? 'bg-rose-950 text-rose-400 border border-rose-800' :
                        s.status === 'IN_TRANSIT' ? 'bg-blue-950 text-blue-400 border border-blue-800' :
                        'bg-emerald-950 text-emerald-400 border border-emerald-800'
                      }`}>
                        <span>{s.status} {s.delayDays ? `(+${s.delayDays}d)` : ''}</span>
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
