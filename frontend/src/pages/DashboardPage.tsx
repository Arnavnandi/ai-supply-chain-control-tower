import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import {
  AlertTriangle,
  Package,
  DollarSign,
  Bot,
  ShieldAlert,
  ArrowUpRight
} from 'lucide-react';
import {
  AreaChart,
  Area,
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer
} from 'recharts';
import { Link } from 'react-router-dom';

export const DashboardPage: React.FC = () => {
  const [summary, setSummary] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchSummary = async () => {
      try {
        const res = await axiosInstance.get('/dashboard/summary');
        setSummary(res.data);
      } catch (err) {
        console.error('Failed to load dashboard summary', err);
      } finally {
        setLoading(false);
      }
    };
    fetchSummary();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[70vh]">
        <div className="flex flex-col items-center space-y-3">
          <div className="w-10 h-10 border-4 border-cyan-500 border-t-transparent rounded-full animate-spin" />
          <p className="text-slate-400 text-sm font-medium">Gathering Supply Chain Telemetry...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6 pb-12">
      {/* High-Priority Active Risk Alert Banner */}
      <div className="glass-panel p-4 rounded-xl border border-rose-500/30 bg-rose-950/20 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div className="flex items-center space-x-3">
          <div className="p-2.5 rounded-lg bg-rose-500/20 text-rose-400">
            <ShieldAlert className="w-6 h-6 animate-pulse" />
          </div>
          <div>
            <h4 className="text-sm font-bold text-rose-200">System Risk Alert Triggered</h4>
            <p className="text-xs text-rose-300/80">
              {summary?.lowStockProductsCount} products at critical stockout threshold • {summary?.delayedShipmentsCount} delayed cargo shipment(s)
            </p>
          </div>
        </div>
        <div className="flex items-center space-x-3">
          <Link
            to="/ai-assistant"
            className="px-3.5 py-1.5 rounded-lg bg-cyan-500/20 hover:bg-cyan-500/30 text-cyan-300 border border-cyan-500/40 text-xs font-semibold flex items-center space-x-1.5 transition-all"
          >
            <Bot className="w-3.5 h-3.5" />
            <span>Consult AI Control Center</span>
          </Link>
        </div>
      </div>

      {/* Control Tower KPI Metric Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Metric 1 */}
        <div className="glass-card p-5 rounded-xl border border-slate-800">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400 uppercase tracking-wider">Total Products SKU</span>
            <div className="p-2 rounded-lg bg-blue-500/10 text-blue-400">
              <Package className="w-4 h-4" />
            </div>
          </div>
          <p className="text-2xl font-bold text-white mt-2">{summary?.totalProducts || 0}</p>
          <p className="text-xs text-slate-400 mt-1">
            Total Inventory: <span className="text-cyan-400 font-semibold">{summary?.totalInventoryUnits?.toLocaleString()} units</span>
          </p>
        </div>

        {/* Metric 2 */}
        <div className="glass-card p-5 rounded-xl border border-slate-800">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400 uppercase tracking-wider">Inventory Asset Valuation</span>
            <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-400">
              <DollarSign className="w-4 h-4" />
            </div>
          </div>
          <p className="text-2xl font-bold text-emerald-400 mt-2">
            ${summary?.totalInventoryValue?.toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </p>
          <p className="text-xs text-slate-400 mt-1">Across 3 regional distribution hubs</p>
        </div>

        {/* Metric 3 */}
        <div className="glass-card p-5 rounded-xl border border-slate-800">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400 uppercase tracking-wider">Stockout Risk Items</span>
            <div className="p-2 rounded-lg bg-amber-500/10 text-amber-400">
              <AlertTriangle className="w-4 h-4" />
            </div>
          </div>
          <p className="text-2xl font-bold text-amber-400 mt-2">{summary?.lowStockProductsCount || 0}</p>
          <p className="text-xs text-slate-400 mt-1">Overstocked SKU count: {summary?.overstockedProductsCount}</p>
        </div>

        {/* Metric 4 */}
        <div className="glass-card p-5 rounded-xl border border-slate-800">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400 uppercase tracking-wider">Operational Risk Score</span>
            <div className="p-2 rounded-lg bg-rose-500/10 text-rose-400">
              <ShieldAlert className="w-4 h-4" />
            </div>
          </div>
          <p className="text-2xl font-bold text-rose-400 mt-2">{summary?.supplyChainRiskScore} / 100</p>
          <p className="text-xs text-slate-400 mt-1">Overall Supplier Reliability: {summary?.overallSupplierReliabilityPct}%</p>
        </div>
      </div>

      {/* Main Control Tower Analytics Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Chart 1: Inventory Stock Trends */}
        <div className="glass-panel p-5 rounded-xl border border-slate-800 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-base font-bold text-white">Multi-Warehouse Inventory Stock Trend</h3>
              <p className="text-xs text-slate-400">Available vs Reserved vs Safety Stock Buffer</p>
            </div>
            <span className="text-xs text-cyan-400 bg-cyan-950/60 border border-cyan-800/40 px-2.5 py-1 rounded-full font-medium">Live Telemetry</span>
          </div>

          <div className="h-64">
            {summary?.inventoryTrends && summary.inventoryTrends.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={summary.inventoryTrends}>
                  <defs>
                    <linearGradient id="colorAvailable" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#0284c7" stopOpacity={0.4}/>
                      <stop offset="95%" stopColor="#0284c7" stopOpacity={0}/>
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                  <XAxis dataKey="month" stroke="#64748b" />
                  <YAxis stroke="#64748b" />
                  <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', color: '#fff' }} />
                  <Area type="monotone" dataKey="available" stroke="#0284c7" fillOpacity={1} fill="url(#colorAvailable)" name="Available Stock" />
                  <Area type="monotone" dataKey="safety" stroke="#f59e0b" fill="none" strokeDasharray="4 4" name="Safety Threshold" />
                </AreaChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-full flex flex-col items-center justify-center text-slate-500 text-xs space-y-1 border border-dashed border-slate-800 rounded-lg">
                <p className="font-medium text-slate-400">No historical inventory trend data recorded</p>
                <p>Database telemetry populates as inventory levels are modified.</p>
              </div>
            )}
          </div>
        </div>

        {/* Chart 2: Statistical Demand Forecast vs Actual Orders */}
        <div className="glass-panel p-5 rounded-xl border border-slate-800 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-base font-bold text-white">Demand Forecasting vs Actual Orders</h3>
              <p className="text-xs text-slate-400">Deterministic Weighted Moving Average + Exponential Smoothing</p>
            </div>
            <span className="text-xs text-indigo-400 bg-indigo-950/60 border border-indigo-800/40 px-2.5 py-1 rounded-full font-medium">Predictive Engine</span>
          </div>

          <div className="h-64">
            {summary?.demandTrends && summary.demandTrends.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={summary.demandTrends}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                  <XAxis dataKey="month" stroke="#64748b" />
                  <YAxis stroke="#64748b" />
                  <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', color: '#fff' }} />
                  <Line type="monotone" dataKey="actualDemand" stroke="#10b981" strokeWidth={2} name="Actual Order Demand" />
                  <Line type="monotone" dataKey="forecasted" stroke="#8b5cf6" strokeWidth={2} strokeDasharray="5 5" name="Statistical Forecast" />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-full flex flex-col items-center justify-center text-slate-500 text-xs space-y-1 border border-dashed border-slate-800 rounded-lg">
                <p className="font-medium text-slate-400">No historical customer order demand data recorded</p>
                <p>Demand trend graphs populate as customer orders are placed.</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Secondary Graphs: Warehouse Utilization & Supplier Performance */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Warehouse Utilization Bar Chart */}
        <div className="glass-panel p-5 rounded-xl border border-slate-800 space-y-4">
          <h3 className="text-base font-bold text-white">Warehouse Capacity Utilization (%)</h3>
          <div className="h-60">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={summary?.warehouseUtilization || []}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                <XAxis dataKey="name" stroke="#64748b" tick={{ fontSize: 11 }} />
                <YAxis stroke="#64748b" domain={[0, 100]} />
                <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', color: '#fff' }} />
                <Bar dataKey="pct" fill="#3b82f6" radius={[4, 4, 0, 0]} name="Utilization %" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Quick Actions & AI Assistant Spotlight */}
        <div className="glass-panel p-5 rounded-xl border border-slate-800 flex flex-col justify-between">
          <div>
            <div className="flex items-center space-x-2 text-cyan-400 mb-2">
              <Bot className="w-5 h-5" />
              <h3 className="text-base font-bold text-white">Executive Control Tower AI</h3>
            </div>
            <p className="text-sm text-slate-300">
              Ask questions regarding stockouts, delayed shipments, supplier selection, or policy documents in natural language.
            </p>
            <div className="mt-4 p-3 rounded-lg bg-slate-900/80 border border-slate-800 text-xs text-slate-400 space-y-2">
              <p className="font-semibold text-slate-300">Sample Prompt Queries:</p>
              <p className="text-cyan-300 cursor-pointer hover:underline">"Which products are at high risk of stockout in 7 days?"</p>
              <p className="text-cyan-300 cursor-pointer hover:underline">"Which supplier should we choose for SKU-ELEC-001?"</p>
            </div>
          </div>

          <Link
            to="/ai-assistant"
            className="mt-5 w-full py-2.5 rounded-lg bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-white font-semibold text-sm flex items-center justify-center space-x-2 shadow-lg shadow-cyan-500/20"
          >
            <span>Open AI Assistant Workspace</span>
            <ArrowUpRight className="w-4 h-4" />
          </Link>
        </div>
      </div>
    </div>
  );
};
