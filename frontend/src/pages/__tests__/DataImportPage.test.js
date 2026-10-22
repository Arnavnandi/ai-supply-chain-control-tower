import test from 'node:test';
import assert from 'node:assert/strict';
import axiosInstance from '../../api/axiosInstance.ts';

// Helper reproduction matching DataImportPage.tsx
const classifyHistoryError = (err) => {
  const isTimeout =
    err?.code === 'ECONNABORTED' ||
    Boolean(err?.message && /timeout/i.test(err.message)) ||
    err?.response?.status === 504;

  const status = err?.response?.status;

  if (isTimeout) {
    return {
      isTimeout: true,
      isAuthError: false,
      message: 'Request timed out after 15s. The backend service may be spinning up from idle (cold start). Please click Retry in a few moments.',
    };
  }

  if (status === 401) {
    return {
      isTimeout: false,
      isAuthError: true,
      message: err?.response?.data?.message || 'Authentication session expired. Please sign in again.',
    };
  }

  if (status === 403) {
    return {
      isTimeout: false,
      isAuthError: true,
      message: err?.response?.data?.message || 'Access denied. You do not have permission to view import history.',
    };
  }

  return {
    isTimeout: false,
    isAuthError: false,
    message: err?.response?.data?.message || err?.message || 'Failed to load import history.',
  };
};

const formatBytes = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
};

const formatDate = (dateStr, timeZone) => {
  if (!dateStr) return '—';
  try {
    const trimmed = dateStr.trim();
    const hasTimezone = /[zZ]$|[+-]\d{2}(?::?\d{2})?$/.test(trimmed);
    const normalizedStr = hasTimezone ? trimmed : `${trimmed}Z`;
    const d = new Date(normalizedStr);
    if (isNaN(d.getTime())) return dateStr;
    return d.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      ...(timeZone ? { timeZone } : {}),
    });
  } catch {
    return dateStr;
  }
};

const getStatusBadgeVariant = (status) => {
  switch (status?.toUpperCase()) {
    case 'IMPORTED':
      return { variant: 'success', label: 'IMPORTED', color: 'emerald' };
    case 'FAILED':
      return { variant: 'danger', label: 'FAILED', color: 'rose' };
    case 'IN_PROGRESS':
      return { variant: 'loading', label: 'IN PROGRESS', color: 'cyan' };
    case 'PENDING':
    default:
      return { variant: 'pending', label: 'PENDING', color: 'amber' };
  }
};

test('formatBytes formats bytes correctly across magnitudes', () => {
  assert.equal(formatBytes(0), '0 B');
  assert.equal(formatBytes(undefined), '0 B');
  assert.equal(formatBytes(512), '512 B');
  assert.equal(formatBytes(1024), '1 KB');
  assert.equal(formatBytes(2048), '2 KB');
  assert.equal(formatBytes(1536), '1.5 KB');
  assert.equal(formatBytes(1048576), '1 MB');
});

test('formatDate safely formats ISO date strings and handles empty values', () => {
  assert.equal(formatDate(undefined), '—');
  assert.equal(formatDate(''), '—');
  const formatted = formatDate('2026-10-17T12:00:00Z', 'UTC');
  assert.match(formatted, /Oct 17, 2026/);
});

test('formatDate normalizes timezone-naive UTC timestamps so they convert correctly to local time', () => {
  // Staging database produces timezone-naive UTC timestamp: '2026-10-21T13:43:08.537906'
  const naiveUtc = '2026-10-21T13:43:08.537906';
  const explicitZ = '2026-10-21T13:43:08.537906Z';

  // In Asia/Kolkata (IST, UTC+05:30), 13:43 UTC must display as 19:13 (7:13 PM)
  const formattedIst = formatDate(naiveUtc, 'Asia/Kolkata');
  assert.match(formattedIst, /Oct 21, 2026/);
  assert.match(formattedIst, /07:13 PM/);

  // Both naive UTC and explicit Z must produce identical formatted output in IST
  assert.equal(formattedIst, formatDate(explicitZ, 'Asia/Kolkata'));

  // In UTC, both should display 01:43 PM
  assert.match(formatDate(naiveUtc, 'UTC'), /01:43 PM/);
  assert.match(formatDate(explicitZ, 'UTC'), /01:43 PM/);
});

