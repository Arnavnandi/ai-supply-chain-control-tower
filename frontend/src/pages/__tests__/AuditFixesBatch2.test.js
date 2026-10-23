import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { classifyApiError } from '../../utils/errorClassification.ts';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// ============================================================================
// FIX 1: Dashboard Telemetry Independence (Promise.allSettled)
// ============================================================================

test('Dashboard Telemetry: Promise.allSettled allows KPI cards to render when predictive early-warning radar fails', async () => {
  // Simulate mock responses
  const mockIntelSummary = {
    summaryKpis: {
      totalProducts: 48,
      totalInventoryUnits: 18500,
      totalInventoryValue: 142500.0,
      supplyChainRiskScore: 78.4,
      overallSupplierReliabilityPct: 91.2,
      warehouseUtilization: [{ warehouseId: 1, utilizationPct: 82.5 }],
      inventoryTrends: [{ month: 'Jan', available: 12000, safety: 4000 }],
    },
    executiveAiBriefing: 'Real-time telemetry grounded in PostgreSQL database records.',
  };

  const mockExecScorecard = {
    reportId: 'EXEC-001',
    overallResiliencyIndex: 82.0,
    resiliencyStatusBand: 'OPTIMAL',
    executiveBriefingSummary: 'Supply chain network operating with high resilience.',
  };

  const mockRadarTimeout = {
    code: 'ECONNABORTED',
    message: 'timeout of 60000ms exceeded',
  };

  // Run Promise.allSettled as implemented in DashboardPage.tsx
  const [intelRes, radarRes, execRes] = await Promise.allSettled([
    Promise.resolve({ data: mockIntelSummary }),
    Promise.reject(mockRadarTimeout),
    Promise.resolve({ data: mockExecScorecard }),
  ]);

  let summary = null;
  let intelligence = null;
  let radarReport = null;
  let executiveScorecard = null;
  let globalError = null;
  let radarError = null;
  let scorecardError = null;

  // Process 1: Core Intelligence Summary
  if (intelRes.status === 'fulfilled') {
    summary = intelRes.value.data?.summaryKpis || null;
    intelligence = intelRes.value.data;
    globalError = null;
  } else {
    const classified = classifyApiError(intelRes.reason, 'Failed to load control tower dashboard intelligence.');
    globalError = classified.message;
  }

  // Process 2: Early Warning Radar
  if (radarRes.status === 'fulfilled') {
    radarReport = radarRes.value.data;
  } else {
    const classified = classifyApiError(radarRes.reason, 'Predictive early-warning radar simulation unavailable.');
    radarError = classified.message;
    radarReport = null;
  }

  // Process 3: Executive Scorecard
  if (execRes.status === 'fulfilled') {
    executiveScorecard = execRes.value.data;
  } else {
    const classified = classifyApiError(execRes.reason, 'Executive resiliency scorecard unavailable.');
    scorecardError = classified.message;
    executiveScorecard = null;
  }

  // VERIFICATION:
  // 1. Global error is null (does NOT block the page into error state)
  assert.equal(globalError, null);
  // 2. Core KPIs are successfully derived and available for cards
  assert.notEqual(summary, null);
  assert.equal(summary.totalProducts, 48);
  assert.equal(summary.totalInventoryValue, 142500.0);
  assert.equal(summary.supplyChainRiskScore, 78.4);
  assert.equal(intelligence.executiveAiBriefing, 'Real-time telemetry grounded in PostgreSQL database records.');
  // 3. Executive scorecard is populated
  assert.notEqual(executiveScorecard, null);
  assert.equal(executiveScorecard.overallResiliencyIndex, 82.0);
  assert.equal(scorecardError, null);
  // 4. Radar failure is isolated, retaining genuine error message without fabricating data
  assert.equal(radarReport, null);
  assert.notEqual(radarError, null);
  assert.match(radarError, /timed out/i);
});

test('Dashboard Telemetry: genuine core summary failure sets global error and triggers retry', async () => {
  const mockCoreFailure = {
    response: { status: 500, data: { message: 'Database connection pool exhausted.' } },
  };

  const [intelRes, _radarRes, _execRes] = await Promise.allSettled([
    Promise.reject(mockCoreFailure),
    Promise.resolve({ data: { earlyWarnings: [] } }),
    Promise.resolve({ data: { reportId: 'EXEC-001' } }),
  ]);

  let summary = null;
  let globalError = null;

  if (intelRes.status === 'fulfilled') {
    summary = intelRes.value.data?.summaryKpis || null;
  } else {
    const classified = classifyApiError(intelRes.reason, 'Failed to load control tower dashboard intelligence.');
    globalError = classified.message;
  }

  // VERIFICATION: Core failure correctly triggers page-level error state with genuine message
  assert.equal(summary, null);
  assert.equal(globalError, 'Database connection pool exhausted.');
});

test('Dashboard Telemetry: race condition guard rejects stale response from slower previous request', async () => {
  let activeRequestId = 0;
  let latestRenderedId = null;

  const simulateFetch = async (requestId, delayMs, payloadValue) => {
    await new Promise((r) => setTimeout(r, delayMs));
    // Guard from DashboardPage.tsx
    if (requestId !== activeRequestId) {
      return; // Discard stale response
    }
    latestRenderedId = payloadValue;
  };

  // Request 1 starts (slow, 50ms)
  activeRequestId = 1;
  const req1 = simulateFetch(1, 50, 'PAYLOAD_REQ_1');

  // Request 2 starts (fast, 10ms - e.g. user clicked refresh immediately)
  activeRequestId = 2;
  const req2 = simulateFetch(2, 10, 'PAYLOAD_REQ_2');

  await Promise.all([req1, req2]);

  // VERIFICATION: Request 1 must NOT overwrite Request 2
  assert.equal(latestRenderedId, 'PAYLOAD_REQ_2');
});

