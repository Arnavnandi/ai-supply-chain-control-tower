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

export const AnalyticsPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'accuracy' | 'optimization' | 'simulation'>('simulation');

  // Accuracy State
  const [accuracyData, setAccuracyData] = useState<AccuracyMetrics | null>(null);
  const [selectedProductId, setSelectedProductId] = useState<number>(1);

  // Optimization State
  const [optimizationReport, setOptimizationReport] = useState<OptimizationReport | null>(null);

  // Simulation State
  const [demandSurge, setDemandSurge] = useState<number>(30);
  const [leadTimeDelay, setLeadTimeDelay] = useState<number>(5);
  const [simulationResult, setSimulationResult] = useState<SimulationResult | null>(null);

  // Executive Report Modal State
  const [executiveReport, setExecutiveReport] = useState<any | null>(null);
  const [showReportModal, setShowReportModal] = useState<boolean>(false);

  useEffect(() => {
    fetchAccuracy(selectedProductId);
    fetchOptimization();
    runSimulation(demandSurge, leadTimeDelay);
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
            <h1 className="text-2xl font-bold tracking-tight text-white">Advanced Analytics & Stress-Testing Simulator</h1>
          </div>
          <p className="text-slate-400 text-sm pl-12">
            Academic Research Suite: Forecast Backtesting (MAPE/RMSE), Dynamic Safety Stock Optimization, and What-If Supply Chain Stress Testing.
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
      <div className="flex items-center gap-4 border-b border-slate-800 pb-4">
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
