import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import {
  BarChart3,
  Sliders,
  Zap,
  Target,
  Layers,
  FileText,
  CheckCircle2,
  X
} from 'lucide-react';

interface AccuracyMetrics {
  productId: number;
  productSku: string;
  productName: string;
  mapePercentage: number;
  rmseValue: number;
  accuracyRating: string;
  sampleSizeMonths: number;
  comparisons: {
    monthLabel: string;
    actualSales: number;
    predictedDemand: number;
    absolutePercentageError: number;
  }[];
}

interface OptimizationReport {
  totalItemsEvaluated: number;
  itemsWithDeficitCount: number;
  itemsWithExcessCount: number;
  totalCapitalOptimizationPotential: number;
  optimizedItems: {
    inventoryId: number;
    productSku: string;
    productName: string;
    warehouseName: string;
    currentStock: number;
    currentSafetyStock: number;
    calculatedDynamicSafetyStock: number;
    optimalReorderPoint: number;
    optimizationStatus: string;
    recommendedAdjustmentUnits: number;
  }[];
}

interface SimulationResult {
  baselineRiskScore: number;
  simulatedRiskScore: number;
  simulatedRiskLevel: string;
  baselineStockoutCount: number;
  simulatedStockoutCount: number;
  projectedFinancialRiskExposure: number;
  projectedStockouts: {
    productSku: string;
    productName: string;
    warehouseName: string;
    currentStock: number;
    simulatedDemand30Day: number;
    projectedDeficitUnits: number;
    timeToStockoutDays: string;
  }[];
  executiveSummary: string;
}

interface CascadeNode {
  hopLevel: number;
  domain: string;
  targetEntity: string;
  nodeRiskScore: number;
  riskBand: string;
  propagationReasoning: string;
  recommendedMitigation: string;
  recommendationId?: number;
}

interface CascadeResult {
  simulationId: string;
  primaryDisruption: string;
  primaryTarget: string;
  cumulativeRiskScore: number;
  cumulativeRiskBand: string;
  impactedDomainsCount: number;
  cascadeNodes: CascadeNode[];
  chainedActionProposalsCreated: boolean;
  generatedRecommendationIds: number[];
  timestamp: string;
}

interface CostSlaOption {
  optionId: string;
  strategyName: string;
  estimatedCostUsd: number;
  expectedLeadTimeDays: number;
  expectedRiskReduction: number;
  residualRiskScore: number;
  residualRiskBand: string;
  slaCustomerProtectionPct: number;
  roiScore: number;
  tradeoffReasoning: string;
  recommendedChoice: boolean;
}

interface CostSlaReport {
  analysisId: string;
  targetDisruptionType: string;
  targetEntity: string;
  initialRiskScore: number;
  initialRiskBand: string;
  tradeoffs: CostSlaOption[];
  optimalStrategyId: string;
  executiveRecommendationSummary: string;
}

interface CategoryEfficacyMetric {
  disruptionCategory: string;
  totalExecutedCount: number;
  historicalSuccessRatePct: number;
  averageRiskReductionDelta: number;
  topRankedActionType: string;
  efficacyRating: string;
}

interface HistoricalEfficacyReport {
  reportId: string;
  totalHistoricalExecutions: number;
  overallSuccessRatePct: number;
  overallAverageRiskReductionDelta: number;
  categoryBreakdowns: CategoryEfficacyMetric[];
  historicalInsightsSummary: string;
}

interface FailoverAllocation {
  pathType: string;
  supplierCode: string;
  supplierName: string;
  allocationPercentage: number;
  reliabilityScore: number;
  logisticsRoute: string;
  targetWarehouseCode: string;
}

interface ContainmentFailoverReport {
  containmentPlanId: string;
  failedSupplierCode: string;
  failedSupplierName: string;
  primaryWarehouseCode: string;
  containmentStatus: string;
  primaryAllocationPct: number;
  fallbackAllocationPct: number;
  allocations: FailoverAllocation[];
  alternateWarehouseCode: string;
  alternateWarehouseAvailableCapacityUnits: number;
  recommendedSafetyBufferUnits: number;
  strategyExplanation: string;
  timestamp: string;
}

