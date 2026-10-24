import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance';
import { RefreshCw, AlertTriangle, CheckCircle2, Warehouse, SlidersHorizontal, ArrowUpDown } from 'lucide-react';
import { classifyApiError } from '../utils/errorClassification';

export const InventoryPage: React.FC = () => {
  const [inventory, setInventory] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<'ALL' | 'LOW_STOCK' | 'OVERSTOCK'>('ALL');
  const [selectedItem, setSelectedItem] = useState<any>(null);
  const [adjustQty, setAdjustQty] = useState(0);
  const [adjustSubmitting, setAdjustSubmitting] = useState(false);
  const [adjustError, setAdjustError] = useState<string | null>(null);
  const [successToast, setSuccessToast] = useState<string | null>(null);

  const fetchInventory = async () => {
    setLoading(true);
    setError(null);
    try {
      const url = filter === 'LOW_STOCK' ? '/inventory/low-stock' : filter === 'OVERSTOCK' ? '/inventory/overstock' : '/inventory';
      const res = await axiosInstance.get(url);
      setInventory(res.data);
    } catch (err: any) {
      console.error('Failed to load inventory stock:', err);
      const classified = classifyApiError(err, 'Failed to load inventory stock records.');
      setError(classified.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInventory();
  }, [filter]);

  const handleAdjust = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedItem) return;
    setAdjustSubmitting(true);
    setAdjustError(null);
    try {
      await axiosInstance.post('/inventory/adjust', {
        productId: selectedItem.productId,
        warehouseId: selectedItem.warehouseId,
        adjustmentQty: Number(adjustQty)
      });
      const adjustedName = selectedItem.productName;
      setSelectedItem(null);
      setAdjustQty(0);
      setSuccessToast(`Stock adjusted successfully for ${adjustedName}`);
      setTimeout(() => setSuccessToast(null), 4000);
      fetchInventory();
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Inventory adjustment failed.';
      setAdjustError(msg);
    } finally {
      setAdjustSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {successToast && (
        <div className="p-3.5 rounded-xl bg-emerald-950/80 border border-emerald-500/50 text-emerald-300 text-xs flex items-center space-x-2.5 shadow-lg shadow-emerald-950/40 animate-fade-in">
          <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
          <span className="font-medium">{successToast}</span>
        </div>
      )}

      {/* Header and Filter Selector */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2.5">
            <h2 className="text-xl font-bold text-white tracking-tight">Multi-Warehouse Inventory Stock</h2>
            {!loading && (
              <span className="px-2.5 py-0.5 rounded-full text-xs font-mono font-semibold bg-cyan-950/70 text-cyan-400 border border-cyan-800/60">
                {inventory.length} Records
              </span>
            )}
          </div>
          <p className="text-xs text-slate-400 mt-1">Live stock levels, safety buffers, and manual adjustment controls across hubs</p>
        </div>

        {/* Filter Pills */}
        <div className="flex items-center space-x-1.5 bg-slate-900/90 p-1 rounded-xl border border-slate-800 text-xs shadow-inner">
          <button
            onClick={() => setFilter('ALL')}
            className={`px-3.5 py-1.5 rounded-lg font-semibold transition-all ${
              filter === 'ALL'
                ? 'bg-cyan-500 text-white shadow-md shadow-cyan-500/25'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
            }`}
          >
            All Inventory
          </button>
          <button
            onClick={() => setFilter('LOW_STOCK')}
            className={`px-3.5 py-1.5 rounded-lg font-semibold transition-all ${
              filter === 'LOW_STOCK'
                ? 'bg-amber-500 text-slate-950 shadow-md shadow-amber-500/25'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
            }`}
          >
            Low Stock Alerts
          </button>
          <button
            onClick={() => setFilter('OVERSTOCK')}
            className={`px-3.5 py-1.5 rounded-lg font-semibold transition-all ${
              filter === 'OVERSTOCK'
                ? 'bg-purple-600 text-white shadow-md shadow-purple-600/25'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
            }`}
          >
            Overstocked SKU
          </button>
        </div>
      </div>

      {/* Inventory Table */}
      <div className="glass-panel rounded-xl overflow-hidden border border-slate-800/80 shadow-2xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="bg-slate-900/90 text-[11px] font-semibold uppercase tracking-wider text-slate-400 border-b border-slate-800/90">
              <tr>
                <th className="px-6 py-4">Product SKU & Name</th>
                <th className="px-6 py-4">Warehouse Location</th>
                <th className="px-6 py-4">Available Qty</th>
                <th className="px-6 py-4">Reorder Level</th>
                <th className="px-6 py-4">Safety Stock</th>
                <th className="px-6 py-4">Status</th>
                <th className="px-6 py-4 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-xs">
              {loading ? (
                Array.from({ length: 5 }).map((_, idx) => (
                  <tr key={idx} className="animate-pulse">
                    <td className="px-6 py-4"><div className="h-4 w-40 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-32 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-20 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-20 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-20 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-24 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4 text-right"><div className="h-6 w-20 bg-slate-800 rounded skeleton-shimmer ml-auto" /></td>
                  </tr>
                ))
              ) : error ? (
                <tr>
                  <td colSpan={7} className="text-center py-14 px-4">
                    <div className="max-w-md mx-auto space-y-3">
                      <div className="flex items-center justify-center space-x-2 text-rose-400">
                        <AlertTriangle className="w-5 h-5" />
                        <p className="text-sm font-semibold">Unable to Load Inventory</p>
                      </div>
                      <p className="text-xs text-slate-400 leading-relaxed">{error}</p>
                      <button
                        type="button"
                        onClick={fetchInventory}
                        className="inline-flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold border border-slate-700 transition-all cursor-pointer shadow-md"
                      >
                        <RefreshCw className="w-3.5 h-3.5 text-cyan-400" />
                        <span>Retry</span>
                      </button>
                    </div>
                  </td>
                </tr>
              ) : inventory.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center py-14 px-4">
                    <div className="max-w-md mx-auto space-y-3">
                      <p className="text-sm font-semibold text-slate-300">
                        {filter === 'ALL'
                          ? 'No multi-warehouse inventory stock records found.'
                          : `No items matching filter "${filter}".`}
                      </p>
                      <p className="text-xs text-slate-500 leading-relaxed">
                        {filter === 'ALL'
                          ? 'Upload inventories.csv to associate product SKUs with regional warehouse facilities and track live stock buffers.'
                          : 'Operational stock is nominal with no items currently in this status.'}
                      </p>
                      {filter === 'ALL' && (
                        <Link
                          to="/import"
                          className="inline-flex items-center px-4 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/30 transition-all"
                        >
                          Upload inventories.csv
                        </Link>
                      )}
                    </div>
                  </td>
                </tr>
              ) : (
                inventory.map((inv) => (
                  <tr key={inv.id} className="hover:bg-slate-800/40 transition-colors">
                    <td className="px-6 py-4">
                      <div className="flex items-center space-x-2">
                        <p className="font-semibold text-white">{inv.productName}</p>
                        {inv.isDemo && (
                          <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-950/80 border border-amber-500/40 text-amber-400">
                            Demo Data
                          </span>
                        )}
                      </div>
                      <p className="font-mono text-[11px] text-cyan-400/90 mt-0.5">{inv.productSku}</p>
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center space-x-1.5 text-slate-300">
                        <Warehouse className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                        <span>{inv.warehouseName}</span>
                        <span className="font-mono text-[10px] text-slate-500">({inv.warehouseCode})</span>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <span className="font-bold font-mono text-white text-sm">
                        {inv.quantityAvailable?.toLocaleString()}
                      </span>
                      <span className="text-slate-500 text-[11px] ml-1">units</span>
                    </td>
                    <td className="px-6 py-4 font-mono text-slate-400">
                      {inv.reorderLevel?.toLocaleString()} units
                    </td>
                    <td className="px-6 py-4 font-mono text-amber-400/90">
                      {inv.safetyStock?.toLocaleString()} units
                    </td>
                    <td className="px-6 py-4">
                      <span className={`px-2.5 py-1 rounded-full text-[11px] font-bold inline-flex items-center space-x-1 ${
                        inv.status === 'CRITICAL' ? 'bg-rose-950/80 text-rose-300 border border-rose-800/60 shadow-xs shadow-rose-900/30' :
                        inv.status === 'LOW_STOCK' ? 'bg-amber-950/80 text-amber-300 border border-amber-800/60' :
                        inv.status === 'OVERSTOCK' ? 'bg-purple-950/80 text-purple-300 border border-purple-800/60' :
                        'bg-emerald-950/80 text-emerald-300 border border-emerald-800/60'
                      }`}>
                        <span>{inv.status}</span>
                      </span>
                    </td>
                    <td className="px-6 py-4 text-right">
                      <button
                        onClick={() => setSelectedItem(inv)}
                        className="px-3 py-1.5 rounded-lg bg-slate-800/90 hover:bg-cyan-950/80 hover:text-cyan-300 hover:border-cyan-700/60 text-slate-300 text-xs font-semibold border border-slate-700/80 transition-all cursor-pointer inline-flex items-center space-x-1"
                      >
                        <ArrowUpDown className="w-3 h-3 text-cyan-400" />
                        <span>Adjust Qty</span>
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Adjust Modal */}
      {selectedItem && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-md flex items-center justify-center p-4 z-50 animate-fade-in">
          <div className="glass-panel p-6 rounded-2xl w-full max-w-md border border-cyan-500/30 shadow-2xl bg-slate-900/95 space-y-4">
            <div>
              <div className="flex items-center space-x-2 text-cyan-400 mb-1">
                <SlidersHorizontal className="w-4 h-4" />
                <h3 className="text-lg font-bold text-white tracking-tight">Adjust Inventory Stock</h3>
              </div>
              <p className="text-xs text-slate-400">
                {selectedItem.productName} ({selectedItem.productSku}) @ {selectedItem.warehouseName}
              </p>
            </div>

            {adjustError && (
              <div className="p-3 rounded-xl bg-rose-950/80 border border-rose-500/40 text-rose-300 text-xs flex items-center space-x-2">
                <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
                <span>{adjustError}</span>
              </div>
            )}

            <form onSubmit={handleAdjust} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                  Adjustment Quantity (+/- units)
                </label>
                <input
                  type="number"
                  value={adjustQty}
                  onChange={(e) => setAdjustQty(Number(e.target.value))}
                  placeholder="e.g. 50 or -20"
                  className="w-full px-4 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono text-sm focus:outline-none focus:border-cyan-400 focus:ring-1 focus:ring-cyan-500/30"
                  required
                  disabled={adjustSubmitting}
                />
                <p className="text-[11px] text-slate-500 mt-1">
                  Positive values add to stock, negative values deplete available units.
                </p>
              </div>

              <div className="flex justify-end space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => {
                    setSelectedItem(null);
                    setAdjustError(null);
                  }}
                  disabled={adjustSubmitting}
                  className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold border border-slate-700 disabled:opacity-50 transition-all cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={adjustSubmitting}
                  className="px-4 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/30 disabled:opacity-50 inline-flex items-center space-x-1.5 transition-all cursor-pointer"
                >
                  {adjustSubmitting && <RefreshCw className="w-3.5 h-3.5 animate-spin text-white" />}
                  <span>{adjustSubmitting ? 'Adjusting...' : 'Confirm Adjustment'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
