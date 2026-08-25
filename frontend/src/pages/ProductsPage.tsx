import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { Search } from 'lucide-react';

export const ProductsPage: React.FC = () => {
  const [products, setProducts] = useState<any[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);

  const fetchProducts = async () => {
    try {
      const res = await axiosInstance.get(`/products${query ? `?query=${query}` : ''}`);
      setProducts(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProducts();
  }, [query]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-white">Product Catalog & SKU Management</h2>
          <p className="text-xs text-slate-400">Master product database with safety thresholds and lead times</p>
        </div>

        <div className="flex items-center space-x-3">
          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-3" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search SKU or name..."
              className="pl-9 pr-4 py-2 rounded-lg bg-slate-900 border border-slate-800 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500 w-64"
            />
          </div>
        </div>
      </div>

      {/* Products Table */}
      <div className="glass-panel rounded-xl overflow-hidden border border-slate-800">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="bg-slate-900/80 text-xs font-semibold uppercase tracking-wider text-slate-400 border-b border-slate-800">
              <tr>
                <th className="px-6 py-4">SKU</th>
                <th className="px-6 py-4">Product Name</th>
                <th className="px-6 py-4">Category</th>
                <th className="px-6 py-4">Unit Price</th>
                <th className="px-6 py-4">Reorder Level</th>
                <th className="px-6 py-4">Safety Stock</th>
                <th className="px-6 py-4">Lead Time</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {products.map((p) => (
                <tr key={p.id} className="hover:bg-slate-800/40 transition-colors">
                  <td className="px-6 py-4 font-mono text-cyan-400 font-semibold">{p.sku}</td>
                  <td className="px-6 py-4 font-medium text-white">{p.name}</td>
                  <td className="px-6 py-4 text-slate-400">{p.categoryName}</td>
                  <td className="px-6 py-4 font-semibold text-emerald-400">${p.price?.toFixed(2)}</td>
                  <td className="px-6 py-4">{p.reorderLevel} units</td>
                  <td className="px-6 py-4 text-amber-400">{p.safetyStock} units</td>
                  <td className="px-6 py-4">{p.leadTimeDays} days</td>
                </tr>
              ))}
              {products.length === 0 && !loading && (
                <tr>
                  <td colSpan={7} className="text-center py-8 text-slate-500">No products found matching query.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
