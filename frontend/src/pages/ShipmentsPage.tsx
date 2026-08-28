import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { Truck, AlertTriangle } from 'lucide-react';

export const ShipmentsPage: React.FC = () => {
  const [shipments, setShipments] = useState<any[]>([]);
  const [analytics, setAnalytics] = useState<any>(null);

  useEffect(() => {
    const fetchShipments = async () => {
      try {
        const [shipRes, analyticsRes] = await Promise.all([
          axiosInstance.get('/shipments'),
          axiosInstance.get('/analytics/logistics')
        ]);
        setShipments(shipRes.data);
        setAnalytics(analyticsRes.data);
      } catch (err) {
        console.error(err);
      }
    };
    fetchShipments();
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-white">Logistics & Active Shipment Congestion</h2>
          <p className="text-xs text-slate-400">Transit tracking across global suppliers, carriers, and regional distribution hubs</p>
        </div>
        {analytics && (
          <div className="flex items-center space-x-3 text-xs font-mono">
            <div className="px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-slate-300">
              Total Shipments: <span className="font-bold text-cyan-400">{analytics.totalShipments}</span>
            </div>
            <div className="px-3 py-1.5 rounded-lg bg-rose-950/50 border border-rose-800 text-rose-300">
              Active Delayed: <span className="font-bold">{analytics.activeDelayedShipments}</span>
            </div>
            <div className="px-3 py-1.5 rounded-lg bg-amber-950/50 border border-amber-800 text-amber-300">
              System Avg Delay: <span className="font-bold">{analytics.averageDelayDaysSystem} days</span>
            </div>
          </div>
        )}
      </div>

      {analytics?.carrierMetrics && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {analytics.carrierMetrics.map((c: any) => (
            <div key={c.carrierName} className="glass-panel p-4 rounded-xl border border-slate-800 space-y-2">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-2">
                  <Truck className="w-4 h-4 text-cyan-400" />
                  <span className="text-sm font-bold text-white">{c.carrierName}</span>
                </div>
                <span className="text-xs font-mono px-2 py-0.5 rounded bg-slate-800 text-cyan-300 font-bold">
                  {c.onTimePerformancePct}% On-Time
                </span>
              </div>
              <div className="flex items-center justify-between text-xs text-slate-400 pt-1">
                <span>Shipments: {c.totalShipments} ({c.delayedShipments} delayed)</span>
                <span>Avg Delay: {c.averageDelayDays}d</span>
              </div>
            </div>
          ))}
        </div>
      )}

      {analytics?.topCongestedRoutes && analytics.topCongestedRoutes.length > 0 && (
        <div className="glass-panel p-4 rounded-xl border border-slate-800 space-y-3">
          <div className="flex items-center space-x-2 text-xs font-bold text-amber-400 uppercase tracking-wider">
            <AlertTriangle className="w-4 h-4" />
            <span>Top Congested Transit Bottlenecks</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
            {analytics.topCongestedRoutes.map((r: any, idx: number) => (
              <div key={idx} className="p-3 rounded-lg bg-slate-900/60 border border-slate-800 flex items-center justify-between">
                <div>
                  <p className="font-bold text-white">{r.origin} ➔ {r.destination}</p>
                  <p className="text-slate-400 text-[11px]">Total: {r.shipmentCount} | Delayed: {r.delayedCount}</p>
                </div>
                <span className={`px-2 py-1 rounded text-[10px] font-bold ${
                  r.congestionLevel === 'HIGH' ? 'bg-rose-950 text-rose-400 border border-rose-800' : 'bg-amber-950 text-amber-400 border border-amber-800'
                }`}>
                  {r.congestionLevel} (Avg +{r.averageDelayDays}d)
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="glass-panel rounded-xl overflow-hidden border border-slate-800">
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
            {shipments.map((s) => (
              <tr key={s.id} className="hover:bg-slate-800/40 transition-colors">
                <td className="px-6 py-4 font-mono text-cyan-400 font-semibold">{s.trackingCode}</td>
                <td className="px-6 py-4 text-white font-medium">{s.supplierName}</td>
                <td className="px-6 py-4 text-xs text-slate-400">{s.origin} ➔ {s.destination}</td>
                <td className="px-6 py-4">{s.carrierName}</td>
                <td className="px-6 py-4">{s.estimatedDeliveryDate}</td>
                <td className="px-6 py-4">
                  <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                    s.status === 'DELAYED' ? 'bg-rose-950 text-rose-400 border border-rose-800' :
                    s.status === 'IN_TRANSIT' ? 'bg-blue-950 text-blue-400 border border-blue-800' :
                    'bg-emerald-950 text-emerald-400 border border-emerald-800'
                  }`}>
                    {s.status} {s.delayDays ? `(+${s.delayDays}d)` : ''}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
