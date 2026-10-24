import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import axiosInstance from '../api/axiosInstance';
import { Search, AlertTriangle, RefreshCw, Clock, Tag } from 'lucide-react';
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
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2.5">
            <h2 className="text-xl font-bold text-white tracking-tight">Product Catalog & SKU Master</h2>
            {!loading && (
              <span className="px-2.5 py-0.5 rounded-full text-xs font-mono font-semibold bg-cyan-950/70 text-cyan-400 border border-cyan-800/60">
                {products.length} SKUs
              </span>
            )}
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Centrally orchestrated SKU directory with safety buffers, reorder thresholds, and lead times
          </p>
        </div>

        <div className="flex items-center space-x-3">
          <div className="relative">
            <Search className="w-4 h-4 text-cyan-400/80 absolute left-3.5 top-3" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search SKU or product name..."
              className="pl-10 pr-9 py-2 rounded-xl bg-slate-900/90 border border-slate-700/80 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-400 focus:ring-1 focus:ring-cyan-500/30 w-72 shadow-inner transition-all"
            />
            {query && (
              <button
                type="button"
                onClick={() => setQuery('')}
                className="absolute right-3 top-2.5 text-xs text-slate-400 hover:text-white"
              >
                ✕
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Catalog Table */}
      <div className="glass-panel rounded-xl overflow-hidden border border-slate-800/80 shadow-2xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="bg-slate-900/90 text-[11px] font-semibold uppercase tracking-wider text-slate-400 border-b border-slate-800/90">
              <tr>
                <th className="px-6 py-4">SKU Code</th>
                <th className="px-6 py-4">Product Name</th>
                <th className="px-6 py-4">Category</th>
                <th className="px-6 py-4">Unit Price</th>
                <th className="px-6 py-4">Reorder Level</th>
                <th className="px-6 py-4">Safety Stock</th>
                <th className="px-6 py-4">Lead Time</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-xs">
              {loading ? (
                Array.from({ length: 5 }).map((_, idx) => (
                  <tr key={idx} className="animate-pulse">
                    <td className="px-6 py-4"><div className="h-4 w-28 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-44 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-24 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-16 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-20 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-20 bg-slate-800 rounded skeleton-shimmer" /></td>
                    <td className="px-6 py-4"><div className="h-4 w-16 bg-slate-800 rounded skeleton-shimmer" /></td>
                  </tr>
                ))
              ) : error ? (
                <tr>
                  <td colSpan={7} className="text-center py-14 px-4">
                    <div className="max-w-md mx-auto space-y-3">
                      <div className="flex items-center justify-center space-x-2 text-rose-400">
                        <AlertTriangle className="w-5 h-5" />
                        <p className="text-sm font-semibold">Unable to Load Product Catalog</p>
                      </div>
                      <p className="text-xs text-slate-400 leading-relaxed">{error}</p>
                      <button
                        type="button"
                        onClick={() => setRetryTrigger((prev) => prev + 1)}
                        className="inline-flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold border border-slate-700 transition-all cursor-pointer shadow-md"
                      >
                        <RefreshCw className="w-3.5 h-3.5 text-cyan-400" />
                        <span>Retry Catalog</span>
                      </button>
                    </div>
                  </td>
                </tr>
              ) : products.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center py-14 px-4">
                    <div className="max-w-md mx-auto space-y-3">
                      <p className="text-sm font-semibold text-slate-300">
                        {query ? 'No products found matching your search.' : 'No operational products in catalog yet.'}
                      </p>
                      <p className="text-xs text-slate-500 leading-relaxed">
                        {query
                          ? 'Try clearing the search query or verifying the SKU code.'
                          : 'Upload products.csv to establish your company SKU catalog, safety stock thresholds, and unit pricing.'}
                      </p>
                      {!query && (
                        <Link
                          to="/import"
                          className="inline-flex items-center px-4 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/30 transition-all"
                        >
                          Upload products.csv
                        </Link>
                      )}
                    </div>
                  </td>
                </tr>
              ) : (
                products.map((p) => (
                  <tr key={p.id} className="hover:bg-slate-800/40 transition-colors">
                    <td className="px-6 py-4 font-mono font-semibold text-cyan-400">
                      <span className="px-2 py-0.5 rounded bg-cyan-950/60 border border-cyan-800/50">
                        {p.sku}
                      </span>
                    </td>
                    <td className="px-6 py-4 font-medium text-white">
                      <div className="flex items-center space-x-2">
                        <span className="text-slate-100 font-semibold">{p.name}</span>
                        {p.isDemo && (
                          <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-950/80 border border-amber-500/40 text-amber-400">
                            Demo Data
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <span className="inline-flex items-center space-x-1 px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-slate-800 text-slate-300 border border-slate-700">
                        <Tag className="w-3 h-3 text-slate-400" />
                        <span>{p.categoryName || 'General'}</span>
                      </span>
                    </td>
                    <td className="px-6 py-4 font-mono font-bold text-emerald-400">
                      ${p.price?.toFixed(2)}
                    </td>
                    <td className="px-6 py-4 text-slate-300 font-mono">
                      {p.reorderLevel?.toLocaleString()} units
                    </td>
                    <td className="px-6 py-4 font-mono">
                      <span className="text-amber-400 font-semibold">{p.safetyStock?.toLocaleString()}</span>
                      <span className="text-slate-500 ml-1">units</span>
                    </td>
                    <td className="px-6 py-4">
                      <span className="inline-flex items-center space-x-1 text-slate-300 font-mono">
                        <Clock className="w-3 h-3 text-cyan-400" />
                        <span>{p.leadTimeDays}d</span>
                      </span>
                    </td>
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
