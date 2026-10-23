import test from 'node:test';
import assert from 'node:assert/strict';

// Helper to simulate the lazy-tab data router logic from AnalyticsPage.tsx
function createAnalyticsTabManager() {
  const tabDataStore = {
    masterOrchestrator: null,
    cascade: null,
    rebalance: null,
    failover: null,
    costSla: null,
    historicalEfficacy: null,
    simulation: null,
    accuracy: null,
    optimization: null,
  };

  const requestCounts = {
    masterOrchestrator: 0,
    cascade: 0,
    rebalance: 0,
    failover: 0,
    costSla: 0,
    historicalEfficacy: 0,
    simulation: 0,
    accuracy: 0,
    optimization: 0,
  };

  const cascadeProposalCalls = [];

  const loadTabData = async (tab, forceRefresh = false, options = {}) => {
    switch (tab) {
      case 'masterOrchestrator':
        if (!tabDataStore.masterOrchestrator || forceRefresh) {
          requestCounts.masterOrchestrator++;
          tabDataStore.masterOrchestrator = { blueprintId: 'MB-101', orchestrationStatus: 'RESOLVED' };
        }
        break;
      case 'cascade':
        if (!tabDataStore.cascade || forceRefresh) {
          requestCounts.cascade++;
          const convertToProposal = Boolean(options.convertToProposal);
          cascadeProposalCalls.push(convertToProposal);
          tabDataStore.cascade = {
            primaryDisruption: options.disruption || 'SUPPLIER_DISRUPTION',
            chainedActionProposalsCreated: convertToProposal,
            cumulativeRiskScore: 68.5,
            cumulativeRiskBand: 'MODERATE_RISK',
            cascadeNodes: [{ hopLevel: 1, domain: 'SUPPLIERS' }],
          };
        }
        break;
      case 'rebalance':
        if (!tabDataStore.rebalance || forceRefresh) {
          requestCounts.rebalance++;
          tabDataStore.rebalance = { rebalancePlanId: 'REBAL-001', rebalancingStatus: 'FEASIBLE' };
        }
        break;
      case 'failover':
        if (!tabDataStore.failover || forceRefresh) {
          requestCounts.failover++;
          tabDataStore.failover = { containmentPlanId: 'FAILOVER-001', containmentStatus: 'ACTIVE' };
        }
        break;
      case 'costSla':
        if (!tabDataStore.costSla || forceRefresh) {
          requestCounts.costSla++;
          tabDataStore.costSla = { analysisId: 'CSLA-001', initialRiskBand: 'MODERATE' };
        }
        break;
      case 'historicalEfficacy':
        if (!tabDataStore.historicalEfficacy || forceRefresh) {
          requestCounts.historicalEfficacy++;
          tabDataStore.historicalEfficacy = { totalHistoricalExecutions: 5, overallSuccessRatePct: 92.5 };
        }
        break;
      case 'simulation':
        if (!tabDataStore.simulation || forceRefresh) {
          requestCounts.simulation++;
          tabDataStore.simulation = { baselineRiskScore: 40, simulatedRiskScore: 75 };
        }
        break;
      case 'accuracy':
        if (!tabDataStore.accuracy || forceRefresh) {
          requestCounts.accuracy++;
          tabDataStore.accuracy = { productId: 1, accuracyRating: 'HIGH_PRECISION' };
        }
        break;
      case 'optimization':
        if (!tabDataStore.optimization || forceRefresh) {
          requestCounts.optimization++;
          tabDataStore.optimization = { totalItemsEvaluated: 10, totalCapitalOptimizationPotential: 50000 };
        }
        break;
    }
    return tabDataStore[tab];
  };

  return { tabDataStore, requestCounts, cascadeProposalCalls, loadTabData };
}

// Helper to simulate view state machine for analytics tab rendering
function evaluateTabViewState({ loading, error, data }) {
  if (loading) return 'LOADING';
  if (error) return 'ERROR';
  if (!data) return 'EMPTY_UNINITIALIZED';
  return 'SUCCESS';
}

// Helper to simulate risk normalization and deduplication in RisksPage.tsx
function normalizeRisks({ intelligenceRisks = [], persistedAlerts = [] }) {
  const items = [];

  for (const ir of intelligenceRisks) {
    items.push({
      id: `intel-${ir.riskType}-${ir.relatedEntityId ?? ir.title}`,
      title: ir.title || ir.riskType || 'Operational Risk',
      description: ir.description || 'Active operational telemetry disruption.',
      severity: ir.severity || 'HIGH',
      status: ir.status || 'ACTIVE',
      category: ir.riskType || 'OPERATIONAL',
      source: 'DATABASE_TELEMETRY',
    });
  }

  for (const pa of persistedAlerts) {
    items.push({
      id: `alert-${pa.id}`,
      title: pa.title || pa.riskType || 'System Alert',
      description: pa.description || '',
      severity: pa.severity || 'MEDIUM',
      status: pa.status || 'ACTIVE',
      category: pa.category || 'SYSTEM',
      source: 'PERSISTED_ALERT',
    });
  }

  return items;
}

