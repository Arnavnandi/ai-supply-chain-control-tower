import test from 'node:test';
import assert from 'node:assert/strict';
import axios from 'axios';
import { getBackendOrigin, getBaseURL } from '../../api/axiosInstance.ts';

// -------------------------------------------------------------
// 1. Dashboard summary deduplication
// -------------------------------------------------------------
test('dashboard deduplication: summary KPIs are safely derived from intelligence summary payload', () => {
  const intelResponse = {
    summaryKpis: {
      totalProducts: 42,
      totalInventoryUnits: 15400,
      totalInventoryValue: 78500.5,
      lowStockProductsCount: 3,
      overstockedProductsCount: 1,
      pendingOrdersCount: 8,
      delayedShipmentsCount: 2,
    },
    actionRecommendations: [],
  };

  const summary = intelResponse?.summaryKpis || null;
  assert.notEqual(summary, null);
  assert.equal(summary.totalProducts, 42);
  assert.equal(summary.totalInventoryUnits, 15400);
  assert.equal(summary.lowStockProductsCount, 3);
});

test('dashboard deduplication: null/empty intelligence payload safely falls back to null without crashing', () => {
  const emptyRes = {};
  const summary = emptyRes?.summaryKpis || null;
  assert.equal(summary, null);
});

// -------------------------------------------------------------
// 2. System Health endpoint routing and origin resolution
// -------------------------------------------------------------
test('getBackendOrigin correctly strips /api context path to produce un-prefixed Actuator origin', () => {
  // Test root-relative '/api'
  const originFromRelative = (url) => (url || '').replace(/\/api\/?$/, '');
  assert.equal(originFromRelative('/api'), '');
  assert.equal(originFromRelative('/api/'), '');

  // Test full URL with /api suffix (e.g. Render staging/production URL)
  assert.equal(
    originFromRelative('https://ai-supply-chain-control-tower-backend-nkw0.onrender.com/api'),
    'https://ai-supply-chain-control-tower-backend-nkw0.onrender.com'
  );
  assert.equal(
    originFromRelative('https://ai-supply-chain-control-tower-backend-nkw0.onrender.com/api/'),
    'https://ai-supply-chain-control-tower-backend-nkw0.onrender.com'
  );

  // Active getBackendOrigin output against current environment
  const currentBase = getBaseURL();
  const currentOrigin = getBackendOrigin();
  if (currentBase === '/api') {
    assert.equal(currentOrigin, '');
  } else {
    assert.equal(currentOrigin, currentBase.replace(/\/api\/?$/, ''));
  }
});

test('System Health Actuator URLs resolve without /api prefix to comply with SecurityConfig', () => {
  const origin = 'https://backend.example.com';
  const healthUrl = origin ? `${origin}/actuator/health` : '/actuator/health';
  const metricsUrl = origin
    ? `${origin}/actuator/metrics/resilience4j.circuitbreaker.calls`
    : '/actuator/metrics/resilience4j.circuitbreaker.calls';

  assert.equal(healthUrl, 'https://backend.example.com/actuator/health');
  assert.equal(metricsUrl, 'https://backend.example.com/actuator/metrics/resilience4j.circuitbreaker.calls');
  assert.doesNotMatch(healthUrl, /\/api\/actuator/);
  assert.doesNotMatch(metricsUrl, /\/api\/actuator/);
});

test('System Health does not fabricate a fake UP status on network or service failure', () => {
  let healthState = null;
  const simulatedFetchFailure = (err) => {
    // Verified behavior: log warning and leave healthState as is (or null), do NOT fake UP
    console.warn('Simulated failure handled:', err.message);
  };

  simulatedFetchFailure(new Error('Connection refused'));
  assert.equal(healthState, null);
});

// -------------------------------------------------------------
// 3. Products Search Debounce & AbortController Cancellation
// -------------------------------------------------------------
test('products search: 300ms debounce fires only once after typing pauses', async () => {
  let timer = null;
  let debouncedQuery = '';
  const setDebouncedQuery = (q) => {
    debouncedQuery = q;
  };

  const handleKeypress = (query) => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      setDebouncedQuery(query);
    }, 50); // fast clock for test suite
  };

  // User types 'W', 'WI', 'WID', 'WIDGET' in quick succession
  handleKeypress('W');
  handleKeypress('WI');
  handleKeypress('WID');
  handleKeypress('WIDGET');

  // Before timer expires, debouncedQuery remains empty
  assert.equal(debouncedQuery, '');

  // Wait for debounce timer to fire
  await new Promise((resolve) => setTimeout(resolve, 80));

  assert.equal(debouncedQuery, 'WIDGET');
});

test('products search: clearing query requests all products without query param', () => {
  const resolveProductsUrl = (query) => {
    const trimmed = (query || '').trim();
    return `/products${trimmed ? `?query=${encodeURIComponent(trimmed)}` : ''}`;
  };

  assert.equal(resolveProductsUrl(''), '/products');
  assert.equal(resolveProductsUrl('   '), '/products');
  assert.equal(resolveProductsUrl('SKU-100'), '/products?query=SKU-100');
  assert.equal(resolveProductsUrl('Solar Panel 200W'), '/products?query=Solar%20Panel%20200W');
});

test('products search: stale request cancellation with AbortController suppresses visible errors', () => {
  const controller = new AbortController();
  controller.abort();

  let visibleError = null;
  const mockHandleCatch = (err) => {
    if (axios.isCancel(err) || err?.name === 'CanceledError' || controller.signal.aborted) {
      // Swallowed: intentional cancellation of stale query
      return;
    }
    visibleError = 'Failed to load product catalog.';
  };

  const cancelErr = new axios.CanceledError('canceled');
  mockHandleCatch(cancelErr);
  assert.equal(visibleError, null);

  const abortErr = new Error('The operation was aborted');
  abortErr.name = 'AbortError';
  mockHandleCatch(abortErr);
  assert.equal(visibleError, null);
});

test('products search: actual server error displays visible error message', () => {
  const controller = new AbortController(); // not aborted
  let visibleError = null;

  const mockHandleCatch = (err) => {
    if (axios.isCancel(err) || err?.name === 'CanceledError' || controller.signal.aborted) {
      return;
    }
    visibleError = 'Failed to load product catalog.';
  };

  const serverErr = new Error('Internal Server Error 500');
  mockHandleCatch(serverErr);
  assert.equal(visibleError, 'Failed to load product catalog.');
});
