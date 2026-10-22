import test from 'node:test';
import assert from 'node:assert/strict';
import axiosInstance from '../../api/axiosInstance.ts';
import { classifyApiError } from '../../utils/errorClassification.ts';

test('axiosInstance preserves the configured 15-second timeout', () => {
  assert.equal(axiosInstance.defaults.timeout, 15000);
});

test('classifyApiError detects ECONNABORTED timeout and provides cold-start guidance', () => {
  const timeoutErr = {
    code: 'ECONNABORTED',
    message: 'timeout of 15000ms exceeded',
  };
  const result = classifyApiError(timeoutErr, 'Failed to load data.');
  assert.equal(result.isTimeout, true);
  assert.equal(result.isAuthError, false);
  assert.match(result.message, /Request timed out after 15s/);
  assert.match(result.message, /cold start/);
  assert.match(result.message, /Retry/);
});

test('classifyApiError detects regex timeout message and HTTP 504 Gateway Timeout', () => {
  const msgErr = { message: 'Connection timeout after 15000ms' };
  const resMsg = classifyApiError(msgErr);
  assert.equal(resMsg.isTimeout, true);
  assert.equal(resMsg.isAuthError, false);
  assert.match(resMsg.message, /cold start/);

  const res504 = classifyApiError({ response: { status: 504 } });
  assert.equal(res504.isTimeout, true);
  assert.equal(res504.isAuthError, false);
  assert.match(res504.message, /cold start/);
});

test('classifyApiError strictly separates 401 Authentication errors from timeouts', () => {
  const authErr401 = {
    response: {
      status: 401,
      data: { message: 'Session token has expired' },
    },
  };
  const result = classifyApiError(authErr401);
  assert.equal(result.isTimeout, false);
  assert.equal(result.isAuthError, true);
  assert.equal(result.message, 'Session token has expired');
  assert.doesNotMatch(result.message, /cold start/i);
  assert.doesNotMatch(result.message, /timed out/i);

  const default401 = classifyApiError({ response: { status: 401 } });
  assert.equal(default401.isTimeout, false);
  assert.equal(default401.isAuthError, true);
  assert.match(default401.message, /Authentication session expired/);
});

test('classifyApiError strictly separates 403 Authorization errors from timeouts', () => {
  const authErr403 = {
    response: {
      status: 403,
      data: { message: 'Tenant access forbidden' },
    },
  };
  const result = classifyApiError(authErr403);
  assert.equal(result.isTimeout, false);
  assert.equal(result.isAuthError, true);
  assert.equal(result.message, 'Tenant access forbidden');
  assert.doesNotMatch(result.message, /cold start/i);
  assert.doesNotMatch(result.message, /timed out/i);

  const default403 = classifyApiError({ response: { status: 403 } });
  assert.equal(default403.isTimeout, false);
  assert.equal(default403.isAuthError, true);
  assert.match(default403.message, /Access denied/);
});

test('classifyApiError uses customized fallback messages across entities when no server message exists', () => {
  const entities = [
    { fallback: 'Failed to load inventory stock records.', label: 'inventory' },
    { fallback: 'Failed to load warehouse distribution centers.', label: 'warehouses' },
    { fallback: 'Failed to load supplier records and performance metrics.', label: 'suppliers' },
    { fallback: 'Failed to load shipment logistics.', label: 'shipments' },
    { fallback: 'Failed to load operational risk alerts.', label: 'risks' },
    { fallback: 'Failed to load product catalog.', label: 'products' },
  ];

  for (const { fallback } of entities) {
    const genericErr = {};
    const classified = classifyApiError(genericErr, fallback);
    assert.equal(classified.isTimeout, false);
    assert.equal(classified.isAuthError, false);
    assert.equal(classified.message, fallback);
  }
});

// Helper simulating the rendering state machine across pages
const computeViewMode = ({ loading, error, recordCount }) => {
  if (loading) return 'LOADING';
  if (error) return 'ERROR';
  if (recordCount === 0) return 'EMPTY';
  return 'DATA';
};

test('view state machine prevents premature empty state on initial mount', () => {
  // Scenario: Page initializes with loading: true, error: null, recordCount: 0
  const initialMountState = { loading: true, error: null, recordCount: 0 };
  assert.equal(computeViewMode(initialMountState), 'LOADING');
  assert.notEqual(computeViewMode(initialMountState), 'EMPTY');
});

test('view state machine never silently renders empty state on API error or timeout', () => {
  // Scenario: Request fails or times out
  const errorState = {
    loading: false,
    error: 'Request timed out after 15s. The backend service may be spinning up from idle (cold start).',
    recordCount: 0,
  };
  assert.equal(computeViewMode(errorState), 'ERROR');
  assert.notEqual(computeViewMode(errorState), 'EMPTY');
});

test('view state machine renders empty state ONLY after successful response confirms 0 records', () => {
  // Scenario: Successful API response with 0 records
  const confirmedEmptyState = {
    loading: false,
    error: null,
    recordCount: 0,
  };
  assert.equal(computeViewMode(confirmedEmptyState), 'EMPTY');
});

test('view state machine renders data when records exist', () => {
  // Scenario: Successful API response with records
  const populatedState = {
    loading: false,
    error: null,
    recordCount: 5,
  };
  assert.equal(computeViewMode(populatedState), 'DATA');
});