export const AnalyticsPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'cascade' | 'failover' | 'costSla' | 'historicalEfficacy' | 'simulation' | 'accuracy' | 'optimization'>('cascade');

  // Accuracy State
  const [accuracyData, setAccuracyData] = useState<AccuracyMetrics | null>(null);
  const [selectedProductId, setSelectedProductId] = useState<number>(1);

  // Optimization State
  const [optimizationReport, setOptimizationReport] = useState<OptimizationReport | null>(null);

  // Simulation State
  const [demandSurge, setDemandSurge] = useState<number>(30);
  const [leadTimeDelay, setLeadTimeDelay] = useState<number>(5);
  const [simulationResult, setSimulationResult] = useState<SimulationResult | null>(null);

  // Cascade State
  const [cascadePrimaryType, setCascadePrimaryType] = useState<string>('SUPPLIER_DISRUPTION');
  const [cascadeTargetEntity, setCascadeTargetEntity] = useState<string>('SUP-TECH-001');
  const [cascadeResult, setCascadeResult] = useState<CascadeResult | null>(null);
  const [cascadeLoading, setCascadeLoading] = useState<boolean>(false);

  // Phase 23 State
  const [costSlaReport, setCostSlaReport] = useState<CostSlaReport | null>(null);
  const [historicalEfficacyReport, setHistoricalEfficacyReport] = useState<HistoricalEfficacyReport | null>(null);

  // Phase 25 State
  const [failoverReport, setFailoverReport] = useState<ContainmentFailoverReport | null>(null);

  // Executive Report Modal State
  const [executiveReport, setExecutiveReport] = useState<any | null>(null);
  const [showReportModal, setShowReportModal] = useState<boolean>(false);

  useEffect(() => {
    fetchAccuracy(selectedProductId);
    fetchOptimization();
    runSimulation(demandSurge, leadTimeDelay);
    runCascadeSimulation(cascadePrimaryType, cascadeTargetEntity);
    fetchCostSlaTradeoff();
    fetchHistoricalEfficacy();
    fetchFailoverContainment();
  }, []);

  const fetchAccuracy = async (productId: number) => {
    try {
      const res = await axiosInstance.get(`/analytics/accuracy/${productId}`);
      setAccuracyData(res.data);
    } catch (err) {
      console.error('Failed to load accuracy metrics:', err);
    }
  };

  const fetchOptimization = async () => {
    try {
      const res = await axiosInstance.get('/analytics/optimization');
      setOptimizationReport(res.data);
    } catch (err) {
      console.error('Failed to load optimization metrics:', err);
    }
  };

  const runSimulation = async (surge: number, delay: number) => {
    try {
      const res = await axiosInstance.post('/analytics/simulate', {
        demandSurgePercentage: surge,
        supplierLeadTimeDelayDays: delay,
        freightDelayPercentage: 20.0
      });
      setSimulationResult(res.data);
    } catch (err) {
      console.error('Failed to run simulation:', err);
    }
  };

  const runCascadeSimulation = async (primaryType: string, targetEntity: string) => {
    setCascadeLoading(true);
    try {
      const res = await axiosInstance.post('/public/simulation/disruption/cascade?convertToActionProposal=true', {
        primaryDisruption: primaryType,
        primaryTarget: targetEntity
      });
      setCascadeResult(res.data);
    } catch (err) {
      console.error('Failed to run cascade simulation:', err);
    } finally {
      setCascadeLoading(false);
    }
  };

  const fetchCostSlaTradeoff = async () => {
    try {
      const res = await axiosInstance.get('/public/simulation/analytics/cost-sla-tradeoff');
      setCostSlaReport(res.data);
    } catch (err) {
      console.error('Failed to load cost-SLA tradeoff report:', err);
    }
  };

  const fetchHistoricalEfficacy = async () => {
    try {
      const res = await axiosInstance.get('/public/simulation/analytics/historical-efficacy');
      setHistoricalEfficacyReport(res.data);
    } catch (err) {
      console.error('Failed to load historical efficacy report:', err);
    }
  };

  const fetchFailoverContainment = async () => {
    try {
      const res = await axiosInstance.get('/public/simulation/analytics/failover-containment');
      setFailoverReport(res.data);
    } catch (err) {
      console.error('Failed to load failover containment report:', err);
    }
  };

  const fetchExecutiveReport = async () => {
    try {
      const res = await axiosInstance.get('/analytics/executive-report');
      setExecutiveReport(res.data);
      setShowReportModal(true);
    } catch (err) {
      console.error('Failed to load executive report:', err);
    }
  };

  return (
    <div className="p-8 space-y-8 bg-slate-950 min-h-screen text-slate-100">
      {/* Page Header */}
      <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-3">
            <div className="p-3 bg-purple-500/10 text-purple-400 rounded-xl border border-purple-500/20">
              <BarChart3 className="h-6 w-6" />
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-white">Advanced Analytics & Decision Intelligence</h1>
          </div>
          <p className="text-slate-400 text-sm pl-12">
            Academic Research Suite: Cost-SLA Tradeoff Matrix, Historical Efficacy Analytics, Cascading Disruption Topology, and Dynamic Safety Stock Optimization.
          </p>
        </div>

        <button
          onClick={fetchExecutiveReport}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold bg-cyan-600 hover:bg-cyan-500 text-white shadow-lg shadow-cyan-600/20 transition-all self-start md:self-auto"
        >
          <FileText className="h-4 w-4" />
          1-Click Executive Audit Report
        </button>
      </div>

      {/* Workspace Tabs */}
      <div className="flex flex-wrap items-center gap-4 border-b border-slate-800 pb-4">
        <button
          onClick={() => setActiveTab('cascade')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold transition-all ${
            activeTab === 'cascade'
              ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/20'
              : 'text-slate-400 hover:text-white bg-slate-900 border border-slate-800'
          }`}
        >
          <Zap className="h-4 w-4 text-amber-400" />
          Cascading Disruption Correlation
        </button>

        <button
          onClick={() => setActiveTab('failover')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold transition-all ${
            activeTab === 'failover'
              ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/20'
              : 'text-slate-400 hover:text-white bg-slate-900 border border-slate-800'
          }`}
        >
          <Sliders className="h-4 w-4 text-cyan-400" />
          Multi-Supplier Failover Split Router
        </button>

        <button
          onClick={() => setActiveTab('costSla')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold transition-all ${
            activeTab === 'costSla'
              ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/20'
              : 'text-slate-400 hover:text-white bg-slate-900 border border-slate-800'
          }`}
        >
          <BarChart3 className="h-4 w-4 text-emerald-400" />
          Cost-SLA Optimization Tradeoff Matrix
        </button>

        <button
          onClick={() => setActiveTab('historicalEfficacy')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold transition-all ${
            activeTab === 'historicalEfficacy'
              ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/20'
              : 'text-slate-400 hover:text-white bg-slate-900 border border-slate-800'
          }`}
        >
          <CheckCircle2 className="h-4 w-4 text-cyan-400" />
          Historical Mitigation Efficacy
        </button>

        <button
          onClick={() => setActiveTab('simulation')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold transition-all ${
            activeTab === 'simulation'
              ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/20'
              : 'text-slate-400 hover:text-white bg-slate-900 border border-slate-800'
          }`}
        >
          <Sliders className="h-4 w-4" />
          What-If Stress Testing Simulator
        </button>

        <button
          onClick={() => setActiveTab('accuracy')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold transition-all ${
            activeTab === 'accuracy'
              ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/20'
              : 'text-slate-400 hover:text-white bg-slate-900 border border-slate-800'
          }`}
        >
          <Target className="h-4 w-4" />
          Forecast Backtesting (MAPE & RMSE)
        </button>

        <button
          onClick={() => setActiveTab('optimization')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold transition-all ${
            activeTab === 'optimization'
              ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/20'
              : 'text-slate-400 hover:text-white bg-slate-900 border border-slate-800'
          }`}
        >
          <Layers className="h-4 w-4" />
          Dynamic Safety Stock Optimization
        </button>
      </div>

      {/* Tab 0: Cascading Multi-Disruption Correlation Matrix */}
      {activeTab === 'cascade' && (
        <div className="space-y-6">
          {/* Controls Card */}
          <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl space-y-4">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div>
                <h3 className="text-lg font-bold text-white flex items-center gap-2">
                  <Zap className="h-5 w-5 text-amber-400" />
                  Cascading Disruption & Cross-Domain Impact Analysis
                </h3>
                <p className="text-xs text-slate-400 mt-1">
                  Simulate multi-hop propagation chains across Suppliers, Inventories, Logistics, and Warehouses to calculate cumulative systemic risk.
                </p>
              </div>

              <div className="flex items-center gap-3">
                <select
                  value={cascadePrimaryType}
                  onChange={(e) => setCascadePrimaryType(e.target.value)}
                  className="bg-slate-950 border border-slate-800 text-xs font-semibold text-slate-200 px-3 py-2 rounded-xl focus:outline-none focus:border-purple-500"
                >
                  <option value="SUPPLIER_DISRUPTION">Supplier Failover (SUPPLIER_DISRUPTION)</option>
                  <option value="INVENTORY_SHORTAGE">Stockout Crisis (INVENTORY_SHORTAGE)</option>
                  <option value="LOGISTICS_DELAY">Carrier Transit Bottleneck (LOGISTICS_DELAY)</option>
                  <option value="WAREHOUSE_CAPACITY_OVERRUN">Warehouse Overrun (WAREHOUSE_CAPACITY_OVERRUN)</option>
                </select>

                <input
                  type="text"
                  value={cascadeTargetEntity}
                  onChange={(e) => setCascadeTargetEntity(e.target.value)}
                  placeholder="Target Entity (e.g. SUP-TECH-001)"
                  className="bg-slate-950 border border-slate-800 text-xs text-slate-200 px-3 py-2 rounded-xl focus:outline-none focus:border-purple-500"
                />

                <button
                  onClick={() => runCascadeSimulation(cascadePrimaryType, cascadeTargetEntity)}
                  disabled={cascadeLoading}
                  className="px-4 py-2 bg-purple-600 hover:bg-purple-500 text-white text-xs font-bold rounded-xl shadow-lg shadow-purple-600/20 transition-all disabled:opacity-50 flex items-center gap-2 whitespace-nowrap"
                >
                  {cascadeLoading ? 'Analyzing...' : 'Run Cascade Analysis'}
                </button>
              </div>
            </div>
          </div>

          {/* Cascade Results */}
          {cascadeResult && (
            <div className="space-y-6">
              {/* Metric Overview Cards */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl">
                  <span className="text-xs text-slate-400 font-semibold block">Primary Originating Node</span>
                  <span className="text-base font-bold text-white mt-1 block uppercase">{cascadeResult.primaryDisruption}</span>
                  <span className="text-xs text-purple-400 font-medium">Target: {cascadeResult.primaryTarget}</span>
                </div>

                <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl">
                  <span className="text-xs text-slate-400 font-semibold block">Impacted Domains Count</span>
                  <span className="text-2xl font-black text-amber-400 mt-1 block">{cascadeResult.impactedDomainsCount} Domains</span>
                  <span className="text-xs text-slate-400">Multi-Hop Failure Chain</span>
                </div>

                <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl">
                  <span className="text-xs text-slate-400 font-semibold block">Cumulative Systemic Risk</span>
                  <div className="flex items-center gap-2 mt-1">
                    <span className="text-2xl font-black text-red-400">{cascadeResult.cumulativeRiskScore.toFixed(1)}</span>
                    <span className="px-2.5 py-0.5 bg-red-500/10 text-red-400 border border-red-500/20 text-xs font-bold rounded-md">
                      {cascadeResult.cumulativeRiskBand}
                    </span>
                  </div>
                </div>

                <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl">
                  <span className="text-xs text-slate-400 font-semibold block">Chained Policy Proposals</span>
                  <span className="text-2xl font-black text-emerald-400 mt-1 block">
                    {cascadeResult.generatedRecommendationIds.length} Created
                  </span>
                  <span className="text-xs text-slate-400">Status: PENDING_APPROVAL</span>
                </div>
              </div>

              {/* Propagation Topology Flow */}
              <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl space-y-4">
                <h4 className="text-sm font-bold text-white uppercase tracking-wider">Multi-Hop Risk Propagation Topology Flow</h4>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  {cascadeResult.cascadeNodes.map((node) => (
                    <div key={node.hopLevel} className="bg-slate-950/80 border border-slate-800 p-5 rounded-xl space-y-3 relative">
                      <div className="flex items-center justify-between">
                        <span className="px-2.5 py-1 bg-purple-500/10 text-purple-400 border border-purple-500/20 text-xs font-bold rounded-lg">
                          Hop #{node.hopLevel} • {node.domain}
                        </span>
                        <span className="px-2 py-0.5 bg-amber-500/10 text-amber-400 border border-amber-500/20 text-xs font-bold rounded">
                          Risk: {node.nodeRiskScore.toFixed(1)}
                        </span>
                      </div>

                      <h5 className="text-sm font-bold text-white">{node.targetEntity}</h5>
                      <p className="text-xs text-slate-400 leading-relaxed">{node.propagationReasoning}</p>

                      <div className="pt-2 border-t border-slate-800/80 text-xs">
                        <span className="text-slate-500 block">Recommended Containment:</span>
                        <span className="text-emerald-400 font-semibold">{node.recommendedMitigation}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Tab: Multi-Supplier Failover Split Router */}
      {activeTab === 'failover' && failoverReport && (
        <div className="space-y-6">
          <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl space-y-6">
            <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 border-b border-slate-800 pb-4">
              <div>
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <Sliders className="w-5 h-5 text-cyan-400" />
                  <span>Automated Disruption Containment & Multi-Supplier Failover Split</span>
                </h3>
                <p className="text-xs text-slate-400">Primary Failed Supplier: {failoverReport.failedSupplierCode} ({failoverReport.failedSupplierName}) | Hub: {failoverReport.primaryWarehouseCode}</p>
              </div>

              <span className="px-3 py-1 bg-cyan-500/10 text-cyan-300 border border-cyan-500/20 text-xs font-bold rounded-lg font-mono">
                {failoverReport.containmentPlanId}
              </span>
            </div>

            {/* Explanation Banner */}
            <div className="p-4 bg-cyan-500/10 border border-cyan-500/30 rounded-xl flex items-start gap-3 text-xs text-cyan-300">
              <Sliders className="w-5 h-5 text-cyan-400 shrink-0 mt-0.5" />
              <p className="leading-relaxed font-medium">{failoverReport.strategyExplanation}</p>
            </div>

            {/* 60 / 40 Split Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {failoverReport.allocations.map((alloc) => (
                <div
                  key={alloc.pathType}
                  className={`p-6 rounded-2xl border space-y-4 ${
                    alloc.pathType === 'PRIMARY_DEGRADED'
                      ? 'bg-slate-900 border-amber-500/40'
                      : 'bg-slate-900 border-cyan-500/40 shadow-xl'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className={`px-2.5 py-1 text-xs font-black uppercase rounded-lg border ${
                      alloc.pathType === 'PRIMARY_DEGRADED'
                        ? 'bg-amber-500/20 text-amber-300 border-amber-500/30'
                        : 'bg-cyan-500/20 text-cyan-300 border-cyan-500/30'
                    }`}>
                      {alloc.allocationPercentage}% Order Volume Allocation
                    </span>
                    <span className="text-xs font-mono font-bold text-slate-400">{alloc.supplierCode}</span>
                  </div>

                  <h4 className="text-base font-bold text-white">{alloc.supplierName}</h4>

                  <div className="pt-3 border-t border-slate-800 space-y-2 text-xs">
                    <div className="flex justify-between">
                      <span className="text-slate-400">Reliability Score:</span>
                      <span className="font-bold text-purple-300">{alloc.reliabilityScore}%</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-slate-400">Logistics Corridor:</span>
                      <span className="font-bold text-slate-200">{alloc.logisticsRoute}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-slate-400">Target Hub:</span>
                      <span className="font-bold text-cyan-400">{alloc.targetWarehouseCode}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {/* Hub Capacity & Safety Stock Metrics */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 space-y-1">
                <span className="text-xs text-slate-400 font-semibold block">Alternate Warehouse Available Capacity</span>
                <span className="text-xl font-bold text-emerald-400">{failoverReport.alternateWarehouseCode}: {failoverReport.alternateWarehouseAvailableCapacityUnits.toLocaleString()} units</span>
              </div>

              <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 space-y-1">
                <span className="text-xs text-slate-400 font-semibold block">Recommended Emergency Safety Stock Adjustment</span>
                <span className="text-xl font-bold text-purple-400">+{failoverReport.recommendedSafetyBufferUnits} units buffer</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Tab: Cost-SLA Optimization Tradeoff Matrix */}
      {activeTab === 'costSla' && costSlaReport && (
        <div className="space-y-6">
          <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl space-y-4">
            <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 border-b border-slate-800 pb-4">
              <div>
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <BarChart3 className="w-5 h-5 text-emerald-400" />
                  <span>Cost vs. SLA Recovery Speed Tradeoff Matrix</span>
                </h3>
                <p className="text-xs text-slate-400">Target Disruption: {costSlaReport.targetDisruptionType} | Entity: {costSlaReport.targetEntity}</p>
              </div>

              <span className="px-3 py-1 bg-indigo-500/10 text-indigo-300 border border-indigo-500/20 text-xs font-bold rounded-lg font-mono">
                {costSlaReport.analysisId}
              </span>
            </div>

            {/* Executive Recommendation Banner */}
            <div className="p-4 bg-emerald-500/10 border border-emerald-500/30 rounded-xl flex items-start gap-3 text-xs text-emerald-300">
              <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
              <p className="leading-relaxed font-medium">{costSlaReport.executiveRecommendationSummary}</p>
            </div>

            {/* Comparative Tradeoff Grid */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 pt-2">
              {costSlaReport.tradeoffs.map((opt) => (
                <div
                  key={opt.optionId}
                  className={`p-5 rounded-2xl border transition-all space-y-4 flex flex-col justify-between ${
                    opt.recommendedChoice
                      ? 'bg-slate-900 border-emerald-500/50 shadow-xl ring-1 ring-emerald-500/30'
                      : 'bg-slate-950/80 border-slate-800'
                  }`}
                >
                  <div className="space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="px-2.5 py-1 bg-purple-500/10 text-purple-400 border border-purple-500/20 text-xs font-bold rounded-lg font-mono">
                        {opt.optionId}
                      </span>
                      {opt.recommendedChoice && (
                        <span className="px-2.5 py-0.5 bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 text-[10px] font-black uppercase rounded">
                          Optimal Strategy
                        </span>
                      )}
                    </div>

                    <h4 className="text-sm font-bold text-white">{opt.strategyName}</h4>
                    <p className="text-xs text-slate-400 leading-relaxed">{opt.tradeoffReasoning}</p>
                  </div>

                  <div className="pt-4 border-t border-slate-800/80 space-y-3 text-xs">
                    <div className="flex justify-between items-center">
                      <span className="text-slate-400">Estimated Cost:</span>
                      <span className="font-bold text-white font-mono">${opt.estimatedCostUsd.toLocaleString()}</span>
                    </div>

                    <div className="flex justify-between items-center">
                      <span className="text-slate-400">Expected Lead Time:</span>
                      <span className="font-bold text-amber-400">{opt.expectedLeadTimeDays} Days</span>
                    </div>

                    <div className="flex justify-between items-center">
                      <span className="text-slate-400">Customer SLA Protection:</span>
                      <span className="font-bold text-emerald-400">{opt.slaCustomerProtectionPct}%</span>
                    </div>

                    <div className="flex justify-between items-center">
                      <span className="text-slate-400">Residual Risk Score:</span>
                      <span className="font-bold text-cyan-400">{opt.residualRiskScore} ({opt.residualRiskBand})</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Tab: Historical Mitigation Efficacy */}
      {activeTab === 'historicalEfficacy' && historicalEfficacyReport && (
        <div className="space-y-6">
          <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl space-y-6">
            <div className="flex items-center justify-between border-b border-slate-800 pb-4">
              <div>
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <CheckCircle2 className="w-5 h-5 text-cyan-400" />
                  <span>Historical Mitigation Effectiveness Analytics</span>
                </h3>
                <p className="text-xs text-slate-400">PostgreSQL Audit & Execution Data Analytics ({historicalEfficacyReport.totalHistoricalExecutions} Executed Disruption Recoveries)</p>
              </div>

              <div className="flex items-center gap-3">
                <span className="text-xs text-slate-400">Overall Success Rate:</span>
                <span className="px-3 py-1 bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 text-sm font-bold rounded-lg">
                  {historicalEfficacyReport.overallSuccessRatePct.toFixed(1)}% Efficacy
                </span>
              </div>
            </div>

            <p className="text-xs text-slate-300 bg-slate-950 p-4 rounded-xl border border-slate-800 leading-relaxed">
              {historicalEfficacyReport.historicalInsightsSummary}
            </p>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              {historicalEfficacyReport.categoryBreakdowns.map((cat) => (
                <div key={cat.disruptionCategory} className="bg-slate-950/80 border border-slate-800 p-5 rounded-2xl space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="px-2.5 py-0.5 bg-purple-500/10 text-purple-400 border border-purple-500/20 text-[10px] font-bold rounded uppercase">
                      {cat.disruptionCategory}
                    </span>
                    <span className="px-2 py-0.5 bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-[10px] font-bold rounded">
                      {cat.efficacyRating}
                    </span>
                  </div>

                  <div className="space-y-1">
                    <span className="text-2xl font-black text-white">{cat.historicalSuccessRatePct}%</span>
                    <span className="text-xs text-slate-400 block">Success Rate ({cat.totalExecutedCount} Events)</span>
                  </div>

                  <div className="pt-3 border-t border-slate-800/80 text-xs space-y-1.5">
                    <div className="flex justify-between">
                      <span className="text-slate-400">Avg Risk Delta:</span>
                      <span className="font-bold text-emerald-400">{cat.averageRiskReductionDelta}</span>
                    </div>

                    <div className="flex justify-between">
                      <span className="text-slate-400">Top Strategy:</span>
                      <span className="font-bold text-cyan-300 font-mono">{cat.topRankedActionType}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Tab 1: What-If Stress Testing Simulator */}
      {activeTab === 'simulation' && (
        <div className="space-y-6">
          {/* Controls Card */}
          <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl space-y-6">
            <h3 className="text-lg font-bold text-white flex items-center gap-2">
              <Zap className="h-5 w-5 text-amber-400" />
              Supply Chain Stress-Test Simulation Controls
            </h3>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
              {/* Slider 1: Demand Surge */}
              <div className="space-y-3">
                <div className="flex justify-between items-center text-sm">
                  <span className="font-semibold text-slate-300">Simulated Market Demand Surge</span>
                  <span className="font-bold text-amber-400 bg-amber-500/10 border border-amber-500/20 px-3 py-1 rounded-lg">
                    +{demandSurge}% Surge
                  </span>
                </div>
                <input
                  type="range"
                  min="0"
                  max="100"
                  step="5"
                  value={demandSurge}
                  onChange={e => {
                    const val = Number(e.target.value);
                    setDemandSurge(val);
                    runSimulation(val, leadTimeDelay);
                  }}
                  className="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-amber-400"
                />
                <span className="text-xs text-slate-500 block">Simulates sudden volume demand spikes across retail and customer order channels.</span>
              </div>

              {/* Slider 2: Supplier Delay */}
              <div className="space-y-3">
                <div className="flex justify-between items-center text-sm">
                  <span className="font-semibold text-slate-300">Supplier Lead-Time Delay</span>
                  <span className="font-bold text-rose-400 bg-rose-500/10 border border-rose-500/20 px-3 py-1 rounded-lg">
                    +{leadTimeDelay} Days Delay
                  </span>
                </div>
                <input
                  type="range"
                  min="0"
                  max="14"
                  step="1"
                  value={leadTimeDelay}
                  onChange={e => {
                    const val = Number(e.target.value);
                    setLeadTimeDelay(val);
                    runSimulation(demandSurge, val);
                  }}
                  className="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-rose-400"
                />
                <span className="text-xs text-slate-500 block">Simulates overseas raw material procurement delays and port congestion.</span>
              </div>
            </div>
          </div>

          {/* Simulation Output Overview */}
          {simulationResult && (
            <div className="space-y-6">
              <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
                <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl">
                  <span className="text-xs text-slate-400 uppercase font-bold block">Baseline System Risk</span>
                  <span className="text-2xl font-black text-slate-200 mt-1 block">{simulationResult.baselineRiskScore} / 100</span>
                  <span className="text-xs text-slate-500">Live PostgreSQL Telemetry</span>
                </div>

                <div className="bg-slate-900 border border-rose-500/30 p-5 rounded-2xl bg-rose-950/10">
                  <span className="text-xs text-rose-400 uppercase font-bold block">Simulated Stress Risk</span>
                  <span className="text-2xl font-black text-rose-400 mt-1 block">{simulationResult.simulatedRiskScore} / 100</span>
                  <span className="text-xs text-rose-300 font-bold uppercase">{simulationResult.simulatedRiskLevel} RISK LEVEL</span>
                </div>

                <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl">
                  <span className="text-xs text-slate-400 uppercase font-bold block">Projected Stockouts</span>
                  <span className="text-2xl font-black text-amber-400 mt-1 block">{simulationResult.simulatedStockoutCount} Items</span>
                  <span className="text-xs text-slate-500">Baseline: {simulationResult.baselineStockoutCount} items</span>
                </div>

                <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl">
                  <span className="text-xs text-slate-400 uppercase font-bold block">Financial Risk Exposure</span>
                  <span className="text-2xl font-black text-emerald-400 mt-1 block">${simulationResult.projectedFinancialRiskExposure.toLocaleString()}</span>
                  <span className="text-xs text-slate-500">Estimated Revenue Loss</span>
                </div>
              </div>

              {/* Executive Summary */}
              <div className="p-5 bg-purple-950/20 border border-purple-500/30 rounded-2xl">
                <h4 className="text-xs font-bold text-purple-400 uppercase tracking-wider mb-1">Stress-Test Executive Simulation Summary</h4>
                <p className="text-sm text-slate-200 leading-relaxed font-medium">{simulationResult.executiveSummary}</p>
              </div>

              {/* Projected Stockouts Table */}
              <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
                <div className="p-4 bg-slate-950 border-b border-slate-800 font-bold text-sm text-white">
                  Projected Stockout Incidents Under Stress Conditions
                </div>
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-950 text-slate-400 uppercase border-b border-slate-800">
                    <tr>
                      <th className="p-4">Product SKU</th>
                      <th className="p-4">Name</th>
                      <th className="p-4">Warehouse</th>
                      <th className="p-4">Current Stock</th>
                      <th className="p-4">Simulated 30D Demand</th>
                      <th className="p-4">Projected Deficit</th>
                      <th className="p-4">Time to Stockout</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800 text-slate-300">
                    {simulationResult.projectedStockouts.map((item, idx) => (
                      <tr key={idx} className="hover:bg-slate-800/40 transition-colors">
                        <td className="p-4 font-mono font-bold text-blue-400">{item.productSku}</td>
                        <td className="p-4 font-semibold text-white">{item.productName}</td>
                        <td className="p-4 text-slate-400">{item.warehouseName}</td>
                        <td className="p-4 font-bold text-slate-200">{item.currentStock} units</td>
                        <td className="p-4 font-bold text-amber-400">{item.simulatedDemand30Day} units</td>
                        <td className="p-4 font-bold text-rose-400">-{item.projectedDeficitUnits} units</td>
                        <td className="p-4 font-mono font-bold text-purple-400">{item.timeToStockoutDays}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Forecast Accuracy & Backtesting */}
      {activeTab === 'accuracy' && accuracyData && (
        <div className="space-y-6">
          <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div>
              <span className="text-xs text-slate-500 uppercase font-bold block">Selected SKU Evaluation</span>
              <h3 className="text-xl font-bold text-white mt-1">{accuracyData.productName} ({accuracyData.productSku})</h3>
            </div>

            <div className="flex items-center gap-3">
              <button
                onClick={() => { setSelectedProductId(1); fetchAccuracy(1); }}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold ${selectedProductId === 1 ? 'bg-purple-600 text-white' : 'bg-slate-800 text-slate-400'}`}
              >
                SKU-ELEC-001
              </button>
              <button
                onClick={() => { setSelectedProductId(2); fetchAccuracy(2); }}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold ${selectedProductId === 2 ? 'bg-purple-600 text-white' : 'bg-slate-800 text-slate-400'}`}
              >
                SKU-ELEC-002
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl">
              <span className="text-xs text-slate-400 uppercase font-bold block">MAPE Accuracy Index</span>
              <span className="text-3xl font-black text-emerald-400 mt-2 block">{accuracyData.mapePercentage}%</span>
              <span className="text-xs text-slate-500 mt-1 block">Mean Absolute Percentage Error</span>
            </div>

            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl">
              <span className="text-xs text-slate-400 uppercase font-bold block">RMSE Error Standard</span>
              <span className="text-3xl font-black text-blue-400 mt-2 block">{accuracyData.rmseValue}</span>
              <span className="text-xs text-slate-500 mt-1 block">Root Mean Squared Error</span>
            </div>

            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl">
              <span className="text-xs text-slate-400 uppercase font-bold block">Backtesting Model Rating</span>
              <span className="text-3xl font-black text-purple-400 mt-2 block">{accuracyData.accuracyRating}</span>
              <span className="text-xs text-slate-500 mt-1 block">Validated over 6-month historical dataset</span>
            </div>
          </div>

          {/* Comparison Table */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
            <div className="p-4 bg-slate-950 border-b border-slate-800 font-bold text-sm text-white">
              Monthly Actual vs Model Predicted Sales Backtest
            </div>
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-950 text-slate-400 uppercase border-b border-slate-800">
                <tr>
                  <th className="p-4">Month</th>
                  <th className="p-4">Actual PostgreSQL Sales</th>
                  <th className="p-4">Model Predicted Demand</th>
                  <th className="p-4">Absolute Error %</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800 text-slate-300">
                {accuracyData.comparisons.map((c, idx) => (
                  <tr key={idx} className="hover:bg-slate-800/40 transition-colors">
                    <td className="p-4 font-bold text-white">{c.monthLabel}</td>
                    <td className="p-4 font-bold text-emerald-400">{c.actualSales} units</td>
                    <td className="p-4 font-bold text-blue-400">{c.predictedDemand} units</td>
                    <td className="p-4 font-mono font-bold text-purple-400">{c.absolutePercentageError}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tab 3: Dynamic Safety Stock Optimization */}
      {activeTab === 'optimization' && optimizationReport && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl">
              <span className="text-xs text-slate-400 uppercase font-bold block">Evaluated Inventory Items</span>
              <span className="text-3xl font-black text-white mt-2 block">{optimizationReport.totalItemsEvaluated} SKUs</span>
            </div>

            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl">
              <span className="text-xs text-slate-400 uppercase font-bold block">Stock Deficit Risks</span>
              <span className="text-3xl font-black text-rose-400 mt-2 block">{optimizationReport.itemsWithDeficitCount} SKUs</span>
            </div>

            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl">
              <span className="text-xs text-slate-400 uppercase font-bold block">Capital Optimization Potential</span>
              <span className="text-3xl font-black text-emerald-400 mt-2 block">${optimizationReport.totalCapitalOptimizationPotential.toLocaleString()}</span>
            </div>
          </div>

          <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
            <div className="p-4 bg-slate-950 border-b border-slate-800 font-bold text-sm text-white">
              Dynamic Safety Stock Formula Optimization (SS = Z • σ_d • √L)
            </div>
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-950 text-slate-400 uppercase border-b border-slate-800">
                <tr>
                  <th className="p-4">SKU</th>
                  <th className="p-4">Name</th>
                  <th className="p-4">Warehouse</th>
                  <th className="p-4">Current Stock</th>
                  <th className="p-4">Current SS</th>
                  <th className="p-4">Calculated Dynamic SS</th>
                  <th className="p-4">Optimization Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800 text-slate-300">
                {optimizationReport.optimizedItems.map(item => (
                  <tr key={item.inventoryId} className="hover:bg-slate-800/40 transition-colors">
                    <td className="p-4 font-mono font-bold text-blue-400">{item.productSku}</td>
                    <td className="p-4 font-semibold text-white">{item.productName}</td>
                    <td className="p-4 text-slate-400">{item.warehouseName}</td>
                    <td className="p-4 font-bold text-slate-200">{item.currentStock}</td>
                    <td className="p-4 text-slate-400">{item.currentSafetyStock}</td>
                    <td className="p-4 font-bold text-purple-400">{item.calculatedDynamicSafetyStock}</td>
                    <td className="p-4">
                      <span className={`px-2.5 py-1 text-xs font-bold rounded-md ${
                        item.optimizationStatus === 'DEFICIT_RISK'
                          ? 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
                          : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                      }`}>
                        {item.optimizationStatus}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {/* Executive Control Report Modal */}
      {showReportModal && executiveReport && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-700 w-full max-w-3xl rounded-2xl p-6 space-y-6 shadow-2xl overflow-y-auto max-h-[90vh]">
            <div className="flex items-center justify-between border-b border-slate-800 pb-4">
              <div className="flex items-center space-x-3">
                <div className="p-2 bg-cyan-500/10 text-cyan-400 rounded-lg border border-cyan-500/20">
                  <FileText className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="text-lg font-bold text-white">{executiveReport.reportTitle}</h3>
                  <p className="text-xs text-slate-400 font-mono">Generated at: {new Date(executiveReport.generatedAt).toLocaleString()}</p>
                </div>
              </div>
              <button
                onClick={() => setShowReportModal(false)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-xs font-mono">
              <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800">
                <span className="text-slate-400 block text-[10px] uppercase">System Risk Score</span>
                <span className="text-xl font-bold text-amber-400 mt-1 block">{executiveReport.systemRiskScore} / 100</span>
                <span className="text-[10px] text-slate-500 uppercase">{executiveReport.systemRiskStatus} LEVEL</span>
              </div>

              <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800">
                <span className="text-slate-400 block text-[10px] uppercase">Supplier Avg OTIF</span>
                <span className="text-xl font-bold text-emerald-400 mt-1 block">{executiveReport.averageSupplierOtifPct}%</span>
                <span className="text-[10px] text-slate-500">On-Time In-Full</span>
              </div>

              <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800">
                <span className="text-slate-400 block text-[10px] uppercase">Delayed Shipments</span>
                <span className="text-xl font-bold text-rose-400 mt-1 block">{executiveReport.activeDelayedShipments} Active</span>
                <span className="text-[10px] text-slate-500">Logistics Delays</span>
              </div>

              <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800">
                <span className="text-slate-400 block text-[10px] uppercase">Overstock Valuation</span>
                <span className="text-xl font-bold text-cyan-400 mt-1 block">${executiveReport.totalExcessCapitalPotential?.toLocaleString()}</span>
                <span className="text-[10px] text-slate-500">Capital Potential</span>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-3 p-4 bg-slate-950 rounded-xl border border-slate-800 text-xs">
              <div className="text-center">
                <span className="text-slate-400 text-[10px] block">Pending HITL Approvals</span>
                <span className="font-bold text-amber-400 text-base">{executiveReport.pendingActionRecommendationsCount}</span>
              </div>
              <div className="text-center border-x border-slate-800">
                <span className="text-slate-400 text-[10px] block">Executed PO Actions</span>
                <span className="font-bold text-emerald-400 text-base">{executiveReport.executedActionsCount}</span>
              </div>
              <div className="text-center">
                <span className="text-slate-400 text-[10px] block">Rejected Proposals</span>
                <span className="font-bold text-rose-400 text-base">{executiveReport.rejectedActionsCount}</span>
              </div>
            </div>

            <div className="p-4 bg-cyan-950/20 border border-cyan-500/30 rounded-xl space-y-2">
              <div className="flex items-center space-x-2 text-cyan-400 font-bold text-xs uppercase tracking-wider">
                <CheckCircle2 className="h-4 w-4" />
                <span>Executive Verdict & Audit Clearance</span>
              </div>
              <p className="text-xs text-slate-200 leading-relaxed font-medium">
                {executiveReport.executiveVerdict}
              </p>
            </div>

            <div className="flex justify-end">
              <button
                onClick={() => setShowReportModal(false)}
                className="px-5 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-white font-bold text-xs transition-colors"
              >
                Close Report
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
