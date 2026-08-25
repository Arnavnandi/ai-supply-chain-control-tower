import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { MapPin } from 'lucide-react';

export const WarehousesPage: React.FC = () => {
  const [warehouses, setWarehouses] = useState<any[]>([]);

  useEffect(() => {
    const fetchWarehouses = async () => {
      try {
        const res = await axiosInstance.get('/warehouses');
        setWarehouses(res.data);
      } catch (err) {
        console.error(err);
      }
    };
    fetchWarehouses();
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-white">Regional Warehouse Distribution Centers</h2>
        <p className="text-xs text-slate-400">Capacity limits, current storage utilization, and facility management</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        {warehouses.map((w) => (
          <div key={w.id} className="glass-panel p-5 rounded-xl border border-slate-800 space-y-4">
            <div className="flex items-start justify-between">
              <div>
                <span className="text-xs font-mono text-cyan-400 font-semibold">{w.code}</span>
                <h3 className="text-lg font-bold text-white">{w.name}</h3>
                <p className="text-xs text-slate-400 flex items-center space-x-1 mt-1">
                  <MapPin className="w-3.5 h-3.5 text-slate-500" />
                  <span>{w.location}</span>
                </p>
              </div>
            </div>

            <div className="space-y-2">
              <div className="flex justify-between text-xs font-semibold">
                <span className="text-slate-400">Storage Capacity Utilization</span>
                <span className={w.utilizationPercentage > 90 ? 'text-rose-400' : 'text-cyan-400'}>{w.utilizationPercentage}%</span>
              </div>
              <div className="w-full h-2.5 rounded-full bg-slate-900 overflow-hidden border border-slate-800">
                <div
                  className={`h-full rounded-full transition-all ${
                    w.utilizationPercentage > 90 ? 'bg-rose-500' : w.utilizationPercentage > 75 ? 'bg-amber-500' : 'bg-cyan-500'
                  }`}
                  style={{ width: `${w.utilizationPercentage}%` }}
                />
              </div>
              <p className="text-xs text-slate-400 text-right">
                {w.currentUtilizationUnits?.toLocaleString()} / {w.totalCapacityUnits?.toLocaleString()} units
              </p>
            </div>

            <div className="pt-3 border-t border-slate-800/80 text-xs text-slate-400">
              <p>Manager: <span className="text-slate-200 font-medium">{w.managerName}</span></p>
              <p>Contact: <span className="text-slate-400">{w.contactEmail}</span></p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
