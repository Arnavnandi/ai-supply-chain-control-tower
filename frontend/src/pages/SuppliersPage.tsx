import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { Star } from 'lucide-react';

export const SuppliersPage: React.FC = () => {
  const [suppliers, setSuppliers] = useState<any[]>([]);

  useEffect(() => {
    const fetchSuppliers = async () => {
      try {
        const res = await axiosInstance.get('/suppliers');
        setSuppliers(res.data);
      } catch (err) {
        console.error(err);
      }
    };
    fetchSuppliers();
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-white">Supplier Performance & Reliability Index</h2>
        <p className="text-xs text-slate-400">Evaluate supplier lead time variance, contract price, and fulfillment SLA</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {suppliers.map((s) => (
          <div key={s.id} className="glass-panel p-5 rounded-xl border border-slate-800 space-y-4">
            <div className="flex items-start justify-between">
              <div>
                <span className="text-xs font-mono text-cyan-400 font-semibold">{s.code}</span>
                <h3 className="text-lg font-bold text-white">{s.name}</h3>
                <p className="text-xs text-slate-400">{s.country} • Contact: {s.contactPerson} ({s.email})</p>
              </div>
              <div className="flex items-center space-x-1 px-2.5 py-1 rounded-full bg-cyan-950 border border-cyan-800 text-cyan-300 text-xs font-bold">
                <Star className="w-3.5 h-3.5 fill-cyan-400 text-cyan-400" />
                <span>{s.reliabilityScore}% Reliability</span>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3 pt-2 border-t border-slate-800/80 text-xs">
              <div className="p-3 rounded-lg bg-slate-900/60 border border-slate-800">
                <span className="text-slate-400">On-Time Delivery</span>
                <p className="text-base font-bold text-emerald-400 mt-0.5">{s.deliveryPerformancePct}%</p>
              </div>
              <div className="p-3 rounded-lg bg-slate-900/60 border border-slate-800">
                <span className="text-slate-400">Avg Lead Time</span>
                <p className="text-base font-bold text-slate-200 mt-0.5">{s.averageLeadTimeDays} days (±{s.leadTimeVarianceDays}d)</p>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