test('formatDate preserves timestamps that already have Z or an explicit timezone offset', () => {
  // Trailing Z
  const withZ = '2026-10-21T13:43:08Z';
  assert.match(formatDate(withZ, 'UTC'), /01:43 PM/);

  // Explicit positive offset (+05:30)
  const withPositiveOffset = '2026-10-21T19:13:08+05:30';
  // 19:13 at +05:30 corresponds to 13:43 UTC
  assert.match(formatDate(withPositiveOffset, 'UTC'), /01:43 PM/);
  assert.match(formatDate(withPositiveOffset, 'Asia/Kolkata'), /07:13 PM/);

  // Explicit negative offset (-04:00)
  const withNegativeOffset = '2026-10-21T09:43:08-04:00';
  // 09:43 at -04:00 corresponds to 13:43 UTC
  assert.match(formatDate(withNegativeOffset, 'UTC'), /01:43 PM/);

  // Compact offset (+0530)
  const withCompactOffset = '2026-10-21T19:13:08+0530';
  assert.match(formatDate(withCompactOffset, 'UTC'), /01:43 PM/);

  // Invalid date string safely returns original string
  assert.equal(formatDate('invalid-date-string'), 'invalid-date-string');
});

test('getStatusBadgeVariant returns correct metadata for each import status', () => {
  assert.deepEqual(getStatusBadgeVariant('IMPORTED'), {
    variant: 'success',
    label: 'IMPORTED',
    color: 'emerald',
  });
  assert.deepEqual(getStatusBadgeVariant('FAILED'), {
    variant: 'danger',
    label: 'FAILED',
    color: 'rose',
  });
  assert.deepEqual(getStatusBadgeVariant('IN_PROGRESS'), {
    variant: 'loading',
    label: 'IN PROGRESS',
    color: 'cyan',
  });
  assert.deepEqual(getStatusBadgeVariant('PENDING'), {
    variant: 'pending',
    label: 'PENDING',
    color: 'amber',
  });
});

test('duplicate file warning and error display condition', () => {
  const duplicateResult = {
    success: true,
    duplicateDetected: true,
    duplicateWarning: 'Duplicate file warning: An identical CSV file was previously imported for this tenant.',
    totalRowsProcessed: 10,
    recordsImported: 10,
    recordsFailed: 0,
  };

  assert.equal(duplicateResult.duplicateDetected, true);
  assert.match(duplicateResult.duplicateWarning, /identical CSV file/);

  const failureResult = {
    success: false,
    duplicateDetected: false,
    errors: ['Row 2: SKU already exists', 'Row 5: Invalid price format'],
    recordsFailed: 2,
    recordsImported: 0,
  };

  assert.equal(failureResult.duplicateDetected, false);
  assert.equal(failureResult.recordsFailed, 2);
  assert.equal(failureResult.errors.length, 2);
});

test('history items distinguish duplicate uploads and preserve tenant row counts', () => {
  const historyItems = [
    {
      id: 2,
      originalFilename: 'products_v2.csv',
      entityType: 'products',
      status: 'IMPORTED',
      totalRows: 15,
      importedRows: 15,
      failedRows: 0,
      fileHash: 'abcdef123456',
      duplicateFile: true,
    },
    {
      id: 1,
      originalFilename: 'products.csv',
      entityType: 'products',
      status: 'IMPORTED',
      totalRows: 15,
      importedRows: 15,
      failedRows: 0,
      fileHash: 'abcdef123456',
      duplicateFile: false,
    },
  ];

  assert.equal(historyItems[0].duplicateFile, true);
  assert.equal(historyItems[1].duplicateFile, false);
  assert.equal(historyItems[0].fileHash, historyItems[1].fileHash);
  assert.equal(historyItems[0].importedRows, 15);
});

