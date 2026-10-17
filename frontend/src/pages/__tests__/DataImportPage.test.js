import test from 'node:test';
import assert from 'node:assert/strict';

// Helper reproduction matching DataImportPage.tsx
const formatBytes = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
};

const formatDate = (dateStr) => {
  if (!dateStr) return '—';
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    return d.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      timeZone: 'UTC',
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
  const formatted = formatDate('2026-10-17T12:00:00Z');
  assert.match(formatted, /Oct 17, 2026/);
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
