import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';

export const ShipmentsPage: React.FC = () => {
  const [shipments, setShipments] = useState<any[]>([]);

  useEffect(() => {
    const fetchShipments = async () => {
      try {
        const res = await axiosInstance.get('/shipments');
        setShipments(res.data);
      } catch (err) {
        console.error(err);
      }
    };
    fetchShipments();
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-white">Logistics & Active Shipment Delays</h2>
        <p className="text-xs text-slate-400">Transit tracking across global suppliers and regional hubs</p>
      </div>

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
