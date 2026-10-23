import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance';
import {
  BarChart3,
  Sliders,
  TrendingUp,
  Zap,
  Target,
  Layers,
  FileText,
  CheckCircle2,
  X,
  AlertTriangle,
  AlertCircle,
  RefreshCw,
  Sparkles
} from 'lucide-react';
import { classifyApiError } from '../utils/errorClassification';

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

interface InterHubTransferOption {
  sourceWarehouseCode: string;
  sourceWarehouseName: string;
  targetWarehouseCode: string;
  targetWarehouseName: string;
  skuCode: string;
  transferQuantityUnits: number;
  transferCostEstimate: number;
  transitLeadTimeDays: number;
  riskReductionDelta: number;
  logisticsCorridor: string;
}

interface RebalancingReport {
  rebalancePlanId: string;
  targetWarehouseCode: string;
  skuCode: string;
  deficitUnits: number;
  rebalancingStatus: string;
  transferOptions: InterHubTransferOption[];
  totalRebalancedUnits: number;
  totalCostSavingsVsNewPurchase: number;
  leadTimeReductionDays: number;
  executiveSummary: string;
  timestamp: string;
}

interface MasterOrchestrationReport {
  orchestrationPlanId: string;
  primaryTargetEntity: string;
  disruptionType: string;
  orchestrationStatus: string;
  failoverContainment: ContainmentFailoverReport;
  multiEchelonRebalance: RebalancingReport;
  costSlaOptimization: CostSlaReport;
  predictiveEarlyWarningScan: any;
  overallSystemicRiskScore: number;
  estimatedRecoveryTimeDays: number;
  totalEstimatedCost: number;
  masterExecutiveSummary: string;
  timestamp: string;
}

export type TabType =
  | 'masterOrchestrator'
  | 'cascade'
  | 'rebalance'
  | 'failover'
  | 'costSla'
  | 'historicalEfficacy'
  | 'simulation'
  | 'accuracy'
  | 'optimization';