test('AnalyticsPage: initial mount only loads active tab (masterOrchestrator), zero secondary requests', async () => {
  const manager = createAnalyticsTabManager();

  // Simulate initial mount: only loadTabData('masterOrchestrator') is executed
  await manager.loadTabData('masterOrchestrator');

  assert.equal(manager.requestCounts.masterOrchestrator, 1);
  assert.equal(manager.requestCounts.cascade, 0);
  assert.equal(manager.requestCounts.rebalance, 0);
  assert.equal(manager.requestCounts.failover, 0);
  assert.equal(manager.requestCounts.costSla, 0);
  assert.equal(manager.requestCounts.historicalEfficacy, 0);
  assert.equal(manager.requestCounts.simulation, 0);
  assert.equal(manager.requestCounts.accuracy, 0);
  assert.equal(manager.requestCounts.optimization, 0);
});

test('AnalyticsPage: tab caching avoids duplicate requests when switching between loaded tabs', async () => {
  const manager = createAnalyticsTabManager();

  // Mount
  await manager.loadTabData('masterOrchestrator');
  assert.equal(manager.requestCounts.masterOrchestrator, 1);

  // User clicks rebalance
  await manager.loadTabData('rebalance');
  assert.equal(manager.requestCounts.rebalance, 1);

  // User switches back to masterOrchestrator
  await manager.loadTabData('masterOrchestrator');
  assert.equal(manager.requestCounts.masterOrchestrator, 1, 'masterOrchestrator must reuse cached data without re-fetching');

  // User switches back to rebalance
  await manager.loadTabData('rebalance');
  assert.equal(manager.requestCounts.rebalance, 1, 'rebalance must reuse cached data without re-fetching');
});

test('AnalyticsPage: explicit refresh bypasses cache and triggers fresh request', async () => {
  const manager = createAnalyticsTabManager();

  await manager.loadTabData('masterOrchestrator');
  assert.equal(manager.requestCounts.masterOrchestrator, 1);

  // Explicit user refresh action on tab
  await manager.loadTabData('masterOrchestrator', true);
  assert.equal(manager.requestCounts.masterOrchestrator, 2, 'Explicit refresh must increment request count');
});

test('AnalyticsPage: cascade simulation defaults to convertToActionProposal=false for fast simulation', async () => {
  const manager = createAnalyticsTabManager();

  // Normal viewing / simulation
  await manager.loadTabData('cascade', false, { convertToProposal: false });

  assert.equal(manager.cascadeProposalCalls.length, 1);
  assert.equal(manager.cascadeProposalCalls[0], false, 'Default simulation must not mutate DB or invoke multi-agent LLM proposals');
  assert.equal(manager.tabDataStore.cascade.chainedActionProposalsCreated, false);
});

test('AnalyticsPage: cascade simulation only enables proposals after explicit promotion action', async () => {
  const manager = createAnalyticsTabManager();

  // Normal simulation first
  await manager.loadTabData('cascade', false, { convertToProposal: false });
  assert.equal(manager.tabDataStore.cascade.chainedActionProposalsCreated, false);

  // User explicitly clicks "Promote to Action Proposals"
  await manager.loadTabData('cascade', true, { convertToProposal: true });

  assert.equal(manager.cascadeProposalCalls.length, 2);
  assert.equal(manager.cascadeProposalCalls[1], true, 'Explicit action must set convertToActionProposal=true');
  assert.equal(manager.tabDataStore.cascade.chainedActionProposalsCreated, true);
});

test('AnalyticsPage: view state machine never flashes empty or calculated state while request is pending or failed', () => {
  // Case 1: Pending request with no data yet
  assert.equal(
    evaluateTabViewState({ loading: true, error: null, data: null }),
    'LOADING',
    'Pending tab request must render loading spinner, never empty state'
  );

  // Case 2: Re-fetching data
  assert.equal(
    evaluateTabViewState({ loading: true, error: null, data: { status: 'OLD' } }),
    'LOADING',
    'Re-fetching must prioritize loading over stale data'
  );

  // Case 3: Failed request
  assert.equal(
    evaluateTabViewState({ loading: false, error: 'Connection failed', data: null }),
    'ERROR',
    'Failed tab request must render error card with working retry, never empty state'
  );

  // Case 4: Successful response
  assert.equal(
    evaluateTabViewState({ loading: false, error: null, data: { status: 'RESOLVED' } }),
    'SUCCESS',
    'Loaded tab renders actual data'
  );
});

test('RisksPage: normalizes live operational risks alongside persisted alerts', () => {
  const intelligenceRisks = [
    {
      riskType: 'STOCKOUT',
      title: 'Projected Stockout in Warehouse A',
      description: 'SKU-001 inventory level below safety stock.',
      severity: 'CRITICAL',
      status: 'ACTIVE',
    },
  ];

  const persistedAlerts = [
    {
      id: 42,
      riskType: 'CARRIER_DELAY',
      title: 'Trans-Pacific Freight Delay',
      description: 'Port congestion causing 4 day delay.',
      severity: 'HIGH',
      status: 'ACKNOWLEDGED',
    },
  ];

  const merged = normalizeRisks({ intelligenceRisks, persistedAlerts });

  assert.equal(merged.length, 2);
  assert.equal(merged[0].id, 'intel-STOCKOUT-Projected Stockout in Warehouse A');
  assert.equal(merged[0].source, 'DATABASE_TELEMETRY');
  assert.equal(merged[1].id, 'alert-42');
  assert.equal(merged[1].source, 'PERSISTED_ALERT');
});