test('axiosInstance has a default 15-second timeout configured', () => {
  assert.equal(axiosInstance.defaults.timeout, 15000);
});

test('classifyHistoryError detects Axios timeout (ECONNABORTED) and returns cold start guidance', () => {
  const timeoutErr = {
    code: 'ECONNABORTED',
    message: 'timeout of 15000ms exceeded',
  };
  const result = classifyHistoryError(timeoutErr);
  assert.equal(result.isTimeout, true);
  assert.equal(result.isAuthError, false);
  assert.match(result.message, /Request timed out after 15s/);
  assert.match(result.message, /cold start/);
  assert.match(result.message, /Retry/);
});

test('classifyHistoryError detects message-based timeout and HTTP 504 Gateway Timeout', () => {
  const msgErr = { message: 'Network timeout occurred' };
  const resMsg = classifyHistoryError(msgErr);
  assert.equal(resMsg.isTimeout, true);
  assert.equal(resMsg.isAuthError, false);

  const gatewayTimeoutErr = { response: { status: 504 } };
  const res504 = classifyHistoryError(gatewayTimeoutErr);
  assert.equal(res504.isTimeout, true);
  assert.equal(res504.isAuthError, false);
});

test('classifyHistoryError strictly distinguishes HTTP 401 Authentication errors from timeouts', () => {
  const authErr401 = {
    response: {
      status: 401,
      data: { message: 'Token expired' },
    },
  };
  const result = classifyHistoryError(authErr401);
  assert.equal(result.isTimeout, false);
  assert.equal(result.isAuthError, true);
  assert.equal(result.message, 'Token expired');
  // Must NOT suggest cold start or timeout
  assert.doesNotMatch(result.message, /cold start/i);
  assert.doesNotMatch(result.message, /timed out/i);

  const fallback401 = { response: { status: 401 } };
  const resFallback = classifyHistoryError(fallback401);
  assert.equal(resFallback.isTimeout, false);
  assert.equal(resFallback.isAuthError, true);
  assert.match(resFallback.message, /Authentication session expired/);
});

test('classifyHistoryError strictly distinguishes HTTP 403 Authorization errors from timeouts', () => {
  const authErr403 = {
    response: {
      status: 403,
      data: { error: 'Forbidden', message: 'Access denied' },
    },
  };
  const result = classifyHistoryError(authErr403);
  assert.equal(result.isTimeout, false);
  assert.equal(result.isAuthError, true);
  assert.equal(result.message, 'Access denied');
  assert.doesNotMatch(result.message, /cold start/i);
  assert.doesNotMatch(result.message, /timed out/i);

  const fallback403 = { response: { status: 403 } };
  const resFallback = classifyHistoryError(fallback403);
  assert.equal(resFallback.isTimeout, false);
  assert.equal(resFallback.isAuthError, true);
  assert.match(resFallback.message, /Access denied/);
});

test('classifyHistoryError handles HTTP 500 server errors and generic network failures', () => {
  const serverErr = {
    response: {
      status: 500,
      data: { message: 'Internal database query failure' },
    },
  };
  const res500 = classifyHistoryError(serverErr);
  assert.equal(res500.isTimeout, false);
  assert.equal(res500.isAuthError, false);
  assert.equal(res500.message, 'Internal database query failure');

  const genericNetworkErr = {
    message: 'Network Error',
  };
  const resGeneric = classifyHistoryError(genericNetworkErr);
  assert.equal(resGeneric.isTimeout, false);
  assert.equal(resGeneric.isAuthError, false);
  assert.equal(resGeneric.message, 'Network Error');
});

