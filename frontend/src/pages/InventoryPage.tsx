import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';

export const InventoryPage: React.FC = () => {
  const [inventory, setInventory] = useState<any[]>([]);
  const [filter, setFilter] = useState<'ALL' | 'LOW_STOCK' | 'OVERSTOCK'>('ALL');
  const [selectedItem, setSelectedItem] = useState<any>(null);
  const [adjustQty, setAdjustQty] = useState(0);

  const fetchInventory = async () => {
    try {
      const url = filter === 'LOW_STOCK' ? '/inventory/low-stock' : filter === 'OVERSTOCK' ? '/inventory/overstock' : '/inventory';
      const res = await axiosInstance.get(url);
      setInventory(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    fetchInventory();
  }, [filter]);

  const handleAdjust = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedItem) return;
    try {
      await axiosInstance.post('/inventory/adjust', {
        productId: selectedItem.productId,
        warehouseId: selectedItem.warehouseId,
        adjustmentQty: Number(adjustQty)
      });
      setSelectedItem(null);
      setAdjustQty(0);
      fetchInventory();
    } catch (err) {
      alert('Adjustment failed');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-white">Multi-Warehouse Inventory Stock</h2>
          <p className="text-xs text-slate-400">Track live inventory across distribution centers</p>
        </div>

        <div className="flex items-center space-x-2 bg-slate-900 p-1 rounded-lg border border-slate-800 text-xs">
          <button
            onClick={() => setFilter('ALL')}
            className={`px-3 py-1.5 rounded-md font-semibold transition-all ${filter === 'ALL' ? 'bg-cyan-500 text-white' : 'text-slate-400 hover:text-white'}`}
          >
            All Inventory
          </button>
          <button
            onClick={() => setFilter('LOW_STOCK')}
            className={`px-3 py-1.5 rounded-md font-semibold transition-all ${filter === 'LOW_STOCK' ? 'bg-amber-500 text-white' : 'text-slate-400 hover:text-white'}`}
          >
            Low Stock Alerts
          </button>
          <button
            onClick={() => setFilter('OVERSTOCK')}
            className={`px-3 py-1.5 rounded-md font-semibold transition-all ${filter === 'OVERSTOCK' ? 'bg-purple-500 text-white' : 'text-slate-400 hover:text-white'}`}
          >
            Overstocked SKU
          </button>
        </div>
      </div>

      <div className="glass-panel rounded-xl overflow-hidden border border-slate-800">
        <table className="w-full text-left text-sm text-slate-300">
          <thead className="bg-slate-900/80 text-xs font-semibold uppercase tracking-wider text-slate-400 border-b border-slate-800">
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
          <tbody className="divide-y divide-slate-800/60">
            {inventory.map((inv) => (
              <tr key={inv.id} className="hover:bg-slate-800/40 transition-colors">
                <td className="px-6 py-4">
                  <p className="font-semibold text-white">{inv.productName}</p>
                  <p className="font-mono text-xs text-cyan-400">{inv.productSku}</p>
                </td>
                <td className="px-6 py-4">{inv.warehouseName} ({inv.warehouseCode})</td>
                <td className="px-6 py-4 font-bold text-white text-base">{inv.quantityAvailable} units</td>
                <td className="px-6 py-4 text-slate-400">{inv.reorderLevel} units</td>
                <td className="px-6 py-4 text-slate-400">{inv.safetyStock} units</td>
                <td className="px-6 py-4">
                  <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                    inv.status === 'CRITICAL' ? 'bg-rose-950/80 text-rose-400 border border-rose-800/50' :
                    inv.status === 'LOW_STOCK' ? 'bg-amber-950/80 text-amber-400 border border-amber-800/50' :
                    inv.status === 'OVERSTOCK' ? 'bg-purple-950/80 text-purple-400 border border-purple-800/50' :
                    'bg-emerald-950/80 text-emerald-400 border border-emerald-800/50'
                  }`}>
                    {inv.status}
                  </span>
                </td>
                <td className="px-6 py-4 text-right">
                  <button
                    onClick={() => setSelectedItem(inv)}
                    className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-cyan-400 text-xs font-semibold border border-slate-700"
                  >
                    Adjust Qty
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Adjust Modal */}
      {selectedItem && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="glass-panel p-6 rounded-2xl w-full max-w-md border border-slate-800">
            <h3 className="text-lg font-bold text-white mb-2">Adjust Inventory Quantity</h3>
            <p className="text-xs text-slate-400 mb-4">{selectedItem.productName} @ {selectedItem.warehouseName}</p>

            <form onSubmit={handleAdjust} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Adjustment Quantity (+/-)</label>
                <input
                  type="number"
                  value={adjustQty}
                  onChange={(e) => setAdjustQty(Number(e.target.value))}
                  placeholder="e.g. 50 or -20"
                  className="w-full px-4 py-2.5 rounded-lg bg-slate-900 border border-slate-700 text-white text-sm"
                  required
                />
              </div>

              <div className="flex justify-end space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => setSelectedItem(null)}
                  className="px-4 py-2 rounded-lg bg-slate-800 text-slate-300 text-sm font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-lg bg-cyan-500 hover:bg-cyan-400 text-white text-sm font-semibold"
                >
                  Confirm Adjustment
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