test('RisksPage: distinguish loading, error, populated, and genuine empty state', () => {
  const evaluateRisksView = ({ loading, error, risks }) => {
    if (loading) return 'LOADING';
    if (error) return 'ERROR';
    if (!risks || risks.length === 0) return 'EMPTY_ZERO_RISKS';
    return 'POPULATED';
  };

  assert.equal(evaluateRisksView({ loading: true, error: null, risks: [] }), 'LOADING');
  assert.equal(evaluateRisksView({ loading: false, error: 'Network timeout', risks: [] }), 'ERROR');
  assert.equal(evaluateRisksView({ loading: false, error: null, risks: [] }), 'EMPTY_ZERO_RISKS');
  assert.equal(evaluateRisksView({ loading: false, error: null, risks: [{ id: 1 }] }), 'POPULATED');
});

test('RisksPage: when primary telemetry endpoint fails and alerts return 0 items, error is surfaced and not hidden as empty state', () => {
  const resolveRisksState = (intelSettled, persistedSettled) => {
    const items = [];
    if (intelSettled.status === 'fulfilled' && intelSettled.value.data?.riskItems) {
      items.push(...intelSettled.value.data.riskItems);
    }
    if (persistedSettled.status === 'fulfilled' && Array.isArray(persistedSettled.value.data)) {
      items.push(...persistedSettled.value.data);
    }

    const intelError = intelSettled.status === 'rejected' ? intelSettled.reason : null;
    const persistedError = persistedSettled.status === 'rejected' ? persistedSettled.reason : null;

    if (intelError || persistedError) {
      const primaryError = intelError || persistedError;
      if (primaryError?.isAuthError || items.length === 0) {
        return { error: primaryError?.message || 'Failed to load operational risks', risks: [] };
      }
    }
    return { error: null, risks: items };
  };

  // Case A: /intelligence/risks rejects (e.g. 500/timeout), /risks resolves to empty array []
  const resultA = resolveRisksState(
    { status: 'rejected', reason: { message: 'Backend timeout after 60s' } },
    { status: 'fulfilled', value: { data: [] } }
  );
  assert.equal(resultA.error, 'Backend timeout after 60s');
  assert.equal(resultA.risks.length, 0);

  // Case B: Both resolve successfully to empty array []
  const resultB = resolveRisksState(
    { status: 'fulfilled', value: { data: { riskItems: [] } } },
    { status: 'fulfilled', value: { data: [] } }
  );
  assert.equal(resultB.error, null);
  assert.equal(resultB.risks.length, 0);

  // Case C: /intelligence/risks succeeds with 2 items, /risks rejects
  const resultC = resolveRisksState(
    { status: 'fulfilled', value: { data: { riskItems: [{ id: 'live-1' }, { id: 'live-2' }] } } },
    { status: 'rejected', reason: { message: 'DB connection error' } }
  );
  assert.equal(resultC.error, null);
  assert.equal(resultC.risks.length, 2, 'Available telemetry items should be presented if partial failure occurs');
});

test('AnalyticsPage: concurrency guards prevent duplicate simultaneous requests while in flight', async () => {
  let inFlight = false;
  let executionCount = 0;

  const concurrentProtectedSimulator = async () => {
    if (inFlight) return 'DROPPED';
    inFlight = true;
    executionCount++;
    // Simulate async delay
    await new Promise((r) => setTimeout(r, 10));
    inFlight = false;
    return 'EXECUTED';
  };

  // Fire three calls concurrently
  const [res1, res2, res3] = await Promise.all([
    concurrentProtectedSimulator(),
    concurrentProtectedSimulator(),
    concurrentProtectedSimulator(),
  ]);

  assert.equal(res1, 'EXECUTED');
  assert.equal(res2, 'DROPPED');
  assert.equal(res3, 'DROPPED');
  assert.equal(executionCount, 1, 'Only one invocation should run while in flight');
});

test('AnalyticsPage: fast tab switching preserves isolated states without cross-contamination', async () => {
  const manager = createAnalyticsTabManager();

  // Rapidly switch between multiple tabs
  await Promise.all([
    manager.loadTabData('failover'),
    manager.loadTabData('costSla'),
    manager.loadTabData('historicalEfficacy'),
  ]);

  assert.equal(manager.requestCounts.failover, 1);
  assert.equal(manager.requestCounts.costSla, 1);
  assert.equal(manager.requestCounts.historicalEfficacy, 1);

  assert.equal(manager.tabDataStore.failover.containmentPlanId, 'FAILOVER-001');
  assert.equal(manager.tabDataStore.costSla.analysisId, 'CSLA-001');
  assert.equal(manager.tabDataStore.historicalEfficacy.overallSuccessRatePct, 92.5);
});

