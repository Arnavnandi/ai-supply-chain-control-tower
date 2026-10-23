import React, { useEffect, useState } from 'react';
import axios from 'axios';
import axiosInstance from '../api/axiosInstance';
import { Search, AlertTriangle, RefreshCw } from 'lucide-react';
import { classifyApiError } from '../utils/errorClassification';

export const ProductsPage: React.FC = () => {
  const [products, setProducts] = useState<any[]>([]);
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryTrigger, setRetryTrigger] = useState(0);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(query);
    }, 300);
    return () => clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    const controller = new AbortController();
    let isCurrent = true;

    const fetchProducts = async () => {
      setLoading(true);
      setError(null);
      try {
        const trimmed = debouncedQuery.trim();
        const res = await axiosInstance.get(`/products${trimmed ? `?query=${encodeURIComponent(trimmed)}` : ''}`, {
          signal: controller.signal,
        });
        if (isCurrent) {
          setProducts(res.data);
        }
      } catch (err: any) {
        if (axios.isCancel(err) || err?.name === 'CanceledError' || controller.signal.aborted) {
          return;
        }
        console.error('Failed to load products:', err);
        if (isCurrent) {
          const classified = classifyApiError(err, 'Failed to load product catalog.');
          setError(classified.message);
        }
      } finally {
        if (isCurrent && !controller.signal.aborted) {
          setLoading(false);
        }
      }
    };

    fetchProducts();

    return () => {
      isCurrent = false;
      controller.abort();
    };
  }, [debouncedQuery, retryTrigger]);

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
              {loading ? (
                <tr>
                  <td colSpan={7} className="text-center py-12 px-4">
                    <div className="flex flex-col items-center justify-center space-y-2 text-slate-400 text-xs">
                      <RefreshCw className="w-6 h-6 animate-spin text-cyan-400" />
                      <span>Loading product catalog...</span>
                    </div>
                  </td>
                </tr>
              ) : error ? (
                <tr>
                  <td colSpan={7} className="text-center py-12 px-4">
                    <div className="max-w-md mx-auto space-y-3">
                      <div className="flex items-center justify-center space-x-2 text-amber-400">
                        <AlertTriangle className="w-5 h-5" />
                        <p className="text-sm font-semibold">Unable to Load Products</p>
                      </div>
                      <p className="text-xs text-slate-400">{error}</p>
                      <button
                        type="button"
                        onClick={() => setRetryTrigger((prev) => prev + 1)}
                        className="inline-flex items-center space-x-1.5 px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold border border-slate-700 transition-all cursor-pointer"
                      >
                        <RefreshCw className="w-3.5 h-3.5 text-cyan-400" />
                        <span>Retry</span>
                      </button>
                    </div>
                  </td>
                </tr>
              ) : products.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center py-12 px-4">
                    <div className="max-w-md mx-auto space-y-3">
                      <p className="text-sm font-semibold text-slate-300">
                        {query ? 'No products found matching your search.' : 'No operational products in catalog yet.'}
                      </p>
                      <p className="text-xs text-slate-500">
                        {query
                          ? 'Try clearing the search query or verifying the SKU.'
                          : 'Upload products.csv to establish your company SKU catalog, safety stock thresholds, and unit pricing.'}
                      </p>
                      {!query && (
                        <a
                          href="/import"
                          className="inline-flex items-center px-4 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-md transition-all"
                        >
                          Upload products.csv
                        </a>
                      )}
                    </div>
                  </td>
                </tr>
              ) : (
                products.map((p) => (
                  <tr key={p.id} className="hover:bg-slate-800/40 transition-colors">
                    <td className="px-6 py-4 font-mono text-cyan-400 font-semibold">{p.sku}</td>
                    <td className="px-6 py-4 font-medium text-white">
                      <div className="flex items-center space-x-2">
                        <span>{p.name}</span>
                        {p.isDemo && (
                          <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-950/80 border border-amber-500/40 text-amber-400">
                            Demo Data
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-6 py-4 text-slate-400">{p.categoryName}</td>
                    <td className="px-6 py-4 font-semibold text-emerald-400">${p.price?.toFixed(2)}</td>
                    <td className="px-6 py-4">{p.reorderLevel} units</td>
                    <td className="px-6 py-4 text-amber-400">{p.safetyStock} units</td>
                    <td className="px-6 py-4">{p.leadTimeDays} days</td>
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
