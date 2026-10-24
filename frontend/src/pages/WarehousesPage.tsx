import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance';
import { MapPin, RefreshCw, AlertTriangle, User, Mail } from 'lucide-react';
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
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2.5">
            <h2 className="text-xl font-bold text-white tracking-tight">Regional Warehouse Distribution Hubs</h2>
            {!loading && (
              <span className="px-2.5 py-0.5 rounded-full text-xs font-mono font-semibold bg-cyan-950/70 text-cyan-400 border border-cyan-800/60">
                {warehouses.length} Facilities
              </span>
            )}
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Storage capacity buffers, live cubic space utilization, and regional facility leadership
          </p>
        </div>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
          {Array.from({ length: 3 }).map((_, idx) => (
            <div key={idx} className="glass-panel p-5 rounded-2xl border border-slate-800 animate-pulse space-y-4">
              <div className="h-5 w-32 bg-slate-800 rounded skeleton-shimmer" />
              <div className="h-4 w-48 bg-slate-800 rounded skeleton-shimmer" />
              <div className="h-3 w-full bg-slate-800 rounded skeleton-shimmer" />
              <div className="h-4 w-24 bg-slate-800 rounded skeleton-shimmer ml-auto" />
            </div>
          ))}
        </div>
      ) : error ? (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4 shadow-2xl">
          <div className="max-w-md mx-auto space-y-3">
            <div className="flex items-center justify-center space-x-2 text-rose-400">
              <AlertTriangle className="w-5 h-5" />
              <h3 className="text-sm font-semibold">Unable to Load Warehouses</h3>
            </div>
            <p className="text-xs text-slate-400 leading-relaxed">{error}</p>
            <button
              type="button"
              onClick={fetchWarehouses}
              className="inline-flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold border border-slate-700 transition-all cursor-pointer shadow-md"
            >
              <RefreshCw className="w-3.5 h-3.5 text-cyan-400" />
              <span>Retry</span>
            </button>
          </div>
        </div>
      ) : warehouses.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
          {warehouses.map((w) => (
            <div
              key={w.id}
              className="glass-panel p-5 rounded-2xl border border-slate-800/80 hover:border-slate-700/90 transition-all shadow-xl space-y-4"
            >
              <div className="flex items-start justify-between">
                <div>
                  <span className="text-xs font-mono font-semibold px-2 py-0.5 rounded bg-cyan-950/60 border border-cyan-800/50 text-cyan-400">
                    {w.code}
                  </span>
                  <h3 className="text-lg font-bold text-white mt-1.5 tracking-tight">{w.name}</h3>
                  <p className="text-xs text-slate-400 flex items-center space-x-1.5 mt-0.5">
                    <MapPin className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                    <span>{w.location}</span>
                  </p>
                </div>
              </div>

              {/* Capacity Utilization Meter */}
              <div className="space-y-2 pt-2 border-t border-slate-800/80">
                <div className="flex justify-between text-xs font-semibold">
                  <span className="text-slate-400">Storage Capacity Utilization</span>
                  <span className={`font-mono font-bold ${w.utilizationPercentage > 90 ? 'text-rose-400' : w.utilizationPercentage > 75 ? 'text-amber-400' : 'text-cyan-400'}`}>
                    {w.utilizationPercentage}%
                  </span>
                </div>
                <div className="w-full h-2 rounded-full bg-slate-900 overflow-hidden border border-slate-800">
                  <div
                    className={`h-full rounded-full transition-all duration-500 ${
                      w.utilizationPercentage > 90
                        ? 'bg-rose-500 shadow-xs shadow-rose-500/50'
                        : w.utilizationPercentage > 75
                        ? 'bg-amber-500 shadow-xs shadow-amber-500/50'
                        : 'bg-cyan-500 shadow-xs shadow-cyan-500/50'
                    }`}
                    style={{ width: `${w.utilizationPercentage}%` }}
                  />
                </div>
                <p className="text-[11px] text-slate-400 font-mono text-right">
                  <span className="text-slate-200 font-semibold">{w.currentUtilizationUnits?.toLocaleString()}</span>
                  <span className="text-slate-500"> / {w.totalCapacityUnits?.toLocaleString()} units</span>
                </p>
              </div>

              {/* Manager Details */}
              <div className="pt-3 border-t border-slate-800/80 text-xs text-slate-400 space-y-1">
                <p className="flex items-center space-x-1.5">
                  <User className="w-3 h-3 text-slate-500" />
                  <span>Manager: <span className="text-slate-200 font-medium">{w.managerName}</span></span>
                </p>
                <p className="flex items-center space-x-1.5">
                  <Mail className="w-3 h-3 text-slate-500" />
                  <span>Contact: <span className="text-slate-400 font-mono text-[11px]">{w.contactEmail}</span></span>
                </p>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4 shadow-2xl">
          <div className="max-w-md mx-auto space-y-3">
            <h3 className="text-base font-bold text-white">No Warehouse Distribution Centers Found</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Upload warehouses.csv via the Ingestion Pipeline to define regional distribution hubs, storage capacities, and manager assignments.
            </p>
            <Link
              to="/import"
              className="inline-flex items-center px-4 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/30 transition-all"
            >
              Upload warehouses.csv
            </Link>
          </div>
        </div>
      )}
    </div>
  );
};
