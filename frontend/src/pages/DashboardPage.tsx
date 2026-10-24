import React, { useEffect, useState, useRef } from 'react';
import axiosInstance from '../api/axiosInstance';
import {
  Package,
  DollarSign,
  Bot,
  ShieldAlert,
  HelpCircle,
  TrendingUp,
  Radar,
  RefreshCw,
  UploadCloud,
  Building2,
  Boxes,
  Users,
  Truck,
  ArrowRight,
  Clock,
  AlertTriangle
} from 'lucide-react';
import {
  AreaChart,
  Area,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer
} from 'recharts';
import { Link } from 'react-router-dom';
import { ExplainabilityModal, type ExplainableRiskItem } from '../components/ExplainabilityModal';
import { classifyApiError } from '../utils/errorClassification';

interface PredictiveWarning {
  warningId: string;
  domain: string;
  predictedDisruptionType: string;
  targetEntity: string;
  anomalySeverityScore: number;
  predictiveRiskBand: string;
  failureProbability: number;
  estimatedDaysToImpact: number;
  anomalyExplanation: string;
  proactiveMitigationStrategy: string;
  recommendationId?: number;
}

interface EarlyWarningRadarReport {
  scanId: string;
  totalAnomaliesDetected: number;
  criticalWarningsCount: number;
  highestFailureProbability: number;
  earlyWarnings: PredictiveWarning[];
  proactiveProposalsGenerated: boolean;
  generatedRecommendationIds: number[];
  timestamp: string;
}

interface ExecutiveScorecardReport {
  reportId: string;
  overallResiliencyIndex?: number;
  resiliencyStatusBand: string;
  supplierOtifComponentScore?: number;
  inventoryBufferComponentScore?: number;
  warehouseCapacityComponentScore?: number;
  historicalEfficacyComponentScore?: number;
  overallPortfolioStatus: string;
  majorRiskHighlights: string[];
  recommendedExecutiveAttentionAreas: string[];
  executiveBriefingSummary: string;
  timestamp: string;
}