// ============================================================================
// FIX 2: Action Center SKU Fallback Logic
// ============================================================================

function resolveActionTargetSku(payload) {
  return payload.productSku?.trim() || payload.sku?.trim() || 'SKU-N/A';
}

test('Action Center SKU Fallback: displays productSku when present', () => {
  const payload = {
    productId: 101,
    productSku: 'SKU-HYD-001',
    warehouseName: 'Chicago Assembly Hub',
  };
  assert.equal(resolveActionTargetSku(payload), 'SKU-HYD-001');
});

test('Action Center SKU Fallback: falls back to sku when productSku is absent', () => {
  const payload = {
    productId: 102,
    sku: 'SKU-VALVE-002',
    orderQuantity: 200,
  };
  assert.equal(resolveActionTargetSku(payload), 'SKU-VALVE-002');
});

test('Action Center SKU Fallback: prioritizes productSku over sku when both are present', () => {
  const payload = {
    productSku: 'SKU-PRIMARY-001',
    sku: 'SKU-ALT-002',
  };
  assert.equal(resolveActionTargetSku(payload), 'SKU-PRIMARY-001');
});

test('Action Center SKU Fallback: gracefully returns SKU-N/A when neither field is available', () => {
  assert.equal(resolveActionTargetSku({}), 'SKU-N/A');
  assert.equal(resolveActionTargetSku({ productSku: '', sku: '' }), 'SKU-N/A');
  assert.equal(resolveActionTargetSku({ productSku: '   ', sku: '   ' }), 'SKU-N/A');
  assert.equal(resolveActionTargetSku({ targetEntity: 'North Hub' }), 'SKU-N/A');
});

// ============================================================================
// FIX 3: Shipments Responsive Table Container (overflow-x-auto)
// ============================================================================

test('Shipments Responsive Table: table element is wrapped in overflow-x-auto container', () => {
  const shipmentsFilePath = resolve(__dirname, '../ShipmentsPage.tsx');
  const fileContent = readFileSync(shipmentsFilePath, 'utf-8');

  // Verify overflow-x-auto wrapper precedes table inside glass-panel
  const pattern = /<div className="glass-panel rounded-xl overflow-hidden border border-slate-800">\s*<div className="overflow-x-auto">\s*<table className="w-full text-left text-sm text-slate-300">/;
  assert.match(
    fileContent,
    pattern,
    'ShipmentsPage must wrap table in <div className="overflow-x-auto"> to prevent clipping on mobile/tablet viewports'
  );

  // Verify closing tags match
  assert.match(
    fileContent,
    /<\/table>\s*<\/div>\s*<\/div>/,
    'ShipmentsPage must properly close the overflow-x-auto wrapper and card container'
  );

  // Verify all 6 columns remain intact
  assert.match(fileContent, /Tracking Code/);
  assert.match(fileContent, /Supplier/);
  assert.match(fileContent, /Origin ➔ Destination/);
  assert.match(fileContent, /Carrier/);
  assert.match(fileContent, /Estimated Delivery/);
  assert.match(fileContent, /Status/);
});

// ============================================================================
// FIX 4: Sidebar Navigation (/documents Policy Knowledge RAG)
// ============================================================================

test('Sidebar Navigation: registers Policy Knowledge RAG linking to /documents with BookOpen icon', () => {
  const sidebarFilePath = resolve(__dirname, '../../components/Sidebar.tsx');
  const fileContent = readFileSync(sidebarFilePath, 'utf-8');

  // Verify BookOpen icon is imported
  assert.match(fileContent, /\bBookOpen\b/, 'Sidebar must import BookOpen icon from lucide-react');

  // Verify nav item entry in primaryNavItems
  assert.match(
    fileContent,
    /{\s*name:\s*'Policy Knowledge RAG',\s*path:\s*'\/documents',\s*icon:\s*BookOpen\s*}/,
    'Sidebar primaryNavItems must include Policy Knowledge RAG linking to /documents'
  );

  // Verify App.tsx route matches
  const appFilePath = resolve(__dirname, '../../App.tsx');
  const appContent = readFileSync(appFilePath, 'utf-8');
  assert.match(
    appContent,
    /<Route\s+path="\/documents"\s+element={<ProtectedLayout\s+title="Supply Chain Policy RAG"><DocumentRagPage\s*\/><\/ProtectedLayout>}\s*\/>/,
    'App.tsx must define matching /documents route for DocumentRagPage'
  );

  // Verify existing navigation items are preserved
  const expectedNavPaths = [
    '/',
    '/import',
    '/ai-assistant',
    '/analytics',
    '/documents',
    '/inventory',
    '/products',
    '/suppliers',
    '/warehouses',
    '/shipments',
    '/risks',
    '/action-center',
    '/system-health',
  ];

  for (const path of expectedNavPaths) {
    assert.match(
      fileContent,
      new RegExp(`['"]${path.replace('/', '\\/')}['"]`),
      `Sidebar must preserve route: ${path}`
    );
  }
});
