import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance';
import { MapPin, RefreshCw, AlertTriangle } from 'lucide-react';
import { classifyApiError } from '../utils/errorClassification';

export const WarehousesPage: React.FC = () => {
  const [warehouses, setWarehouses] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchWarehouses = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await axiosInstance.get('/warehouses');
      setWarehouses(res.data);
    } catch (err: any) {
      console.error('Failed to load warehouses:', err);
      const classified = classifyApiError(err, 'Failed to load warehouse distribution centers.');
      setError(classified.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchWarehouses();
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-white">Regional Warehouse Distribution Centers</h2>
        <p className="text-xs text-slate-400">Capacity limits, current storage utilization, and facility management</p>
      </div>

      {loading ? (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800">
          <div className="flex flex-col items-center justify-center space-y-3 text-slate-400 text-xs">
            <RefreshCw className="w-6 h-6 animate-spin text-cyan-400" />
            <span>Loading regional warehouse distribution centers...</span>
          </div>
        </div>
      ) : error ? (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4">
          <div className="max-w-md mx-auto space-y-3">
            <div className="flex items-center justify-center space-x-2 text-amber-400">
              <AlertTriangle className="w-5 h-5" />
              <h3 className="text-sm font-semibold">Unable to Load Warehouses</h3>
            </div>
            <p className="text-xs text-slate-400 leading-relaxed">{error}</p>
            <button
              type="button"
              onClick={fetchWarehouses}
              className="inline-flex items-center space-x-1.5 px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold border border-slate-700 transition-all cursor-pointer"
            >
              <RefreshCw className="w-3.5 h-3.5 text-cyan-400" />
              <span>Retry</span>
            </button>
          </div>
        </div>
      ) : warehouses.length > 0 ? (
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
      ) : (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4">
          <div className="max-w-md mx-auto space-y-3">
            <h3 className="text-base font-bold text-white">No Warehouse Distribution Centers Found</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Upload warehouses.csv via the Ingestion Pipeline to define regional distribution hubs, storage capacities, and manager assignments.
            </p>
            <Link
              to="/import"
              className="inline-flex items-center px-4 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/20 transition-all"
            >
              Upload warehouses.csv
            </Link>
          </div>
        </div>
      )}
    </div>
  );
};