export const DashboardPage: React.FC = () => {
  const [summary, setSummary] = useState<any>(null);
  const [intelligence, setIntelligence] = useState<any>(null);
  const [radarReport, setRadarReport] = useState<EarlyWarningRadarReport | null>(null);
  const [executiveScorecard, setExecutiveScorecard] = useState<ExecutiveScorecardReport | null>(null);
  const [radarError, setRadarError] = useState<string | null>(null);
  const [scorecardError, setScorecardError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedExplainableItem, setSelectedExplainableItem] = useState<ExplainableRiskItem | null>(null);
  const fetchRequestIdRef = useRef(0);

  const fetchDashboardData = async (isManualRefresh = false) => {
    const requestId = ++fetchRequestIdRef.current;
    if (isManualRefresh) setRefreshing(true);
    else setLoading(true);
    setError(null);
    setRadarError(null);
    setScorecardError(null);

    try {
      const [intelRes, radarRes, execRes] = await Promise.allSettled([
        axiosInstance.get('/intelligence/summary'),
        axiosInstance.get('/public/simulation/predictive/early-warnings?convertToActionProposal=false'),
        axiosInstance.get('/public/simulation/executive/command-center')
      ]);

      if (requestId !== fetchRequestIdRef.current) return;

      // 1. Process Core Intelligence Summary (Primary operational telemetry & KPI cards)
      if (intelRes.status === 'fulfilled') {
        setSummary(intelRes.value.data?.summaryKpis || null);
        setIntelligence(intelRes.value.data);
        setError(null);
      } else {
        console.error('Failed to load control tower core summary telemetry:', intelRes.reason);
        const classified = classifyApiError(intelRes.reason, 'Failed to load control tower dashboard intelligence.');
        // If core summary fails, KPI cards cannot render, so surface primary error with retry
        setError(classified.message);
      }

      // 2. Process Predictive Early-Warning Radar
      if (radarRes.status === 'fulfilled') {
        setRadarReport(radarRes.value.data);
        setRadarError(null);
      } else {
        console.warn('Predictive early-warnings failed to load:', radarRes.reason);
        const classified = classifyApiError(radarRes.reason, 'Predictive early-warning radar simulation unavailable.');
        setRadarError(classified.message);
        setRadarReport(null);
      }

      // 3. Process Executive Resiliency Command Center Scorecard
      if (execRes.status === 'fulfilled') {
        setExecutiveScorecard(execRes.value.data);
        setScorecardError(null);
      } else {
        console.warn('Executive command center scorecard failed to load:', execRes.reason);
        const classified = classifyApiError(execRes.reason, 'Executive resiliency scorecard unavailable.');
        setScorecardError(classified.message);
        setExecutiveScorecard(null);
      }
    } catch (err: any) {
      if (requestId !== fetchRequestIdRef.current) return;
      console.error('Unexpected error loading control tower intelligence', err);
      const classified = classifyApiError(err, 'Failed to load control tower dashboard intelligence.');
      setError(classified.message);
    } finally {
      if (requestId === fetchRequestIdRef.current) {
        setLoading(false);
        if (isManualRefresh) setRefreshing(false);
      }
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, []);

  if (loading) {
    return (
      <div className="space-y-6 pb-12 animate-fadeIn">
        {/* Skeleton Top Bar */}
        <div className="glass-panel p-4 rounded-xl border border-cyan-500/10 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="w-3 h-3 rounded-full bg-cyan-500/40 animate-pulse" />
            <div className="h-4 w-48 rounded skeleton-shimmer" />
          </div>
          <div className="h-8 w-32 rounded-lg skeleton-shimmer" />
        </div>

        {/* Skeleton Scorecard */}
        <div className="glass-panel p-6 rounded-2xl border border-cyan-500/20 space-y-4">
          <div className="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-6">
            <div className="space-y-2">
              <div className="h-8 w-36 rounded skeleton-shimmer" />
              <div className="h-4 w-72 rounded skeleton-shimmer" />
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 w-full lg:w-auto">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="h-16 w-28 rounded-xl bg-slate-900/80 skeleton-shimmer" />
              ))}
            </div>
          </div>
        </div>

        {/* Skeleton 4 KPI Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="glass-card p-5 rounded-xl border border-slate-800 h-32 flex flex-col justify-between">
              <div className="flex justify-between items-center">
                <div className="h-3 w-28 rounded skeleton-shimmer" />
                <div className="w-8 h-8 rounded-lg skeleton-shimmer" />
              </div>
              <div className="h-7 w-24 rounded skeleton-shimmer" />
              <div className="h-3 w-36 rounded skeleton-shimmer" />
            </div>
          ))}
        </div>

        {/* Skeleton Radar & Charts */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="glass-panel p-6 rounded-xl border border-slate-800 h-72 skeleton-shimmer" />
          <div className="glass-panel p-6 rounded-xl border border-slate-800 h-72 skeleton-shimmer" />
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-[60vh] p-4">
        <div className="glass-panel p-8 max-w-lg w-full text-center rounded-2xl border border-amber-500/30 space-y-5 shadow-2xl shadow-black/80 bg-[#0c162b]/95">
          <div className="w-14 h-14 rounded-2xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center mx-auto text-amber-400 shadow-inner">
            <AlertTriangle className="w-7 h-7" />
          </div>
          <div className="space-y-1">
            <h3 className="text-lg font-bold text-white tracking-tight">Control Tower Telemetry Unavailable</h3>
            <p className="text-xs text-slate-300 leading-relaxed max-w-sm mx-auto">{error}</p>
          </div>
          <div className="pt-2 flex justify-center">
            <button
              type="button"
              onClick={() => fetchDashboardData(false)}
              className="inline-flex items-center space-x-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-white text-xs font-bold shadow-lg shadow-cyan-500/25 transition-all cursor-pointer ring-1 ring-white/10"
            >
              <RefreshCw className="w-4 h-4 text-white" />
              <span>Retry Connection</span>
            </button>
          </div>
        </div>
      </div>
    );
  }

  const isUnpopulated = !summary?.hasOperationalData || summary?.dataStatus === 'UNPOPULATED';
  const riskReport = intelligence?.riskReport;
  const recommendations: ExplainableRiskItem[] = intelligence?.prioritizedRecommendations || [];

  return (
    <div className="space-y-6 pb-12">
      {/* ONBOARDING STATE: DISPLAYED WHEN NO OPERATIONAL DATA EXISTS */}
      {isUnpopulated && (
        <div className="space-y-6">
          <div className="glass-panel p-8 rounded-2xl border border-cyan-500/30 bg-gradient-to-br from-cyan-950/40 via-[#0b1426] to-[#0d1830] shadow-2xl relative overflow-hidden ring-1 ring-cyan-500/20">
            <div className="max-w-3xl space-y-4">
              <div className="flex items-center space-x-2.5">
                <span className="px-2.5 py-1 rounded-md bg-cyan-500/10 border border-cyan-500/30 text-cyan-300 font-mono text-xs font-semibold uppercase tracking-wider">
                  Company Workspace Initialized
                </span>
                <span className="text-slate-500">•</span>
                <span className="text-slate-400 text-xs">Zero Fabricated Business Records</span>
              </div>

              <h2 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
                Welcome to your AI Supply Chain Control Tower
              </h2>

              <p className="text-sm text-slate-300 leading-relaxed">
                This enterprise control tower is strictly grounded in authenticated company operational records.
                Resilience scores, supplier OTIF, inventory safety buffers, disruption predictions, and AI recommendations
                are calculated directly from your database and are never fabricated.
              </p>

              <div className="flex flex-wrap items-center gap-4 pt-2">
                <Link
                  to="/import"
                  className="px-5 py-3 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-white font-bold text-xs shadow-lg shadow-cyan-500/25 flex items-center space-x-2 transition-all cursor-pointer ring-1 ring-white/10"
                >
                  <UploadCloud className="w-4 h-4" />
                  <span>Upload Your Operational Data (CSV)</span>
                  <ArrowRight className="w-4 h-4" />
                </Link>
              </div>
            </div>
          </div>

          {/* Supported CSV Pipeline Overview */}
          <div className="glass-panel p-6 rounded-2xl border border-cyan-500/15 space-y-4 bg-[#09101f]/90">
            <h3 className="text-xs font-bold text-cyan-400 uppercase tracking-widest flex items-center space-x-2">
              <span>Supported CSV Onboarding Pipeline</span>
              <span className="text-slate-600">•</span>
              <span className="text-slate-400 font-normal lowercase">5-step operational ingestion</span>
            </h3>
            <p className="text-xs text-slate-400">
              Upload your company records in the recommended sequence to activate live calculations across all operational modules:
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3 pt-1">
              <div className="p-3.5 rounded-xl bg-[#0d172e] border border-cyan-500/20 space-y-2 hover:border-cyan-500/40 transition-colors">
                <div className="flex items-center space-x-2 text-cyan-400">
                  <Package className="w-4 h-4" />
                  <span className="text-xs font-bold uppercase">1. Products</span>
                </div>
                <p className="text-[11px] text-cyan-400/90 font-mono">products.csv</p>
                <p className="text-[11px] text-slate-300">SKU catalog, unit prices, reorder thresholds &amp; safety stocks.</p>
              </div>

              <div className="p-3.5 rounded-xl bg-[#0d172e] border border-cyan-500/20 space-y-2 hover:border-cyan-500/40 transition-colors">
                <div className="flex items-center space-x-2 text-indigo-400">
                  <Building2 className="w-4 h-4" />
                  <span className="text-xs font-bold uppercase">2. Warehouses</span>
                </div>
                <p className="text-[11px] text-indigo-400/90 font-mono">warehouses.csv</p>
                <p className="text-[11px] text-slate-300">Logistics hubs, storage capacities &amp; regional locations.</p>
              </div>

              <div className="p-3.5 rounded-xl bg-[#0d172e] border border-cyan-500/20 space-y-2 hover:border-cyan-500/40 transition-colors">
                <div className="flex items-center space-x-2 text-purple-400">
                  <Users className="w-4 h-4" />
                  <span className="text-xs font-bold uppercase">3. Suppliers</span>
                </div>
                <p className="text-[11px] text-purple-400/90 font-mono">suppliers.csv</p>
                <p className="text-[11px] text-slate-300">Vendor profiles, baseline lead times &amp; contract terms.</p>
              </div>

              <div className="p-3.5 rounded-xl bg-[#0d172e] border border-cyan-500/20 space-y-2 hover:border-cyan-500/40 transition-colors">
                <div className="flex items-center space-x-2 text-emerald-400">
                  <Boxes className="w-4 h-4" />
                  <span className="text-xs font-bold uppercase">4. Inventory</span>
                </div>
                <p className="text-[11px] text-emerald-400/90 font-mono">inventories.csv</p>
                <p className="text-[11px] text-slate-300">Live quantity available, reserved quantities per facility.</p>
              </div>

              <div className="p-3.5 rounded-xl bg-[#0d172e] border border-cyan-500/20 space-y-2 hover:border-cyan-500/40 transition-colors">
                <div className="flex items-center space-x-2 text-amber-400">
                  <Truck className="w-4 h-4" />
                  <span className="text-xs font-bold uppercase">5. Shipments</span>
                </div>
                <p className="text-[11px] text-amber-400/90 font-mono">shipments.csv</p>
                <p className="text-[11px] text-slate-300">In-transit tracking, carrier performance &amp; delay logs.</p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* POPULATED DASHBOARD: HEADER BAR WITH REFRESH & LAST UPDATED */}
      {!isUnpopulated && (
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 bg-[#0c162b]/90 p-4 rounded-xl border border-cyan-500/20 shadow-lg shadow-black/40">
          <div className="flex items-center space-x-3">
            <span className="flex h-3 w-3 rounded-full bg-emerald-400 shadow-[0_0_10px_rgba(52,211,153,0.8)] animate-pulse" />
            <div>
              <div className="flex items-center space-x-2">
                <h2 className="text-sm font-bold text-white tracking-wide">Live Enterprise Control Tower</h2>
                <span className="px-2 py-0.5 rounded bg-cyan-950/80 text-[9px] font-mono font-bold text-cyan-300 border border-cyan-500/30 uppercase">
                  ACTIVE STREAM
                </span>
              </div>
              <p className="text-xs text-slate-400 flex items-center space-x-1.5 mt-0.5">
                <Clock className="w-3.5 h-3.5 text-slate-500" />
                <span>
                  Last Database Telemetry Update:{' '}
                  <span className="text-cyan-300 font-mono text-[11px]">
                    {summary?.lastUpdated ? new Date(summary.lastUpdated).toLocaleString() : 'Active Session'}
                  </span>
                </span>
              </p>
            </div>
          </div>

          <div className="flex items-center space-x-2.5">
            <button
              onClick={() => fetchDashboardData(true)}
              disabled={refreshing}
              className="px-3.5 py-2 rounded-xl bg-[#111e38] hover:bg-[#16274a] text-slate-200 text-xs font-semibold flex items-center space-x-2 transition-all border border-cyan-500/20 hover:border-cyan-500/40 disabled:opacity-50 cursor-pointer shadow-sm shadow-black"
              title="Recalculate and refresh dashboard metrics from database"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin text-cyan-400' : 'text-slate-400'}`} />
              <span>{refreshing ? 'Recalculating...' : 'Refresh Telemetry'}</span>
            </button>
            <Link
              to="/ai-assistant"
              className="px-3.5 py-2 rounded-xl bg-gradient-to-r from-blue-600 via-indigo-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white text-xs font-semibold flex items-center space-x-1.5 transition-all shadow-md shadow-indigo-600/30 ring-1 ring-white/10"
            >
              <Bot className="w-3.5 h-3.5" />
              <span>Ask AI Assistant</span>
            </Link>
          </div>
        </div>
      )}

      {/* 0. Executive Command Center Resiliency Scorecard Banner */}
      {executiveScorecard && (
        <div className="bg-gradient-to-r from-[#0e172e] via-[#0f1d38] to-[#141b33] border border-cyan-500/30 p-6 rounded-2xl shadow-2xl space-y-5 relative overflow-hidden ring-1 ring-white/5">
          <div className="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-6">
            <div className="space-y-1.5 max-w-2xl">
              <div className="flex items-center gap-3 flex-wrap">
                <span className="text-4xl font-black text-white tracking-tight font-outfit">
                  {executiveScorecard.overallResiliencyIndex != null && executiveScorecard.overallResiliencyIndex > 0
                    ? executiveScorecard.overallResiliencyIndex.toFixed(1)
                    : 'N/A'}
                </span>
                <span className="text-xs text-slate-400 font-bold uppercase tracking-widest font-mono">/ 100</span>
                <span className={`px-3 py-1 text-xs font-extrabold uppercase rounded-lg border ${
                  executiveScorecard.resiliencyStatusBand === 'OPTIMAL'
                    ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40 shadow-sm shadow-emerald-950'
                    : executiveScorecard.resiliencyStatusBand === 'HEALTHY'
                    ? 'bg-cyan-500/20 text-cyan-300 border-cyan-500/40 shadow-sm shadow-cyan-950'
                    : executiveScorecard.resiliencyStatusBand === 'WARNING'
                    ? 'bg-amber-500/20 text-amber-300 border-amber-500/40 shadow-sm shadow-amber-950'
                    : executiveScorecard.resiliencyStatusBand === 'CRITICAL'
                    ? 'bg-rose-500/20 text-rose-300 border-rose-500/40 shadow-sm shadow-rose-950'
                    : 'bg-slate-700/30 text-slate-400 border-slate-600/40'
                }`}>
                  {executiveScorecard.resiliencyStatusBand === 'INSUFFICIENT_DATA' ? 'Insufficient Data' : `${executiveScorecard.resiliencyStatusBand} Resiliency`}
                </span>
                <span className="text-[10px] font-mono text-cyan-400/80 uppercase tracking-wider">
                  REPORT: {executiveScorecard.reportId || 'SYS-ACTIVE'}
                </span>
              </div>
              <h2 className="text-base font-bold text-white tracking-tight">Executive Command Center Resiliency Scorecard</h2>
              <p className="text-xs text-slate-300 leading-relaxed">{executiveScorecard.executiveBriefingSummary}</p>
            </div>

            {/* 4 Component Micro-Progress Bars */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3.5 w-full lg:w-auto shrink-0">
              <div className="bg-[#091122]/90 p-3.5 rounded-xl border border-cyan-500/20 space-y-1.5 hover:border-cyan-500/40 transition-colors">
                <span className="text-[9px] text-slate-400 font-bold block uppercase tracking-wider">Supplier OTIF (30%)</span>
                <span className="text-xl font-black text-cyan-300 font-mono">
                  {executiveScorecard.supplierOtifComponentScore != null
                    ? `${executiveScorecard.supplierOtifComponentScore.toFixed(0)}%`
                    : 'N/A'}
                </span>
                <div className="w-full bg-slate-800/80 rounded-full h-1 overflow-hidden">
                  <div
                    className="bg-cyan-400 h-full rounded-full transition-all duration-500"
                    style={{ width: `${Math.min(100, Math.max(0, executiveScorecard.supplierOtifComponentScore || 0))}%` }}
                  />
                </div>
              </div>

              <div className="bg-[#091122]/90 p-3.5 rounded-xl border border-cyan-500/20 space-y-1.5 hover:border-cyan-500/40 transition-colors">
                <span className="text-[9px] text-slate-400 font-bold block uppercase tracking-wider">Inventory Safety (25%)</span>
                <span className="text-xl font-black text-blue-300 font-mono">
                  {executiveScorecard.inventoryBufferComponentScore != null
                    ? `${executiveScorecard.inventoryBufferComponentScore.toFixed(0)}%`
                    : 'N/A'}
                </span>
                <div className="w-full bg-slate-800/80 rounded-full h-1 overflow-hidden">
                  <div
                    className="bg-blue-400 h-full rounded-full transition-all duration-500"
                    style={{ width: `${Math.min(100, Math.max(0, executiveScorecard.inventoryBufferComponentScore || 0))}%` }}
                  />
                </div>
              </div>

              <div className="bg-[#091122]/90 p-3.5 rounded-xl border border-cyan-500/20 space-y-1.5 hover:border-cyan-500/40 transition-colors">
                <span className="text-[9px] text-slate-400 font-bold block uppercase tracking-wider">Warehouse Health (25%)</span>
                <span className="text-xl font-black text-amber-300 font-mono">
                  {executiveScorecard.warehouseCapacityComponentScore != null
                    ? `${executiveScorecard.warehouseCapacityComponentScore.toFixed(0)}%`
                    : 'N/A'}
                </span>
                <div className="w-full bg-slate-800/80 rounded-full h-1 overflow-hidden">
                  <div
                    className="bg-amber-400 h-full rounded-full transition-all duration-500"
                    style={{ width: `${Math.min(100, Math.max(0, executiveScorecard.warehouseCapacityComponentScore || 0))}%` }}
                  />
                </div>
              </div>

              <div className="bg-[#091122]/90 p-3.5 rounded-xl border border-cyan-500/20 space-y-1.5 hover:border-cyan-500/40 transition-colors">
                <span className="text-[9px] text-slate-400 font-bold block uppercase tracking-wider">Efficacy Rating (20%)</span>
                <span className="text-xl font-black text-emerald-300 font-mono">
                  {executiveScorecard.historicalEfficacyComponentScore != null
                    ? `${executiveScorecard.historicalEfficacyComponentScore.toFixed(0)}%`
                    : 'N/A'}
                </span>
                <div className="w-full bg-slate-800/80 rounded-full h-1 overflow-hidden">
                  <div
                    className="bg-emerald-400 h-full rounded-full transition-all duration-500"
                    style={{ width: `${Math.min(100, Math.max(0, executiveScorecard.historicalEfficacyComponentScore || 0))}%` }}
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
      {scorecardError && !executiveScorecard && !isUnpopulated && (
        <div className="bg-[#0c162b]/80 border border-slate-800 p-4 rounded-xl flex items-center justify-between text-xs text-slate-400">
          <div className="flex items-center space-x-2 text-amber-400">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>Executive Resiliency Scorecard: {scorecardError}</span>
          </div>
          <button
            type="button"
            onClick={() => fetchDashboardData(true)}
            className="text-cyan-400 hover:text-cyan-300 font-semibold cursor-pointer underline text-[11px]"
          >
            Retry Telemetry
          </button>
        </div>
      )}

      {/* 1. AI Executive Briefing Banner */}
      <div className="glass-panel p-5 rounded-xl border border-cyan-500/20 bg-gradient-to-r from-[#0b172e] via-[#0d1c37] to-[#121c35] flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div className="flex items-start space-x-4">
          <div className="p-3 rounded-xl bg-cyan-500/15 text-cyan-400 border border-cyan-500/30 shrink-0 shadow-inner">
            <Bot className="w-7 h-7" />
          </div>
          <div className="space-y-1">
            <div className="flex items-center space-x-2">
              <h3 className="text-base font-bold text-white tracking-tight">AI Control Tower Executive Briefing</h3>
              <span className="text-[10px] font-semibold uppercase tracking-wider bg-cyan-500/15 text-cyan-300 border border-cyan-500/30 px-2.5 py-0.5 rounded">
                Grounded Database Telemetry
              </span>
            </div>
            <p className="text-xs text-slate-300 leading-relaxed max-w-4xl">
              {intelligence?.executiveAiBriefing || 'Evaluating real-time supply chain operational metrics...'}
            </p>
          </div>
        </div>
      </div>

      {/* 1.5. Predictive Early-Warning Radar Widget */}
      {radarReport && (
        <div className="bg-[#0b1426]/90 border border-cyan-500/20 p-5 rounded-2xl shadow-xl space-y-4 relative overflow-hidden">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className={`p-2.5 rounded-xl border ${
                radarReport.earlyWarnings && radarReport.earlyWarnings.length > 0
                  ? 'bg-amber-500/15 text-amber-400 border-amber-500/30 shadow-sm shadow-amber-950'
                  : 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30 shadow-sm shadow-emerald-950'
              }`}>
                <Radar className={`w-5 h-5 ${radarReport.earlyWarnings && radarReport.earlyWarnings.length > 0 ? 'animate-pulse text-amber-400' : 'text-emerald-400'}`} />
              </div>
              <div>
                <h3 className="text-sm font-bold text-white flex items-center gap-2">
                  <span>Predictive Disruption Early-Warning Radar</span>
                  <span className={`px-2 py-0.5 text-[10px] font-extrabold uppercase rounded border ${
                    radarReport.earlyWarnings && radarReport.earlyWarnings.length > 0
                      ? 'bg-amber-500/20 text-amber-300 border-amber-500/30'
                      : 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30'
                  }`}>
                    {radarReport.earlyWarnings && radarReport.earlyWarnings.length > 0 ? 'Active Anomaly Signals' : 'Nominal Status'}
                  </span>
                </h3>
                <p className="text-xs text-slate-400">Dynamic anomaly scan evaluating inventory deficits &amp; warehouse overflow</p>
              </div>
            </div>

            <div className="flex items-center gap-3 text-xs">
              <span className="text-slate-400">Highest Failure Probability:</span>
              <span className={`px-2.5 py-1 font-bold rounded-lg border font-mono ${
                radarReport.highestFailureProbability > 0
                  ? 'bg-rose-500/20 text-rose-300 border-rose-500/40 shadow-sm shadow-rose-950'
                  : 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30'
              }`}>
                {radarReport.highestFailureProbability > 0
                  ? `${(radarReport.highestFailureProbability * 100).toFixed(0)}% Impending Disruption`
                  : '0% (Nominal)'}
              </span>
            </div>
          </div>

          {radarReport.earlyWarnings && radarReport.earlyWarnings.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {radarReport.earlyWarnings.map((warn) => (
                <div key={warn.warningId} className="bg-[#080f1e]/90 border border-cyan-500/20 p-4 rounded-xl space-y-2.5 hover:border-cyan-500/40 transition-colors">
                  <div className="flex items-center justify-between">
                    <span className="px-2 py-0.5 bg-cyan-500/10 text-cyan-300 border border-cyan-500/20 text-[10px] font-bold font-mono rounded uppercase">
                      {warn.domain} • {warn.targetEntity}
                    </span>
                    <span className="px-2 py-0.5 bg-rose-500/15 text-rose-300 border border-rose-500/30 text-[10px] font-bold rounded">
                      Impact in {warn.estimatedDaysToImpact} Days
                    </span>
                  </div>

                  <h4 className="text-xs font-bold text-white tracking-tight">{warn.predictedDisruptionType}</h4>
                  <p className="text-xs text-slate-300 leading-relaxed">{warn.anomalyExplanation}</p>

                  <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-[11px]">
                    <span className="text-emerald-400 font-semibold truncate max-w-[200px]">{warn.proactiveMitigationStrategy}</span>
                    {warn.recommendationId && (
                      <span className="text-cyan-300 font-mono font-bold bg-cyan-500/15 px-2 py-0.5 rounded border border-cyan-500/30 whitespace-nowrap">
                        Rec #{warn.recommendationId}
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="p-4 bg-[#080f1e]/50 border border-slate-800/60 rounded-xl text-center text-xs text-slate-400">
              {isUnpopulated
                ? 'No operational data recorded yet. Upload company records to activate predictive early-warning scan.'
                : 'No predictive disruption anomalies detected. Operational parameters within normal tolerances across active network.'}
            </div>
          )}
        </div>
      )}
      {radarError && !radarReport && !isUnpopulated && (
        <div className="bg-[#0c162b]/80 border border-slate-800 p-4 rounded-xl flex items-center justify-between text-xs text-slate-400">
          <div className="flex items-center space-x-2 text-amber-400">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>Predictive Early-Warning Radar: {radarError}</span>
          </div>
          <button
            type="button"
            onClick={() => fetchDashboardData(true)}
            className="text-cyan-400 hover:text-cyan-300 font-semibold cursor-pointer underline text-[11px]"
          >
            Retry Telemetry
          </button>
        </div>
      )}

      {/* 2. Control Tower Risk Matrix Radar & KPIs */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Risk Score */}
        <div className="glass-card p-5 rounded-xl border border-cyan-500/20 relative overflow-hidden flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Control Tower Risk Score</span>
              <div className="p-2 rounded-lg bg-rose-500/15 text-rose-400 border border-rose-500/20">
                <ShieldAlert className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline space-x-2 mt-2">
              <p className="text-3xl font-black text-rose-400 font-outfit">
                {summary?.supplyChainRiskScore != null ? summary.supplyChainRiskScore : 'N/A'}
              </p>
              <span className="text-xs text-slate-400 font-semibold uppercase">
                {summary?.supplyChainRiskScore != null ? `/ 100 (${riskReport?.riskLevel || 'LOW'})` : '(Awaiting Data)'}
              </span>
            </div>
          </div>
          <div className="mt-3">
            {summary?.supplyChainRiskScore == null ? (
              <span className="inline-block text-[10px] bg-[#0c162b] text-amber-300 px-2 py-0.5 rounded border border-amber-500/30">
                Insufficient data: upload inventories &amp; shipments
              </span>
            ) : (
              <div className="flex items-center gap-1.5 text-[11px]">
                <span className="bg-rose-500/20 text-rose-300 px-2 py-0.5 rounded border border-rose-500/30 font-semibold">
                  {riskReport?.criticalRisksCount || 0} Critical
                </span>
                <span className="bg-amber-500/20 text-amber-300 px-2 py-0.5 rounded border border-amber-500/30 font-semibold">
                  {riskReport?.highRisksCount || 0} High
                </span>
                <span className="bg-yellow-500/20 text-yellow-300 px-2 py-0.5 rounded border border-yellow-500/30 font-semibold">
                  {riskReport?.mediumRisksCount || 0} Med
                </span>
              </div>
            )}
          </div>
        </div>

        {/* Total SKUs */}
        <div className="glass-card p-5 rounded-xl border border-cyan-500/20 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Catalog &amp; Inventory</span>
              <div className="p-2 rounded-lg bg-cyan-500/15 text-cyan-400 border border-cyan-500/20">
                <Package className="w-4 h-4" />
              </div>
            </div>
            <p className="text-2xl font-bold text-white mt-2 font-outfit">{summary?.totalProducts || 0} Products</p>
            <p className="text-xs text-slate-400 mt-1">
              Total Available Stock: <span className="text-cyan-300 font-mono font-semibold">{summary?.totalInventoryUnits?.toLocaleString() || 0} units</span>
            </p>
          </div>
          <div className="mt-3">
            {!summary?.totalProducts ? (
              <span className="inline-block text-[10px] bg-[#0c162b] text-amber-300 px-2 py-0.5 rounded border border-amber-500/30">
                Insufficient data: upload products.csv
              </span>
            ) : summary?.totalInventoryUnits === 0 ? (
              <span className="inline-block text-[10px] bg-[#0c162b] text-amber-300 px-2 py-0.5 rounded border border-amber-500/30">
                Insufficient data: upload inventories.csv
              </span>
            ) : null}
          </div>
        </div>

        {/* Asset Valuation */}
        <div className="glass-card p-5 rounded-xl border border-cyan-500/20 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Asset Valuation</span>
              <div className="p-2 rounded-lg bg-emerald-500/15 text-emerald-400 border border-emerald-500/20">
                <DollarSign className="w-4 h-4" />
              </div>
            </div>
            <p className="text-2xl font-bold text-emerald-300 mt-2 font-outfit font-mono">
              ${summary?.totalInventoryValue?.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) || '0.00'}
            </p>
            <p className="text-xs text-slate-400 mt-1">Across {summary?.warehouseUtilization?.length || 0} regional logistics hubs</p>
          </div>
          <div className="mt-3">
            {(summary?.warehouseUtilization?.length || 0) === 0 ? (
              <span className="inline-block text-[10px] bg-[#0c162b] text-amber-300 px-2 py-0.5 rounded border border-amber-500/30">
                Insufficient data: upload warehouses.csv
              </span>
            ) : null}
          </div>
        </div>

        {/* Supplier Reliability */}
        <div className="glass-card p-5 rounded-xl border border-cyan-500/20 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Supplier Performance</span>
              <div className="p-2 rounded-lg bg-blue-500/15 text-blue-400 border border-blue-500/20">
                <TrendingUp className="w-4 h-4" />
              </div>
            </div>
            <p className="text-2xl font-bold text-blue-300 mt-2 font-outfit font-mono">
              {summary?.overallSupplierReliabilityPct != null ? `${summary.overallSupplierReliabilityPct}%` : 'N/A'}
            </p>
            <p className="text-xs text-slate-400 mt-1">
              Delayed Cargo Transit: <span className="text-rose-400 font-mono font-semibold">{summary?.delayedShipmentsCount || 0} shipments</span>
            </p>
          </div>
          <div className="mt-3">
            {summary?.overallSupplierReliabilityPct == null ? (
              <span className="inline-block text-[10px] bg-[#0c162b] text-amber-300 px-2 py-0.5 rounded border border-amber-500/30">
                Insufficient data: upload shipments.csv
              </span>
            ) : null}
          </div>
        </div>
      </div>

      {/* 3. Prioritized Action Recommendations with Explainability Trigger */}
      <div className="glass-panel p-5 rounded-xl border border-cyan-500/20 space-y-4 bg-[#091122]/90">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-base font-bold text-white flex items-center gap-2">
              <ShieldAlert className="w-5 h-5 text-amber-400" />
              <span>Prioritized AI Risk Alerts &amp; Mitigations</span>
            </h3>
            <p className="text-xs text-slate-400">Ground-truth detected anomalies with full explainability metadata</p>
          </div>
          <span className="text-xs bg-[#0f1d38] text-cyan-300 px-3 py-1 rounded-lg border border-cyan-500/30 font-mono font-semibold">
            {recommendations.length} Active Items
          </span>
        </div>

        {recommendations.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {recommendations.slice(0, 6).map((item) => (
              <div
                key={item.id}
                className="p-4 rounded-xl bg-[#0b1426] border border-cyan-500/20 hover:border-cyan-500/40 transition-all space-y-3 flex flex-col justify-between hover:shadow-lg hover:shadow-black/60"
              >
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] font-mono uppercase tracking-wider px-2 py-0.5 rounded bg-slate-900 text-slate-300 border border-slate-700">
                      {item.category}
                    </span>
                    <span className={`text-[10px] font-bold px-2.5 py-0.5 rounded-full ${
                      item.severity === 'CRITICAL' ? 'bg-rose-500/20 text-rose-300 border border-rose-500/30' :
                      item.severity === 'HIGH' ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30' :
                      'bg-yellow-500/20 text-yellow-300 border border-yellow-500/30'
                    }`}>
                      {item.severity}
                    </span>
                  </div>
                  <h4 className="text-sm font-semibold text-slate-100 line-clamp-1">{item.title}</h4>
                  <p className="text-xs text-slate-300 line-clamp-2">{item.problemDetected}</p>
                </div>

                <button
                  onClick={() => setSelectedExplainableItem(item)}
                  className="w-full mt-2 py-2 px-3 rounded-lg bg-cyan-500/15 hover:bg-cyan-500/25 text-cyan-300 border border-cyan-500/30 text-xs font-semibold flex items-center justify-center gap-1.5 transition-all cursor-pointer"
                >
                  <HelpCircle className="w-3.5 h-3.5" />
                  <span>Explain Recommendation</span>
                </button>
              </div>
            ))}
          </div>
        ) : (
          <div className="p-8 text-center text-slate-400 text-xs border border-slate-800/80 rounded-xl bg-[#070d18]/50">
            {isUnpopulated
              ? 'No active risk alerts detected for this company. Upload operational data to trigger real-time risk monitoring.'
              : 'No active risk alerts or mitigation proposals detected for this company. Operational state is nominal.'}
          </div>
        )}
      </div>

      {/* 4. Analytics Charts Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Chart 1: Inventory Stock Trends */}
        <div className="glass-panel p-5 rounded-xl border border-cyan-500/20 space-y-4 bg-[#091122]/90">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-base font-bold text-white tracking-tight">Multi-Warehouse Inventory Stock Trend</h3>
              <p className="text-xs text-slate-400">Available vs Reserved vs Safety Stock Buffer</p>
            </div>
            <span className="text-xs text-cyan-300 bg-cyan-950/80 border border-cyan-500/30 px-2.5 py-1 rounded-full font-medium font-mono text-[11px]">
              {summary?.inventoryTrends && summary.inventoryTrends.length > 0 ? 'LIVE TELEMETRY' : 'AWAITING DATA'}
            </span>
          </div>

          <div className="h-64">
            {summary?.inventoryTrends && summary.inventoryTrends.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={summary.inventoryTrends}>
                  <defs>
                    <linearGradient id="colorAvailable" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#06b6d4" stopOpacity={0.4}/>
                      <stop offset="95%" stopColor="#06b6d4" stopOpacity={0}/>
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                  <XAxis dataKey="month" stroke="#64748b" />
                  <YAxis stroke="#64748b" />
                  <Tooltip contentStyle={{ backgroundColor: '#0c162b', borderColor: '#0891b2', color: '#f8fafc', borderRadius: '0.75rem', fontSize: '0.75rem' }} />
                  <Area type="monotone" dataKey="available" stroke="#06b6d4" strokeWidth={2} fillOpacity={1} fill="url(#colorAvailable)" name="Available Stock" />
                  <Area type="monotone" dataKey="safety" stroke="#f59e0b" fill="none" strokeDasharray="4 4" strokeWidth={2} name="Safety Threshold" />
                </AreaChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-full flex items-center justify-center text-xs text-slate-500 border border-dashed border-slate-800 rounded-xl">
                No multi-warehouse inventory telemetry recorded yet.
              </div>
            )}
          </div>
        </div>

        {/* Chart 2: Statistical Demand Forecast vs Actual Orders */}
        <div className="glass-panel p-5 rounded-xl border border-cyan-500/20 space-y-4 bg-[#091122]/90">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-base font-bold text-white tracking-tight">Demand Forecasting vs Actual Orders</h3>
              <p className="text-xs text-slate-400">Deterministic Weighted Moving Average + Exponential Smoothing</p>
            </div>
            <span className="text-xs text-indigo-300 bg-indigo-950/80 border border-indigo-500/30 px-2.5 py-1 rounded-full font-medium font-mono text-[11px]">
              {summary?.demandTrends && summary.demandTrends.length > 0 ? 'PREDICTIVE ENGINE' : 'AWAITING ORDERS'}
            </span>
          </div>

          <div className="h-64">
            {summary?.demandTrends && summary.demandTrends.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={summary.demandTrends}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                  <XAxis dataKey="month" stroke="#64748b" />
                  <YAxis stroke="#64748b" />
                  <Tooltip contentStyle={{ backgroundColor: '#0c162b', borderColor: '#6366f1', color: '#f8fafc', borderRadius: '0.75rem', fontSize: '0.75rem' }} />
                  <Line type="monotone" dataKey="actualDemand" stroke="#10b981" strokeWidth={2.5} name="Actual Order Demand" />
                  <Line type="monotone" dataKey="forecasted" stroke="#8b5cf6" strokeWidth={2} strokeDasharray="5 5" name="Statistical Forecast" />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-full flex items-center justify-center text-xs text-slate-500 border border-dashed border-slate-800 rounded-xl">
                No customer order trends recorded yet to compute demand forecast.
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Explainability Modal Component */}
      <ExplainabilityModal
        item={selectedExplainableItem}
        onClose={() => setSelectedExplainableItem(null)}
      />
    </div>
  );
};
export default DashboardPage;