export const AnalyticsPage: React.FC = () => {
  const [selectedCategory, setSelectedCategory] = useState<'disruption' | 'inventory' | 'resilience'>('disruption');
  const [activeTab, setActiveTab] = useState<TabType>('masterOrchestrator');

  // Tenant Entities (Loaded lazily when tabs requiring entity selectors are chosen)
  const [productList, setProductList] = useState<any[]>([]);
  const [supplierList, setSupplierList] = useState<any[]>([]);
  const [warehouseList, setWarehouseList] = useState<any[]>([]);
  const [catalogLoaded, setCatalogLoaded] = useState(false);

  // Tab Data States (Cached in state once loaded to avoid duplicate requests)
  const [masterReport, setMasterReport] = useState<MasterOrchestrationReport | null>(null);
  const [cascadeResult, setCascadeResult] = useState<CascadeResult | null>(null);
  const [rebalanceReport, setRebalanceReport] = useState<RebalancingReport | null>(null);
  const [failoverReport, setFailoverReport] = useState<ContainmentFailoverReport | null>(null);
  const [costSlaReport, setCostSlaReport] = useState<CostSlaReport | null>(null);
  const [historicalEfficacyReport, setHistoricalEfficacyReport] = useState<HistoricalEfficacyReport | null>(null);
  const [simulationResult, setSimulationResult] = useState<SimulationResult | null>(null);
  const [accuracyData, setAccuracyData] = useState<AccuracyMetrics | null>(null);
  const [optimizationReport, setOptimizationReport] = useState<OptimizationReport | null>(null);

  // Simulation / Cascade Parameters
  const [demandSurge, setDemandSurge] = useState<number>(30);
  const [leadTimeDelay, setLeadTimeDelay] = useState<number>(5);
  const [cascadePrimaryType, setCascadePrimaryType] = useState<string>('SUPPLIER_DISRUPTION');
  const [cascadeTargetEntity, setCascadeTargetEntity] = useState<string>('');
  const [selectedProductId, setSelectedProductId] = useState<number | null>(null);

  // Individual Tab Loading States
  const [loadingMaster, setLoadingMaster] = useState(false);
  const [loadingCascade, setLoadingCascade] = useState(false);
  const [generatingProposal, setGeneratingProposal] = useState(false);
  const [loadingRebalance, setLoadingRebalance] = useState(false);
  const [loadingFailover, setLoadingFailover] = useState(false);
  const [loadingCostSla, setLoadingCostSla] = useState(false);
  const [loadingHistoricalEfficacy, setLoadingHistoricalEfficacy] = useState(false);
  const [loadingSimulation, setLoadingSimulation] = useState(false);
  const [loadingAccuracy, setLoadingAccuracy] = useState(false);
  const [loadingOptimization, setLoadingOptimization] = useState(false);

  // Individual Tab Error States
  const [errorMaster, setErrorMaster] = useState<string | null>(null);
  const [errorCascade, setErrorCascade] = useState<string | null>(null);
  const [errorRebalance, setErrorRebalance] = useState<string | null>(null);
  const [errorFailover, setErrorFailover] = useState<string | null>(null);
  const [errorCostSla, setErrorCostSla] = useState<string | null>(null);
  const [errorHistoricalEfficacy, setErrorHistoricalEfficacy] = useState<string | null>(null);
  const [errorSimulation, setErrorSimulation] = useState<string | null>(null);
  const [errorAccuracy, setErrorAccuracy] = useState<string | null>(null);
  const [errorOptimization, setErrorOptimization] = useState<string | null>(null);

  // Executive Report Modal State
  const [executiveReport, setExecutiveReport] = useState<any | null>(null);
  const [showReportModal, setShowReportModal] = useState<boolean>(false);

  // Lazy Catalog Loader: Only called when a tab requiring entity selectors is selected
  const ensureCatalogLoaded = async () => {
    if (catalogLoaded) {
      return {
        products: productList,
        suppliers: supplierList,
        warehouses: warehouseList,
        initialTarget: cascadeTargetEntity,
        firstProductId: selectedProductId,
      };
    }
    try {
      const [prodRes, suppRes, whRes] = await Promise.allSettled([
        axiosInstance.get('/products'),
        axiosInstance.get('/suppliers'),
        axiosInstance.get('/warehouses'),
      ]);

      const prods = prodRes.status === 'fulfilled' ? prodRes.value.data || [] : [];
      const supps = suppRes.status === 'fulfilled' ? suppRes.value.data || [] : [];
      const whs = whRes.status === 'fulfilled' ? whRes.value.data || [] : [];

      setProductList(prods);
      setSupplierList(supps);
      setWarehouseList(whs);
      setCatalogLoaded(true);

      let initialTarget = cascadeTargetEntity;
      if (!initialTarget) {
        if (supps.length > 0) initialTarget = supps[0].supplierCode || '';
        else if (whs.length > 0) initialTarget = whs[0].code || '';
        else if (prods.length > 0) initialTarget = prods[0].sku || '';
        if (initialTarget) setCascadeTargetEntity(initialTarget);
      }

      let firstProdId = selectedProductId;
      if (!firstProdId && prods.length > 0) {
        firstProdId = prods[0].id;
        setSelectedProductId(firstProdId);
      }

      return { products: prods, suppliers: supps, warehouses: whs, initialTarget, firstProductId: firstProdId };
    } catch (e) {
      console.error('Failed to load catalog records for analytics:', e);
      return { products: [], suppliers: [], warehouses: [], initialTarget: '', firstProductId: null };
    }
  };

  const handleCascadeTypeChange = (newType: string) => {
    setCascadePrimaryType(newType);
    if (newType === 'SUPPLIER_DISRUPTION') {
      setCascadeTargetEntity(supplierList[0]?.supplierCode || '');
    } else if (newType === 'WAREHOUSE_CAPACITY_OVERRUN') {
      setCascadeTargetEntity(warehouseList[0]?.code || '');
    } else if (newType === 'INVENTORY_SHORTAGE') {
      setCascadeTargetEntity(productList[0]?.sku || '');
    } else if (newType === 'LOGISTICS_DELAY') {
      setCascadeTargetEntity(supplierList[0]?.supplierCode || warehouseList[0]?.code || '');
    }
  };

  // --- Specialized Tab Fetchers with Loading, Error, and Retry Support ---

  const fetchMasterOrchestration = async () => {
    if (loadingMaster) return;
    setLoadingMaster(true);
    setErrorMaster(null);
    try {
      const res = await axiosInstance.get('/public/simulation/analytics/unified-orchestration');
      setMasterReport(res.data);
      setErrorMaster(null);
    } catch (err: any) {
      console.error('Failed to load master orchestration blueprint:', err);
      const classified = classifyApiError(err, 'Failed to load master orchestration blueprint.');
      setErrorMaster(classified.message);
    } finally {
      setLoadingMaster(false);
    }
  };

  const runCascadeSimulation = async (
    primaryType: string,
    targetEntity: string,
    convertToProposal = false
  ) => {
    if (loadingCascade || generatingProposal) return;
    if (convertToProposal) {
      setGeneratingProposal(true);
    } else {
      setLoadingCascade(true);
    }
    setErrorCascade(null);

    try {
      // By default convertToActionProposal is false for fast non-mutating simulation
      const res = await axiosInstance.post(
        `/public/simulation/disruption/cascade?convertToActionProposal=${convertToProposal}`,
        {
          primaryDisruption: primaryType,
          primaryTarget: targetEntity,
        }
      );
      setCascadeResult(res.data);
      setErrorCascade(null);
    } catch (err: any) {
      console.error('Failed to run cascade simulation:', err);
      const classified = classifyApiError(err, 'Failed to execute cascading disruption simulation.');
      setErrorCascade(classified.message);
    } finally {
      setLoadingCascade(false);
      setGeneratingProposal(false);
    }
  };

  const fetchMultiEchelonRebalance = async () => {
    if (loadingRebalance) return;
    setLoadingRebalance(true);
    setErrorRebalance(null);
    try {
      const res = await axiosInstance.get('/public/simulation/analytics/multi-echelon-rebalance');
      setRebalanceReport(res.data);
      setErrorRebalance(null);
    } catch (err: any) {
      console.error('Failed to load multi-echelon rebalance report:', err);
      const classified = classifyApiError(err, 'Failed to load multi-echelon inventory rebalancing report.');
      setErrorRebalance(classified.message);
    } finally {
      setLoadingRebalance(false);
    }
  };

  const fetchFailoverContainment = async () => {
    if (loadingFailover) return;
    setLoadingFailover(true);
    setErrorFailover(null);
    try {
      const res = await axiosInstance.get('/public/simulation/analytics/failover-containment');
      setFailoverReport(res.data);
      setErrorFailover(null);
    } catch (err: any) {
      console.error('Failed to load failover containment report:', err);
      const classified = classifyApiError(err, 'Failed to load supplier failover containment report.');
      setErrorFailover(classified.message);
    } finally {
      setLoadingFailover(false);
    }
  };

  const fetchCostSlaTradeoff = async () => {
    if (loadingCostSla) return;
    setLoadingCostSla(true);
    setErrorCostSla(null);
    try {
      const res = await axiosInstance.get('/public/simulation/analytics/cost-sla-tradeoff');
      setCostSlaReport(res.data);
      setErrorCostSla(null);
    } catch (err: any) {
      console.error('Failed to load cost-SLA tradeoff report:', err);
      const classified = classifyApiError(err, 'Failed to load Cost vs. SLA recovery tradeoff matrix.');
      setErrorCostSla(classified.message);
    } finally {
      setLoadingCostSla(false);
    }
  };

  const fetchHistoricalEfficacy = async () => {
    if (loadingHistoricalEfficacy) return;
    setLoadingHistoricalEfficacy(true);
    setErrorHistoricalEfficacy(null);
    try {
      const res = await axiosInstance.get('/public/simulation/analytics/historical-efficacy');
      setHistoricalEfficacyReport(res.data);
      setErrorHistoricalEfficacy(null);
    } catch (err: any) {
      console.error('Failed to load historical efficacy report:', err);
      const classified = classifyApiError(err, 'Failed to load historical mitigation efficacy report.');
      setErrorHistoricalEfficacy(classified.message);
    } finally {
      setLoadingHistoricalEfficacy(false);
    }
  };

  const runSimulation = async (surge: number, delay: number) => {
    if (loadingSimulation) return;
    setLoadingSimulation(true);
    setErrorSimulation(null);
    try {
      const res = await axiosInstance.post('/analytics/simulate', {
        demandSurgePercentage: surge,
        supplierLeadTimeDelayDays: delay,
        freightDelayPercentage: 20.0,
      });
      setSimulationResult(res.data);
      setErrorSimulation(null);
    } catch (err: any) {
      console.error('Failed to run simulation:', err);
      const classified = classifyApiError(err, 'Failed to run what-if stress test simulation.');
      setErrorSimulation(classified.message);
    } finally {
      setLoadingSimulation(false);
    }
  };

  const fetchAccuracy = async (productId?: number | null) => {
    const targetId = productId || selectedProductId || productList[0]?.id;
    if (!targetId || loadingAccuracy) return;
    setLoadingAccuracy(true);
    setErrorAccuracy(null);
    try {
      const res = await axiosInstance.get(`/analytics/accuracy/${targetId}`);
      setAccuracyData(res.data);
      setErrorAccuracy(null);
    } catch (err: any) {
      console.error('Failed to load accuracy metrics:', err);
      const classified = classifyApiError(err, 'Failed to load forecast backtesting metrics.');
      setErrorAccuracy(classified.message);
      const prod = productList.find((p) => p.id === targetId);
      setAccuracyData({
        productId: targetId,
        productSku: prod?.sku || 'UNKNOWN',
        productName: prod?.name || 'Unknown SKU',
        mapePercentage: null as any,
        rmseValue: null as any,
        accuracyRating: 'INSUFFICIENT_DATA',
        sampleSizeMonths: 0,
        comparisons: [],
      });
    } finally {
      setLoadingAccuracy(false);
    }
  };

  const fetchOptimization = async () => {
    if (loadingOptimization) return;
    setLoadingOptimization(true);
    setErrorOptimization(null);
    try {
      const res = await axiosInstance.get('/analytics/optimization');
      setOptimizationReport(res.data);
      setErrorOptimization(null);
    } catch (err: any) {
      console.error('Failed to load optimization metrics:', err);
      const classified = classifyApiError(err, 'Failed to load safety stock optimization report.');
      setErrorOptimization(classified.message);
    } finally {
      setLoadingOptimization(false);
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

  // --- Lazy Tab Router / Dispatcher ---
  // Only fetches the data needed for the active tab, caching results in state to prevent duplicate requests.
  const loadTabData = async (tab: TabType, forceRefresh = false) => {
    switch (tab) {
      case 'masterOrchestrator':
        if (!masterReport || forceRefresh) {
          await fetchMasterOrchestration();
        }
        break;
      case 'cascade':
        if (!cascadeResult || forceRefresh) {
          const catalog = await ensureCatalogLoaded();
          const target = cascadeTargetEntity || catalog.initialTarget;
          if (target) {
            await runCascadeSimulation(cascadePrimaryType, target, false);
          }
        }
        break;
      case 'rebalance':
        if (!rebalanceReport || forceRefresh) {
          await fetchMultiEchelonRebalance();
        }
        break;
      case 'failover':
        if (!failoverReport || forceRefresh) {
          await fetchFailoverContainment();
        }
        break;
      case 'costSla':
        if (!costSlaReport || forceRefresh) {
          await fetchCostSlaTradeoff();
        }
        break;
      case 'historicalEfficacy':
        if (!historicalEfficacyReport || forceRefresh) {
          await fetchHistoricalEfficacy();
        }
        break;
      case 'simulation':
        if (!simulationResult || forceRefresh) {
          await runSimulation(demandSurge, leadTimeDelay);
        }
        break;
      case 'accuracy':
        if (!accuracyData || forceRefresh) {
          const catalog = await ensureCatalogLoaded();
          const prodId = selectedProductId || catalog.firstProductId;
          if (prodId) {
            await fetchAccuracy(prodId);
          }
        }
        break;
      case 'optimization':
        if (!optimizationReport || forceRefresh) {
          await fetchOptimization();
        }
        break;
    }
  };

  const selectTab = (tab: TabType, forceRefresh = false) => {
    setActiveTab(tab);
    loadTabData(tab, forceRefresh);
  };

  const selectCategory = (category: 'disruption' | 'inventory' | 'resilience') => {
    setSelectedCategory(category);
    if (category === 'disruption' && !['masterOrchestrator', 'cascade', 'simulation'].includes(activeTab)) {
      selectTab('masterOrchestrator');
    } else if (category === 'inventory' && !['accuracy', 'optimization', 'rebalance'].includes(activeTab)) {
      selectTab('accuracy');
    } else if (category === 'resilience' && !['failover', 'costSla', 'historicalEfficacy'].includes(activeTab)) {
      selectTab('failover');
    }
  };

  // On mount: ONLY fetch the initial active tab ('masterOrchestrator')
  useEffect(() => {
    loadTabData('masterOrchestrator');
  }, []);

  // UI Helper: Standard Tab Loading Spinner
  const renderTabLoading = (message: string) => (
    <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800">
      <div className="flex flex-col items-center justify-center space-y-3 text-slate-400 text-xs">
        <RefreshCw className="w-6 h-6 animate-spin text-cyan-400" />
        <span>{message}</span>
      </div>
    </div>
  );

  // UI Helper: Standard Tab Error Panel with Working Retry Action
  const renderTabError = (title: string, errorMsg: string, onRetry: () => void) => (
    <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4">
      <div className="max-w-md mx-auto space-y-3">
        <div className="flex items-center justify-center space-x-2 text-amber-400">
          <AlertTriangle className="w-5 h-5" />
          <h3 className="text-sm font-semibold">{title}</h3>
        </div>
        <p className="text-xs text-slate-400 leading-relaxed">{errorMsg}</p>
        <button
          type="button"
          onClick={onRetry}
          className="inline-flex items-center space-x-1.5 px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-white text-xs font-semibold border border-slate-700 transition-all cursor-pointer"
        >
          <RefreshCw className="w-3.5 h-3.5 text-cyan-400" />
          <span>Retry Analysis</span>
        </button>
      </div>
    </div>
  );


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

      {/* 3 Primary Analytics Domain Category Selectors */}
      <div className="flex flex-wrap items-center gap-3 bg-slate-900/60 p-2 rounded-2xl border border-slate-800">
        <button
          onClick={() => selectCategory('disruption')}
          className={`flex items-center gap-2.5 px-5 py-2.5 rounded-xl text-xs font-bold transition-all ${
            selectedCategory === 'disruption'
              ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/30'
              : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
          }`}
        >
          <Zap className="h-4 w-4 text-amber-400" />
          <span>Disruption & Risk</span>
        </button>

        <button
          onClick={() => selectCategory('inventory')}
          className={`flex items-center gap-2.5 px-5 py-2.5 rounded-xl text-xs font-bold transition-all ${
            selectedCategory === 'inventory'
              ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/30'
              : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
          }`}
        >
          <TrendingUp className="h-4 w-4 text-emerald-400" />
          <span>Inventory & Forecasting</span>
        </button>

        <button
          onClick={() => selectCategory('resilience')}
          className={`flex items-center gap-2.5 px-5 py-2.5 rounded-xl text-xs font-bold transition-all ${
            selectedCategory === 'resilience'
              ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/30'
              : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
          }`}
        >
          <CheckCircle2 className="h-4 w-4 text-cyan-400" />
          <span>Resilience & Efficacy</span>
        </button>
      </div>

      {/* Sub-tabs under selected category */}
      <div className="flex flex-wrap items-center gap-3 border-b border-slate-800 pb-4">
        {selectedCategory === 'disruption' && (
          <>
            <button
              onClick={() => selectTab('masterOrchestrator')}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === 'masterOrchestrator'
                  ? 'bg-slate-800 text-cyan-300 border border-cyan-500/30 shadow-md'
                  : 'text-slate-400 hover:text-white bg-slate-900/60 border border-slate-800'
              }`}
            >
              <Layers className="h-3.5 w-3.5 text-cyan-400" />
              Master Disruption Containment & Recovery Blueprint
            </button>
            <button
              onClick={() => selectTab('cascade')}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === 'cascade'
                  ? 'bg-slate-800 text-amber-300 border border-amber-500/30 shadow-md'
                  : 'text-slate-400 hover:text-white bg-slate-900/60 border border-slate-800'
              }`}
            >
              <Zap className="h-3.5 w-3.5 text-amber-400" />
              Cascading Disruption Correlation
            </button>
            <button
              onClick={() => selectTab('simulation')}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === 'simulation'
                  ? 'bg-slate-800 text-purple-300 border border-purple-500/30 shadow-md'
                  : 'text-slate-400 hover:text-white bg-slate-900/60 border border-slate-800'
              }`}
            >
              <Sliders className="h-3.5 w-3.5 text-purple-400" />
              Deterministic What-If Simulator
            </button>
          </>
        )}

        {selectedCategory === 'inventory' && (
          <>
            <button
              onClick={() => selectTab('accuracy')}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === 'accuracy'
                  ? 'bg-slate-800 text-cyan-300 border border-cyan-500/30 shadow-md'
                  : 'text-slate-400 hover:text-white bg-slate-900/60 border border-slate-800'
              }`}
            >
              <Target className="h-3.5 w-3.5 text-cyan-400" />
              Forecast Backtesting (MAPE & RMSE)
            </button>
            <button
              onClick={() => selectTab('optimization')}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === 'optimization'
                  ? 'bg-slate-800 text-emerald-300 border border-emerald-500/30 shadow-md'
                  : 'text-slate-400 hover:text-white bg-slate-900/60 border border-slate-800'
              }`}
            >
              <Layers className="h-3.5 w-3.5 text-emerald-400" />
              Dynamic Safety Stock Optimization
            </button>
            <button
              onClick={() => selectTab('rebalance')}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === 'rebalance'
                  ? 'bg-slate-800 text-purple-300 border border-purple-500/30 shadow-md'
                  : 'text-slate-400 hover:text-white bg-slate-900/60 border border-slate-800'
              }`}
            >
              <TrendingUp className="h-3.5 w-3.5 text-purple-400" />
              Multi-Echelon Inventory Rebalancing
            </button>
          </>
        )}

        {selectedCategory === 'resilience' && (
          <>
            <button
              onClick={() => selectTab('failover')}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === 'failover'
                  ? 'bg-slate-800 text-cyan-300 border border-cyan-500/30 shadow-md'
                  : 'text-slate-400 hover:text-white bg-slate-900/60 border border-slate-800'
              }`}
            >
              <Sliders className="h-3.5 w-3.5 text-cyan-400" />
              Multi-Supplier Failover Split Router
            </button>
            <button
              onClick={() => selectTab('costSla')}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === 'costSla'
                  ? 'bg-slate-800 text-emerald-300 border border-emerald-500/30 shadow-md'
                  : 'text-slate-400 hover:text-white bg-slate-900/60 border border-slate-800'
              }`}
            >
              <BarChart3 className="h-3.5 w-3.5 text-emerald-400" />
              Cost-SLA Optimization Tradeoff Matrix
            </button>
            <button
              onClick={() => selectTab('historicalEfficacy')}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
                activeTab === 'historicalEfficacy'
                  ? 'bg-slate-800 text-purple-300 border border-purple-500/30 shadow-md'
                  : 'text-slate-400 hover:text-white bg-slate-900/60 border border-slate-800'
              }`}
            >
              <CheckCircle2 className="h-3.5 w-3.5 text-purple-400" />
              Historical Mitigation Efficacy
            </button>
          </>
        )}
      </div>


      {/* Tab: Master Disruption Containment & Recovery Blueprint */}
      {activeTab === 'masterOrchestrator' && (
        loadingMaster ? (
          renderTabLoading('Synthesizing Master Disruption Containment & Recovery Blueprint...')
        ) : errorMaster ? (
          renderTabError('Unable to Generate Master Blueprint', errorMaster, () => fetchMasterOrchestration())
        ) : masterReport && masterReport.orchestrationStatus !== 'INSUFFICIENT_DATA' ? (
          <div className="space-y-6">
            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl space-y-6">
              <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 border-b border-slate-800 pb-4">
                <div>
                  <h3 className="text-base font-bold text-white flex items-center gap-2">
                    <Layers className="w-5 h-5 text-cyan-400" />
                    <span>Master Disruption Containment & Recovery Blueprint</span>
                  </h3>
                  <p className="text-xs text-slate-400">Target Disruption: {masterReport.disruptionType} on {masterReport.primaryTargetEntity} | Status: {masterReport.orchestrationStatus}</p>
                </div>

                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => fetchMasterOrchestration()}
                    disabled={loadingMaster}
                    className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-950 hover:bg-slate-800 text-slate-300 text-xs font-semibold border border-slate-800 transition-all cursor-pointer disabled:opacity-50"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 text-cyan-400 ${loadingMaster ? 'animate-spin' : ''}`} />
                    <span>Refresh</span>
                  </button>
                  <span className="px-3 py-1 bg-cyan-500/10 text-cyan-300 border border-cyan-500/20 text-xs font-bold rounded-lg font-mono">
                    {masterReport.orchestrationPlanId}
                  </span>
                </div>
              </div>

              {/* Master Executive Summary Banner */}
              <div className="p-4 bg-cyan-500/10 border border-cyan-500/30 rounded-xl flex items-start gap-3 text-xs text-cyan-300">
                <Layers className="w-5 h-5 text-cyan-400 shrink-0 mt-0.5" />
                <p className="leading-relaxed font-medium">{masterReport.masterExecutiveSummary}</p>
              </div>

              {/* Executive Metric Highlights */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 space-y-1">
                  <span className="text-xs text-slate-400 font-semibold block">Overall Systemic Risk Score</span>
                  <div className="flex items-center gap-2">
                    <span className="text-2xl font-black text-amber-400">{masterReport.overallSystemicRiskScore.toFixed(1)}</span>
                    <span className="px-2 py-0.5 bg-amber-500/10 text-amber-400 text-xs font-bold rounded border border-amber-500/20">MODERATE</span>
                  </div>
                </div>

                <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 space-y-1">
                  <span className="text-xs text-slate-400 font-semibold block">Estimated Recovery Time</span>
                  <span className="text-2xl font-black text-emerald-400">{masterReport.estimatedRecoveryTimeDays} Days</span>
                </div>

                <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 space-y-1">
                  <span className="text-xs text-slate-400 font-semibold block">Total Estimated Recovery Cost</span>
                  <span className="text-2xl font-black text-cyan-400">${masterReport.totalEstimatedCost.toLocaleString()}</span>
                </div>
              </div>

              {/* Combined Strategy Cards Grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Failover Routing Component */}
                <div className="p-5 rounded-2xl bg-slate-950 border border-slate-800 space-y-3">
                  <span className="text-xs font-bold text-cyan-400 uppercase tracking-wider block">1. Multi-Supplier Failover Allocation</span>
                  <p className="text-xs text-slate-300">
                    Primary Path: 60% allocation on {masterReport.failoverContainment?.failedSupplierCode || 'N/A'} | Fallback Path: 40% re-allocated to secondary vendor routed to {masterReport.failoverContainment?.alternateWarehouseCode || 'N/A'}.
                  </p>
                  <div className="pt-2 border-t border-slate-800 text-xs flex justify-between text-slate-400">
                    <span>Containment Status: <strong className="text-emerald-400">{masterReport.failoverContainment?.containmentStatus || 'PENDING'}</strong></span>
                    <span>Safety Buffer: <strong className="text-purple-300">+{masterReport.failoverContainment?.recommendedSafetyBufferUnits ?? 0} units</strong></span>
                  </div>
                </div>

                {/* Multi-Echelon Stock Transfer Component */}
                <div className="p-5 rounded-2xl bg-slate-950 border border-slate-800 space-y-3">
                  <span className="text-xs font-bold text-emerald-400 uppercase tracking-wider block">2. Multi-Echelon Stock Redistribution</span>
                  <p className="text-xs text-slate-300">
                    Transfers {masterReport.multiEchelonRebalance?.totalRebalancedUnits ?? 0} units of stock from surplus regional hubs, saving ${masterReport.multiEchelonRebalance?.totalCostSavingsVsNewPurchase?.toLocaleString() || '0'} vs new purchase.
                  </p>
                  <div className="pt-2 border-t border-slate-800 text-xs flex justify-between text-slate-400">
                    <span>Rebalance Status: <strong className="text-emerald-400">{masterReport.multiEchelonRebalance?.rebalancingStatus || 'PENDING'}</strong></span>
                    <span>Lead Time Saved: <strong className="text-purple-300">-{masterReport.multiEchelonRebalance?.leadTimeReductionDays ?? 0} Days</strong></span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4">
            <div className="max-w-md mx-auto space-y-3">
              <AlertCircle className="h-8 w-8 text-cyan-400 mx-auto" />
              <h3 className="text-base font-bold text-white">Insufficient Data for Master Orchestration Blueprint</h3>
              <p className="text-xs text-slate-400 leading-relaxed">
                The Master Orchestration Blueprint requires active suppliers, inventory items, and warehouses in your company catalog to model multi-domain failover and rebalancing.
              </p>
              <Link
                to="/import"
                className="inline-flex items-center px-4 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/20 transition-all"
              >
                Upload Ingestion Data
              </Link>
            </div>
          </div>
        )
      )}

      {/* Tab: Cascading Multi-Disruption Correlation Matrix */}
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

              <div className="flex items-center gap-3 flex-wrap">
                <select
                  value={cascadePrimaryType}
                  onChange={(e) => handleCascadeTypeChange(e.target.value)}
                  className="bg-slate-950 border border-slate-800 text-xs font-semibold text-slate-200 px-3 py-2 rounded-xl focus:outline-none focus:border-purple-500"
                >
                  <option value="SUPPLIER_DISRUPTION">Supplier Failover (SUPPLIER_DISRUPTION)</option>
                  <option value="INVENTORY_SHORTAGE">Stockout Crisis (INVENTORY_SHORTAGE)</option>
                  <option value="LOGISTICS_DELAY">Carrier Transit Bottleneck (LOGISTICS_DELAY)</option>
                  <option value="WAREHOUSE_CAPACITY_OVERRUN">Warehouse Overrun (WAREHOUSE_CAPACITY_OVERRUN)</option>
                </select>

                {cascadePrimaryType === 'SUPPLIER_DISRUPTION' ? (
                  supplierList.length > 0 ? (
                    <select
                      value={cascadeTargetEntity}
                      onChange={(e) => setCascadeTargetEntity(e.target.value)}
                      className="bg-slate-950 border border-slate-800 text-xs text-slate-200 px-3 py-2 rounded-xl focus:outline-none focus:border-purple-500"
                    >
                      {supplierList.map((s) => (
                        <option key={s.id} value={s.supplierCode}>{s.name} ({s.supplierCode})</option>
                      ))}
                    </select>
                  ) : (
                    <span className="text-xs text-amber-400 bg-amber-500/10 border border-amber-500/20 px-3 py-2 rounded-xl">
                      No suppliers registered
                    </span>
                  )
                ) : cascadePrimaryType === 'WAREHOUSE_CAPACITY_OVERRUN' ? (
                  warehouseList.length > 0 ? (
                    <select
                      value={cascadeTargetEntity}
                      onChange={(e) => setCascadeTargetEntity(e.target.value)}
                      className="bg-slate-950 border border-slate-800 text-xs text-slate-200 px-3 py-2 rounded-xl focus:outline-none focus:border-purple-500"
                    >
                      {warehouseList.map((w) => (
                        <option key={w.id} value={w.code}>{w.name} ({w.code})</option>
                      ))}
                    </select>
                  ) : (
                    <span className="text-xs text-amber-400 bg-amber-500/10 border border-amber-500/20 px-3 py-2 rounded-xl">
                      No warehouses registered
                    </span>
                  )
                ) : cascadePrimaryType === 'INVENTORY_SHORTAGE' ? (
                  productList.length > 0 ? (
                    <select
                      value={cascadeTargetEntity}
                      onChange={(e) => setCascadeTargetEntity(e.target.value)}
                      className="bg-slate-950 border border-slate-800 text-xs text-slate-200 px-3 py-2 rounded-xl focus:outline-none focus:border-purple-500"
                    >
                      {productList.map((p) => (
                        <option key={p.id} value={p.sku}>{p.name} ({p.sku})</option>
                      ))}
                    </select>
                  ) : (
                    <span className="text-xs text-amber-400 bg-amber-500/10 border border-amber-500/20 px-3 py-2 rounded-xl">
                      No products registered
                    </span>
                  )
                ) : (
                  (supplierList.length > 0 || warehouseList.length > 0) ? (
                    <select
                      value={cascadeTargetEntity}
                      onChange={(e) => setCascadeTargetEntity(e.target.value)}
                      className="bg-slate-950 border border-slate-800 text-xs text-slate-200 px-3 py-2 rounded-xl focus:outline-none focus:border-purple-500"
                    >
                      {supplierList.map((s) => (
                        <option key={`sup-${s.id}`} value={s.supplierCode}>Vendor: {s.name} ({s.supplierCode})</option>
                      ))}
                      {warehouseList.map((w) => (
                        <option key={`wh-${w.id}`} value={w.code}>Hub: {w.name} ({w.code})</option>
                      ))}
                    </select>
                  ) : (
                    <span className="text-xs text-amber-400 bg-amber-500/10 border border-amber-500/20 px-3 py-2 rounded-xl">
                      No logistics nodes
                    </span>
                  )
                )}

                <button
                  type="button"
                  onClick={() => runCascadeSimulation(cascadePrimaryType, cascadeTargetEntity, false)}
                  disabled={loadingCascade || generatingProposal || !cascadeTargetEntity}
                  className="px-4 py-2 bg-purple-600 hover:bg-purple-500 text-white text-xs font-bold rounded-xl shadow-lg shadow-purple-600/20 transition-all disabled:opacity-50 flex items-center gap-2 whitespace-nowrap cursor-pointer"
                >
                  <Zap className={`h-3.5 w-3.5 ${loadingCascade ? 'animate-spin' : ''}`} />
                  <span>{loadingCascade ? 'Analyzing...' : 'Run Cascade Analysis'}</span>
                </button>
              </div>
            </div>
          </div>

          {/* Cascade Results */}
          {loadingCascade ? (
            renderTabLoading('Tracing multi-hop cascading disruption propagation across topology...')
          ) : errorCascade ? (
            renderTabError('Unable to Execute Cascade Analysis', errorCascade, () => runCascadeSimulation(cascadePrimaryType, cascadeTargetEntity, false))
          ) : cascadeResult ? (
            cascadeResult.cumulativeRiskBand === 'INSUFFICIENT_DATA' || cascadeResult.cascadeNodes.length === 0 ? (
              <div className="glass-panel p-10 text-center rounded-2xl border border-slate-800 space-y-3">
                <AlertCircle className="h-8 w-8 text-amber-400 mx-auto" />
                <h4 className="text-sm font-bold text-white">Insufficient Operational Data for Cascade Propagation</h4>
                <p className="text-xs text-slate-400 max-w-md mx-auto leading-relaxed">
                  Cascading multi-hop correlation models the propagation of failures across suppliers, warehouses, and inventory. Please upload suppliers.csv, warehouses.csv, and inventory.csv to trace impact chains.
                </p>
              </div>
            ) : (
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

                  <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl flex flex-col justify-between">
                    <div>
                      <span className="text-xs text-slate-400 font-semibold block">Chained Policy Proposals</span>
                      <span className="text-2xl font-black text-emerald-400 mt-1 block">
                        {cascadeResult.generatedRecommendationIds?.length || 0} Created
                      </span>
                      <span className="text-xs text-slate-400">
                        {cascadeResult.chainedActionProposalsCreated
                          ? 'Status: PENDING_APPROVAL'
                          : 'Simulation Mode (No DB mutations)'}
                      </span>
                    </div>

                    {!cascadeResult.chainedActionProposalsCreated && (
                      <button
                        type="button"
                        onClick={() => runCascadeSimulation(cascadePrimaryType, cascadeTargetEntity, true)}
                        disabled={generatingProposal || loadingCascade || !cascadeTargetEntity}
                        className="mt-3 inline-flex items-center justify-center space-x-1.5 px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold shadow-md shadow-emerald-600/20 transition-all disabled:opacity-50 cursor-pointer"
                      >
                        <Sparkles className={`w-3.5 h-3.5 ${generatingProposal ? 'animate-spin' : ''}`} />
                        <span>{generatingProposal ? 'Generating Proposals...' : 'Promote to Action Proposals'}</span>
                      </button>
                    )}
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
            )
          ) : (
            <div className="glass-panel p-10 text-center rounded-2xl border border-slate-800 space-y-3">
              <AlertCircle className="h-8 w-8 text-slate-500 mx-auto" />
              <h4 className="text-sm font-bold text-white">No Cascade Simulation Executed</h4>
              <p className="text-xs text-slate-400 max-w-md mx-auto leading-relaxed">
                Select a primary disruption type and entity above, then click &ldquo;Run Cascade Analysis&rdquo; to simulate systemic propagation.
              </p>
            </div>
          )}
        </div>
      )}


      {/* Tab: Multi-Echelon Inventory Rebalancing */}
      {activeTab === 'rebalance' && (
        loadingRebalance ? (
          renderTabLoading('Computing Multi-Echelon Inventory Rebalancing & Cross-Dock Routes...')
        ) : errorRebalance ? (
          renderTabError('Unable to Calculate Inventory Rebalancing', errorRebalance, () => fetchMultiEchelonRebalance())
        ) : rebalanceReport && rebalanceReport.rebalancingStatus !== 'INSUFFICIENT_DATA' ? (
          <div className="space-y-6">
            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl space-y-6">
              <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 border-b border-slate-800 pb-4">
                <div>
                  <h3 className="text-base font-bold text-white flex items-center gap-2">
                    <TrendingUp className="w-5 h-5 text-emerald-400" />
                    <span>Multi-Echelon Inventory Rebalancing & Cross-Dock Redistribution</span>
                  </h3>
                  <p className="text-xs text-slate-400">Target Deficit Hub: {rebalanceReport.targetWarehouseCode} | SKU: {rebalanceReport.skuCode} | Shortage: {rebalanceReport.deficitUnits} units</p>
                </div>

                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => fetchMultiEchelonRebalance()}
                    disabled={loadingRebalance}
                    className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-950 hover:bg-slate-800 text-slate-300 text-xs font-semibold border border-slate-800 transition-all cursor-pointer disabled:opacity-50"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 text-emerald-400 ${loadingRebalance ? 'animate-spin' : ''}`} />
                    <span>Refresh</span>
                  </button>
                  <span className="px-3 py-1 bg-emerald-500/10 text-emerald-300 border border-emerald-500/20 text-xs font-bold rounded-lg font-mono">
                    {rebalanceReport.rebalancePlanId}
                  </span>
                </div>
              </div>

              {/* Executive Summary Banner */}
              <div className="p-4 bg-emerald-500/10 border border-emerald-500/30 rounded-xl flex items-start gap-3 text-xs text-emerald-300">
                <TrendingUp className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
                <p className="leading-relaxed font-medium">{rebalanceReport.executiveSummary}</p>
              </div>

              {/* Metric Summary Cards */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 space-y-1">
                  <span className="text-xs text-slate-400 font-semibold block">Total Rebalanced Stock</span>
                  <span className="text-2xl font-black text-emerald-400">{rebalanceReport.totalRebalancedUnits} / {rebalanceReport.deficitUnits} units</span>
                </div>

                <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 space-y-1">
                  <span className="text-xs text-slate-400 font-semibold block">Procurement Cost Savings</span>
                  <span className="text-2xl font-black text-cyan-400">${rebalanceReport.totalCostSavingsVsNewPurchase.toLocaleString()}</span>
                </div>

                <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 space-y-1">
                  <span className="text-xs text-slate-400 font-semibold block">Lead Time Reduction</span>
                  <span className="text-2xl font-black text-purple-400">-{rebalanceReport.leadTimeReductionDays} Days vs Vendor Order</span>
                </div>
              </div>

              {/* Transfer Options Table */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Inter-Hub Transfer Routes</h4>
                <div className="grid grid-cols-1 gap-3">
                  {rebalanceReport.transferOptions.map((opt, idx) => (
                    <div key={idx} className="p-4 rounded-xl bg-slate-950 border border-slate-800 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className="px-2 py-0.5 bg-purple-500/20 text-purple-300 border border-purple-500/30 text-xs font-mono font-bold rounded">
                            {opt.sourceWarehouseCode}
                          </span>
                          <span className="text-slate-400 text-xs font-bold">➔</span>
                          <span className="px-2 py-0.5 bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 text-xs font-mono font-bold rounded">
                            {opt.targetWarehouseCode}
                          </span>
                          <span className="text-xs text-slate-300 font-semibold pl-2">{opt.sourceWarehouseName}</span>
                        </div>
                        <p className="text-xs text-slate-400">{opt.logisticsCorridor}</p>
                      </div>

                      <div className="flex items-center gap-6 text-xs">
                        <div>
                          <span className="text-slate-400 block">Transfer Quantity</span>
                          <span className="font-bold text-white text-sm">{opt.transferQuantityUnits} units</span>
                        </div>
                        <div>
                          <span className="text-slate-400 block">Transit Time</span>
                          <span className="font-bold text-emerald-400 text-sm">{opt.transitLeadTimeDays} day(s)</span>
                        </div>
                        <div>
                          <span className="text-slate-400 block">Est. Cost</span>
                          <span className="font-bold text-cyan-400 text-sm">${opt.transferCostEstimate.toLocaleString()}</span>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4">
            <div className="max-w-md mx-auto space-y-3">
              <AlertCircle className="h-8 w-8 text-emerald-400 mx-auto" />
              <h3 className="text-base font-bold text-white">Insufficient Data for Inventory Rebalancing</h3>
              <p className="text-xs text-slate-400 leading-relaxed">
                Multi-echelon rebalancing requires multiple registered warehouses and active inventory records to model cross-dock stock transfers.
              </p>
              <Link
                to="/import"
                className="inline-flex items-center px-4 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/20 transition-all"
              >
                Upload Ingestion Data
              </Link>
            </div>
          </div>
        )
      )}

      {/* Tab: Multi-Supplier Failover Split Router */}
      {activeTab === 'failover' && (
        loadingFailover ? (
          renderTabLoading('Synthesizing Supplier Failover & Split Containment Strategy...')
        ) : errorFailover ? (
          renderTabError('Unable to Calculate Supplier Failover Strategy', errorFailover, () => fetchFailoverContainment())
        ) : failoverReport && failoverReport.containmentStatus !== 'INSUFFICIENT_DATA' ? (
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

                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => fetchFailoverContainment()}
                    disabled={loadingFailover}
                    className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-950 hover:bg-slate-800 text-slate-300 text-xs font-semibold border border-slate-800 transition-all cursor-pointer disabled:opacity-50"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 text-cyan-400 ${loadingFailover ? 'animate-spin' : ''}`} />
                    <span>Refresh</span>
                  </button>
                  <span className="px-3 py-1 bg-cyan-500/10 text-cyan-300 border border-cyan-500/20 text-xs font-bold rounded-lg font-mono">
                    {failoverReport.containmentPlanId}
                  </span>
                </div>
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
        ) : (
          <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4">
            <div className="max-w-md mx-auto space-y-3">
              <AlertCircle className="h-8 w-8 text-cyan-400 mx-auto" />
              <h3 className="text-base font-bold text-white">Insufficient Data for Supplier Failover Routing</h3>
              <p className="text-xs text-slate-400 leading-relaxed">
                Failover routing requires at least two registered suppliers and warehouse facilities to model alternate split routing.
              </p>
              <Link
                to="/import"
                className="inline-flex items-center px-4 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/20 transition-all"
              >
                Upload Ingestion Data
              </Link>
            </div>
          </div>
        )
      )}

      {/* Tab: Cost-SLA Optimization Tradeoff Matrix */}
      {activeTab === 'costSla' && (
        loadingCostSla ? (
          renderTabLoading('Evaluating Cost vs. SLA Recovery Speed Tradeoff Matrix...')
        ) : errorCostSla ? (
          renderTabError('Unable to Evaluate Cost-SLA Tradeoff Matrix', errorCostSla, () => fetchCostSlaTradeoff())
        ) : costSlaReport && costSlaReport.initialRiskBand !== 'INSUFFICIENT_DATA' ? (
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

                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => fetchCostSlaTradeoff()}
                    disabled={loadingCostSla}
                    className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-950 hover:bg-slate-800 text-slate-300 text-xs font-semibold border border-slate-800 transition-all cursor-pointer disabled:opacity-50"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 text-indigo-400 ${loadingCostSla ? 'animate-spin' : ''}`} />
                    <span>Refresh</span>
                  </button>
                  <span className="px-3 py-1 bg-indigo-500/10 text-indigo-300 border border-indigo-500/20 text-xs font-bold rounded-lg font-mono">
                    {costSlaReport.analysisId}
                  </span>
                </div>
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
        ) : (
          <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4">
            <div className="max-w-md mx-auto space-y-3">
              <AlertCircle className="h-8 w-8 text-indigo-400 mx-auto" />
              <h3 className="text-base font-bold text-white">Insufficient Data for Cost-SLA Tradeoff Matrix</h3>
              <p className="text-xs text-slate-400 leading-relaxed">
                Evaluating logistics costs versus customer SLA preservation requires registered products and suppliers.
              </p>
              <Link
                to="/import"
                className="inline-flex items-center px-4 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/20 transition-all"
              >
                Upload Ingestion Data
              </Link>
            </div>
          </div>
        )
      )}

      {/* Tab: Historical Mitigation Efficacy */}
      {activeTab === 'historicalEfficacy' && (
        loadingHistoricalEfficacy ? (
          renderTabLoading('Analyzing Historical Mitigation Effectiveness & Audit Logs...')
        ) : errorHistoricalEfficacy ? (
          renderTabError('Unable to Load Historical Mitigation Analytics', errorHistoricalEfficacy, () => fetchHistoricalEfficacy())
        ) : historicalEfficacyReport && historicalEfficacyReport.totalHistoricalExecutions > 0 ? (
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
                  <button
                    type="button"
                    onClick={() => fetchHistoricalEfficacy()}
                    disabled={loadingHistoricalEfficacy}
                    className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-950 hover:bg-slate-800 text-slate-300 text-xs font-semibold border border-slate-800 transition-all cursor-pointer disabled:opacity-50"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 text-cyan-400 ${loadingHistoricalEfficacy ? 'animate-spin' : ''}`} />
                    <span>Refresh</span>
                  </button>
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
        ) : (
          <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4">
            <div className="max-w-md mx-auto space-y-3">
              <AlertCircle className="h-8 w-8 text-cyan-400 mx-auto" />
              <h3 className="text-base font-bold text-white">No Historical Mitigation Actions Recorded</h3>
              <p className="text-xs text-slate-400 leading-relaxed">
                Historical mitigation effectiveness analytics are computed from executed incident response actions in the Action Center. As disruptions are approved and resolved, efficacy statistics will accumulate here.
              </p>
            </div>
          </div>
        )
      )}

      {/* Tab 1: What-If Stress Testing Simulator */}
      {activeTab === 'simulation' && (
        <div className="space-y-6">
          {/* Deterministic Simulation Disclaimer */}
          <div className="p-4 rounded-xl bg-amber-500/10 border border-amber-500/30 flex items-start gap-3 text-xs text-amber-300">
            <AlertTriangle className="h-5 w-5 text-amber-400 shrink-0 mt-0.5" />
            <div>
              <span className="font-bold uppercase tracking-wider block text-amber-200">
                Deterministic Simulation Model (What-If Stress Testing)
              </span>
              <p className="mt-0.5 text-amber-300/90 leading-relaxed">
                This simulator performs mathematical stress testing against baseline telemetry. It evaluates hypothetical disruptions under theoretical demand surge and lead-time shock parameters. <strong>Simulations are strictly hypothetical and do NOT mutate or alter live operational inventory, supplier, or shipment records.</strong>
              </p>
            </div>
          </div>

          {/* Controls Card */}
          <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl space-y-6">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
              <h3 className="text-lg font-bold text-white flex items-center gap-2">
                <Zap className="h-5 w-5 text-amber-400" />
                Deterministic Supply Chain Stress-Test Simulation Controls
              </h3>
              <button
                type="button"
                onClick={() => runSimulation(demandSurge, leadTimeDelay)}
                disabled={loadingSimulation}
                className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-950 hover:bg-slate-800 text-slate-300 text-xs font-semibold border border-slate-800 transition-all cursor-pointer disabled:opacity-50"
              >
                <RefreshCw className={`w-3.5 h-3.5 text-amber-400 ${loadingSimulation ? 'animate-spin' : ''}`} />
                <span>Re-simulate</span>
              </button>
            </div>

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
          {loadingSimulation ? (
            renderTabLoading('Simulating Market Demand Surge & Supplier Delay Stress...')
          ) : errorSimulation ? (
            renderTabError('Unable to Run Stress Simulation', errorSimulation, () => runSimulation(demandSurge, leadTimeDelay))
          ) : simulationResult ? (
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
          ) : (
            <div className="glass-panel p-10 text-center rounded-2xl border border-slate-800 space-y-3">
              <AlertCircle className="h-8 w-8 text-slate-500 mx-auto" />
              <h4 className="text-sm font-bold text-white">No Stress Simulation Results</h4>
              <p className="text-xs text-slate-400 max-w-md mx-auto leading-relaxed">
                Adjust the demand surge and supplier delay sliders above to compute projected stockouts and financial exposure.
              </p>
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Forecast Accuracy & Backtesting */}
      {activeTab === 'accuracy' && (
        loadingAccuracy ? (
          renderTabLoading('Evaluating SKU Forecast Accuracy & Backtesting Metrics...')
        ) : errorAccuracy ? (
          renderTabError('Unable to Evaluate Forecast Accuracy', errorAccuracy, () => fetchAccuracy(selectedProductId))
        ) : accuracyData ? (
          <div className="space-y-6">
            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div>
                <span className="text-xs text-slate-500 uppercase font-bold block">Selected SKU Evaluation</span>
                <h3 className="text-xl font-bold text-white mt-1">{accuracyData.productName} ({accuracyData.productSku})</h3>
              </div>

              <div className="flex items-center gap-3 flex-wrap">
                <button
                  type="button"
                  onClick={() => fetchAccuracy(selectedProductId)}
                  disabled={loadingAccuracy}
                  className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-950 hover:bg-slate-800 text-slate-300 text-xs font-semibold border border-slate-800 transition-all cursor-pointer disabled:opacity-50"
                >
                  <RefreshCw className={`w-3.5 h-3.5 text-purple-400 ${loadingAccuracy ? 'animate-spin' : ''}`} />
                  <span>Refresh</span>
                </button>
                <div className="flex items-center gap-2 flex-wrap">
                  {productList.map((p) => (
                    <button
                      key={p.id}
                      onClick={() => { setSelectedProductId(p.id); fetchAccuracy(p.id); }}
                      className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                        selectedProductId === p.id ? 'bg-purple-600 text-white shadow-md' : 'bg-slate-800 text-slate-400 hover:text-white'
                      }`}
                    >
                      {p.sku}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl">
              <span className="text-xs text-slate-400 uppercase font-bold block">MAPE Accuracy Index</span>
              <span className="text-3xl font-black text-emerald-400 mt-2 block">
                {accuracyData.mapePercentage != null ? `${accuracyData.mapePercentage}%` : 'N/A'}
              </span>
              <span className="text-xs text-slate-500 mt-1 block">Mean Absolute Percentage Error</span>
            </div>

            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl">
              <span className="text-xs text-slate-400 uppercase font-bold block">RMSE Error Standard</span>
              <span className="text-3xl font-black text-blue-400 mt-2 block">
                {accuracyData.rmseValue != null ? accuracyData.rmseValue : 'N/A'}
              </span>
              <span className="text-xs text-slate-500 mt-1 block">Root Mean Squared Error</span>
            </div>

            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl">
              <span className="text-xs text-slate-400 uppercase font-bold block">Backtesting Model Rating</span>
              <span className="text-3xl font-black text-purple-400 mt-2 block">{accuracyData.accuracyRating || 'INSUFFICIENT_DATA'}</span>
              <span className="text-xs text-slate-500 mt-1 block">
                {accuracyData.sampleSizeMonths ? `Validated over ${accuracyData.sampleSizeMonths}-month dataset` : 'Requires minimum 4-month order history'}
              </span>
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
                {accuracyData.comparisons && accuracyData.comparisons.length > 0 ? (
                  accuracyData.comparisons.map((c, idx) => (
                    <tr key={idx} className="hover:bg-slate-800/40 transition-colors">
                      <td className="p-4 font-bold text-white">{c.monthLabel}</td>
                      <td className="p-4 font-bold text-emerald-400">{c.actualSales} units</td>
                      <td className="p-4 font-bold text-blue-400">{c.predictedDemand} units</td>
                      <td className="p-4 font-mono font-bold text-purple-400">{c.absolutePercentageError}%</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={4} className="p-6 text-center text-slate-400">
                      Insufficient monthly order history to perform out-of-sample backtesting (requires at least 4 months of sales).
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
          <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4">
            <div className="max-w-md mx-auto space-y-3">
              <h3 className="text-base font-bold text-white">No Forecast Accuracy Data Available</h3>
              <p className="text-xs text-slate-400 leading-relaxed">
                Backtesting evaluation requires catalog products and at least 4 months of historical order demand.
                Upload products.csv and orders.csv to compute deterministic MAPE and RMSE accuracy ratings.
              </p>
              <Link
                to="/import"
                className="inline-flex items-center px-4 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/20 transition-all"
              >
                Upload Ingestion Data
              </Link>
            </div>
          </div>
        )
      )}

      {/* Tab 3: Dynamic Safety Stock Optimization */}
      {activeTab === 'optimization' && (
        loadingOptimization ? (
          renderTabLoading('Calculating Dynamic Safety Stock Optimization...')
        ) : errorOptimization ? (
          renderTabError('Unable to Calculate Safety Stock Optimization', errorOptimization, () => fetchOptimization())
        ) : optimizationReport && optimizationReport.totalItemsEvaluated > 0 ? (
          <div className="space-y-6">
            <div className="flex justify-end">
              <button
                type="button"
                onClick={() => fetchOptimization()}
                disabled={loadingOptimization}
                className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-300 text-xs font-semibold border border-slate-800 transition-all cursor-pointer disabled:opacity-50"
              >
                <RefreshCw className={`w-3.5 h-3.5 text-emerald-400 ${loadingOptimization ? 'animate-spin' : ''}`} />
                <span>Refresh Optimization</span>
              </button>
            </div>
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
        ) : (
          <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-4">
            <div className="max-w-md mx-auto space-y-3">
              <AlertCircle className="h-8 w-8 text-emerald-400 mx-auto" />
              <h3 className="text-base font-bold text-white">No Inventory Items Available for Optimization</h3>
              <p className="text-xs text-slate-400 leading-relaxed">
                Dynamic safety stock calculation requires active inventory records. Upload products.csv and inventory.csv to evaluate dynamic safety buffers and reorder points.
              </p>
              <Link
                to="/import"
                className="inline-flex items-center px-4 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold shadow-lg shadow-cyan-600/20 transition-all"
              >
                Upload Ingestion Data
              </Link>
            </div>
          </div>
        )
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
