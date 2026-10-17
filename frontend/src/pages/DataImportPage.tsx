import React, { useState, useEffect } from 'react';
import axiosInstance from '../api/axiosInstance';
import {
  UploadCloud,
  CheckCircle2,
  AlertCircle,
  RefreshCw,
  FileText,
  ShieldCheck,
  AlertTriangle,
  ChevronDown,
  ChevronRight,
  Clock,
  XCircle,
  FileSpreadsheet,
  User,
  Hash,
} from 'lucide-react';

export interface CsvImportHistoryItem {
  id: number;
  companyId: number;
  userId?: number;
  username?: string;
  originalFilename: string;
  entityType: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'IMPORTED' | 'FAILED' | string;
  totalRows: number;
  importedRows: number;
  failedRows: number;
  validationErrors?: string;
  errorList: string[];
  fileSizeBytes?: number;
  fileHash?: string;
  duplicateFile: boolean;
  startedAt: string;
  completedAt?: string;
}

const ENTITY_LABELS: Record<string, string> = {
  products: 'Products',
  warehouses: 'Warehouses',
  suppliers: 'Suppliers',
  inventories: 'Inventories',
  supplier_products: 'Supplier Contracts',
  orders: 'Customer Orders',
  order_items: 'Order Items',
  shipments: 'Shipments',
  categories: 'Categories',
};

export const DataImportPage: React.FC = () => {
  const [entityType, setEntityType] = useState('products');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [loadingFile, setLoadingFile] = useState(false);
  const [result, setResult] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);

  // History state
  const [history, setHistory] = useState<CsvImportHistoryItem[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [expandedRowId, setExpandedRowId] = useState<number | null>(null);

  const fetchHistory = async () => {
    setLoadingHistory(true);
    setHistoryError(null);
    try {
      const res = await axiosInstance.get<CsvImportHistoryItem[]>('/data/import/history');
      setHistory(res.data);
    } catch (err: any) {
      console.error('Failed to load CSV import history:', err);
      setHistoryError(err.response?.data?.message || 'Failed to load import history.');
    } finally {
      setLoadingHistory(false);
    }
  };

  useEffect(() => {
    fetchHistory();
  }, []);

  const handleFileUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile) {
      setError('Please select a CSV file to upload.');
      return;
    }

    setLoadingFile(true);
    setResult(null);
    setError(null);

    const formData = new FormData();
    formData.append('file', selectedFile);
    formData.append('entityType', entityType);

    try {
      const res = await axiosInstance.post('/data/import/file', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
      setResult(res.data);
    } catch (err: any) {
      if (err.response?.data?.errors || err.response?.data?.totalRowsProcessed !== undefined) {
        setResult(err.response.data);
      } else {
        setError(err.response?.data?.message || 'CSV file upload failed.');
      }
    } finally {
      setLoadingFile(false);
      // Automatically refresh history to reflect the latest ingestion
      fetchHistory();
    }
  };

  const toggleRowExpansion = (id: number) => {
    setExpandedRowId((prev) => (prev === id ? null : id));
  };

  const formatBytes = (bytes?: number) => {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
  };

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return '—';
    try {
      const d = new Date(dateStr);
      return d.toLocaleString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return dateStr;
    }
  };

  const renderStatusBadge = (status: string) => {
    switch (status?.toUpperCase()) {
      case 'IMPORTED':
        return (
          <span className="inline-flex items-center space-x-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-950/80 border border-emerald-500/40 text-emerald-400">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
            <span>IMPORTED</span>
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center space-x-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-rose-950/80 border border-rose-500/40 text-rose-400">
            <XCircle className="w-3.5 h-3.5 text-rose-400" />
            <span>FAILED</span>
          </span>
        );
      case 'IN_PROGRESS':
        return (
          <span className="inline-flex items-center space-x-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-cyan-950/80 border border-cyan-500/40 text-cyan-400 animate-pulse">
            <RefreshCw className="w-3.5 h-3.5 text-cyan-400 animate-spin" />
            <span>IN PROGRESS</span>
          </span>
        );
      case 'PENDING':
      default:
        return (
          <span className="inline-flex items-center space-x-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-950/80 border border-amber-500/40 text-amber-400">
            <Clock className="w-3.5 h-3.5 text-amber-400" />
            <span>PENDING</span>
          </span>
        );
    }
  };

  return (
    <div className="space-y-6 max-w-5xl">
      <div>
        <h2 className="text-xl font-bold text-white">Dataset Ingestion &amp; Data Pipeline</h2>
        <p className="text-xs text-slate-400">
          Upload company operational CSV records with transactional integrity and atomic rollback. All calculations and AI insights are derived exclusively from your persisted data.
        </p>
      </div>

      {/* CSV Ingestion Sequence & Foreign Key Dependency Guide */}
      <div className="glass-panel p-5 rounded-2xl border border-slate-800 space-y-3 bg-slate-900/50">
        <div className="flex items-center justify-between">
          <h3 className="text-xs font-bold text-slate-300 uppercase tracking-wider flex items-center space-x-2">
            <ShieldCheck className="w-4 h-4 text-cyan-400" />
            <span>Recommended CSV Upload Sequence &amp; Dependencies</span>
          </h3>
          <span className="text-[10px] text-cyan-400 font-mono">Atomic Database Transactions</span>
        </div>
        <p className="text-xs text-slate-400 leading-relaxed">
          To satisfy foreign-key dependencies, please upload your entity CSV files in the following order:
        </p>
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3 pt-1 text-xs">
          <div className="p-3 rounded-xl bg-slate-950/80 border border-slate-800 space-y-1">
            <span className="text-[10px] font-bold text-cyan-400 uppercase block">Phase 1: Base Catalogs</span>
            <p className="font-semibold text-white">products.csv</p>
            <p className="font-semibold text-white">warehouses.csv</p>
            <p className="font-semibold text-white">suppliers.csv</p>
            <p className="text-[11px] text-slate-500 pt-1">Defines SKUs, facility codes &amp; vendor profiles.</p>
          </div>

          <div className="p-3 rounded-xl bg-slate-950/80 border border-slate-800 space-y-1">
            <span className="text-[10px] font-bold text-indigo-400 uppercase block">Phase 2: Inventories &amp; Contracts</span>
            <p className="font-semibold text-white">inventories.csv</p>
            <p className="font-semibold text-white">supplier_products.csv</p>
            <p className="text-[11px] text-slate-500 pt-1">Maps SKUs to warehouses and suppliers.</p>
          </div>

          <div className="p-3 rounded-xl bg-slate-950/80 border border-slate-800 space-y-1">
            <span className="text-[10px] font-bold text-purple-400 uppercase block">Phase 3: Orders &amp; Demand</span>
            <p className="font-semibold text-white">orders.csv</p>
            <p className="font-semibold text-white">order_items.csv</p>
            <p className="text-[11px] text-slate-500 pt-1">Sales demand for forecasting &amp; backtesting.</p>
          </div>

          <div className="p-3 rounded-xl bg-slate-950/80 border border-slate-800 space-y-1">
            <span className="text-[10px] font-bold text-emerald-400 uppercase block">Phase 4: Logistics</span>
            <p className="font-semibold text-white">shipments.csv</p>
            <p className="text-[11px] text-slate-500 pt-1">Transit tracking for carrier OTIF &amp; delay analysis.</p>
          </div>
        </div>
      </div>

      {/* Upload Custom Entity CSV - Full Width Interface */}
      <div className="glass-panel p-6 rounded-2xl border border-slate-800 bg-slate-900/60 space-y-5">
        <div className="flex items-center space-x-3 pb-3 border-b border-slate-800/80">
          <div className="p-3 rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
            <UploadCloud className="w-6 h-6" />
          </div>
          <div>
            <h3 className="text-base font-bold text-white">Upload Company Entity CSV</h3>
            <p className="text-xs text-slate-400">
              Select target entity type, choose your CSV file, and ingest into your isolated company workspace
            </p>
          </div>
        </div>

        <form onSubmit={handleFileUpload} className="space-y-5">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                Target Entity Type
              </label>
              <select
                value={entityType}
                onChange={(e) => setEntityType(e.target.value)}
                className="w-full px-4 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs focus:outline-none focus:border-cyan-500"
              >
                <option value="products">1. Products (SKU Catalog: sku, name, price, reorder_level, safety_stock)</option>
                <option value="warehouses">2. Warehouses (Hubs: code, name, location, total_capacity_units)</option>
                <option value="suppliers">3. Suppliers (Vendors: code, name, reliability_score, delivery_performance_pct)</option>
                <option value="inventories">4. Inventories (Stock Levels: product_sku, warehouse_code, quantity_available)</option>
                <option value="supplier_products">5. Supplier Contracts (Contracts: supplier_code, product_sku, contract_price)</option>
                <option value="orders">6. Customer Orders (Orders: order_number, customer_name, order_date, total_amount)</option>
                <option value="order_items">7. Order Items (Lines: order_number, product_sku, quantity, unit_price)</option>
                <option value="shipments">8. Shipments (Logistics: tracking_code, supplier_code, warehouse_code, status)</option>
                <option value="categories">Categories (Product taxonomy)</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                Select CSV File
              </label>
              <input
                type="file"
                accept=".csv"
                onChange={(e) => setSelectedFile(e.target.files ? e.target.files[0] : null)}
                className="w-full text-xs text-slate-400 file:mr-4 file:py-2.5 file:px-4 file:rounded-xl file:border-0 file:text-xs file:font-semibold file:bg-slate-800 file:text-cyan-400 hover:file:bg-slate-700 bg-slate-950 border border-slate-800 rounded-xl cursor-pointer"
              />
            </div>
          </div>

          <div className="flex justify-end pt-2">
            <button
              type="submit"
              disabled={loadingFile || !selectedFile}
              className="py-3 px-6 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-white font-semibold text-xs shadow-lg shadow-cyan-500/20 flex items-center justify-center space-x-2 transition-all disabled:opacity-50 cursor-pointer"
            >
              {loadingFile ? (
                <>
                  <RefreshCw className="w-4 h-4 animate-spin" />
                  <span>Validating &amp; Ingesting CSV...</span>
                </>
              ) : (
                <>
                  <UploadCloud className="w-4 h-4" />
                  <span>Upload &amp; Ingest Records</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>

      {/* Duplicate File Content Warning Banner */}
      {result?.duplicateDetected && (
        <div className="p-4 rounded-xl bg-amber-950/40 border border-amber-500/40 flex items-start space-x-3 text-amber-200 text-xs">
          <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5 text-amber-400" />
          <div className="space-y-1">
            <h4 className="font-bold text-amber-300">Duplicate File Content Detected</h4>
            <p className="text-amber-200/90 leading-relaxed">
              {result.duplicateWarning ||
                'An identical file was previously imported for this tenant. Existing records were updated or retained based on business key upsert rules.'}
            </p>
          </div>
        </div>
      )}

      {/* Error Output */}
      {error && (
        <div className="p-4 rounded-xl bg-rose-950/60 border border-rose-500/40 flex items-start space-x-3 text-rose-300 text-sm">
          <AlertCircle className="w-5 h-5 shrink-0 mt-0.5" />
          <div>
            <h4 className="font-bold text-rose-200">Import Error</h4>
            <p className="text-xs text-rose-300/90 mt-0.5">{error}</p>
          </div>
        </div>
      )}

      {/* Result Status Display */}
      {result && (
        <div className={`glass-panel p-6 rounded-2xl border ${result.success ? 'border-emerald-500/30 bg-emerald-950/20' : 'border-amber-500/30 bg-amber-950/20'} space-y-4`}>
          <div className="flex items-center space-x-3">
            {result.success ? (
              <CheckCircle2 className="w-6 h-6 text-emerald-400 shrink-0" />
            ) : (
              <AlertCircle className="w-6 h-6 text-amber-400 shrink-0" />
            )}
            <div>
              <h3 className={`text-base font-bold ${result.success ? 'text-emerald-200' : 'text-amber-200'}`}>
                {result.message}
              </h3>
              <p className="text-xs text-slate-400 font-mono">Entity Type: {result.entityType}</p>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4 text-center text-xs">
            <div className="p-3 rounded-lg bg-slate-900/80 border border-slate-800">
              <span className="text-slate-400 block uppercase">Rows Processed</span>
              <span className="text-lg font-bold text-white mt-1 block">{result.totalRowsProcessed}</span>
            </div>
            <div className="p-3 rounded-lg bg-slate-900/80 border border-slate-800">
              <span className="text-slate-400 block uppercase">Imported</span>
              <span className="text-lg font-bold text-emerald-400 mt-1 block">{result.recordsImported}</span>
            </div>
            <div className="p-3 rounded-lg bg-slate-900/80 border border-slate-800">
              <span className="text-slate-400 block uppercase">Failed/Errors</span>
              <span className={`text-lg font-bold ${result.recordsFailed > 0 ? 'text-rose-400' : 'text-slate-400'} mt-1 block`}>
                {result.recordsFailed}
              </span>
            </div>
          </div>

          {result.errors && result.errors.length > 0 && (
            <div className="mt-4 p-4 rounded-xl bg-slate-950/90 border border-slate-800 space-y-2 max-h-48 overflow-y-auto">
              <div className="flex items-center space-x-2 text-xs font-bold text-rose-400">
                <FileText className="w-4 h-4" />
                <span>Row Validation Errors ({result.errors.length}):</span>
              </div>
              <ul className="space-y-1 font-mono text-xs text-rose-300/80">
                {result.errors.map((errStr: string, idx: number) => (
                  <li key={idx} className="border-b border-slate-900 pb-1">{errStr}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {/* Tenant CSV Ingestion History Section */}
      <div className="glass-panel p-6 rounded-2xl border border-slate-800 bg-slate-900/60 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-800/80">
          <div className="flex items-center space-x-3">
            <div className="p-2.5 rounded-xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
              <FileSpreadsheet className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h3 className="text-base font-bold text-white">Tenant CSV Import History</h3>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-slate-800 text-slate-300 border border-slate-700">
                  {history.length} {history.length === 1 ? 'Import' : 'Imports'}
                </span>
              </div>
              <p className="text-xs text-slate-400">
                Audit trail of tenant-scoped CSV dataset uploads, processing statuses, row counts, and error summaries
              </p>
            </div>
          </div>

          <button
            onClick={fetchHistory}
            disabled={loadingHistory}
            className="px-3 py-1.5 rounded-xl bg-slate-950 border border-slate-800 hover:border-slate-700 text-xs text-slate-300 hover:text-white flex items-center space-x-1.5 transition-colors disabled:opacity-50 cursor-pointer self-start sm:self-auto"
            title="Refresh history"
          >
            <RefreshCw className={`w-3.5 h-3.5 text-cyan-400 ${loadingHistory ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </button>
        </div>

        {historyError && (
          <div className="p-3 rounded-xl bg-rose-950/60 border border-rose-500/40 text-xs text-rose-300 flex items-center space-x-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{historyError}</span>
          </div>
        )}

        {loadingHistory && history.length === 0 ? (
          <div className="py-12 flex flex-col items-center justify-center space-y-3 text-slate-400 text-xs">
            <RefreshCw className="w-6 h-6 animate-spin text-cyan-400" />
            <span>Loading import history...</span>
          </div>
        ) : history.length === 0 ? (
          <div className="py-12 flex flex-col items-center justify-center space-y-2 text-center">
            <FileSpreadsheet className="w-10 h-10 text-slate-600 mb-1" />
            <p className="text-sm font-semibold text-slate-300">No Import History Found</p>
            <p className="text-xs text-slate-500 max-w-sm">
              No CSV datasets have been uploaded to this company workspace yet. Use the upload form above to ingest your operational records.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-slate-800">
            <table className="w-full text-left text-xs text-slate-300">
              <thead className="bg-slate-950/90 text-[11px] font-semibold uppercase tracking-wider text-slate-400 border-b border-slate-800">
                <tr>
                  <th className="px-4 py-3.5">Original File &amp; Hash</th>
                  <th className="px-4 py-3.5">Entity</th>
                  <th className="px-4 py-3.5">Status</th>
                  <th className="px-4 py-3.5 text-center">Processed</th>
                  <th className="px-4 py-3.5 text-center">Imported</th>
                  <th className="px-4 py-3.5 text-center">Failed</th>
                  <th className="px-4 py-3.5">Uploaded By</th>
                  <th className="px-4 py-3.5">Timestamp</th>
                  <th className="px-4 py-3.5 text-right">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 bg-slate-900/40">
                {history.map((item) => {
                  const isExpanded = expandedRowId === item.id;
                  const hasErrors =
                    (item.errorList && item.errorList.length > 0) ||
                    (item.validationErrors && item.validationErrors.trim().length > 0) ||
                    item.failedRows > 0 ||
                    item.status === 'FAILED';

                  return (
                    <React.Fragment key={item.id}>
                      <tr className="hover:bg-slate-800/40 transition-colors">
                        {/* Filename & Hash */}
                        <td className="px-4 py-3.5">
                          <div className="space-y-1">
                            <div className="flex items-center space-x-1.5 flex-wrap">
                              <span className="font-semibold text-white">{item.originalFilename}</span>
                              {item.duplicateFile && (
                                <span
                                  className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-semibold bg-amber-950/80 border border-amber-500/40 text-amber-400"
                                  title="Identical file content SHA-256 was previously imported"
                                >
                                  Duplicate File
                                </span>
                              )}
                            </div>
                            <div className="flex items-center space-x-2 text-[11px] text-slate-500 font-mono">
                              <span>{formatBytes(item.fileSizeBytes)}</span>
                              {item.fileHash && (
                                <span title={`SHA-256: ${item.fileHash}`}>
                                  • hash:{item.fileHash.substring(0, 8)}...
                                </span>
                              )}
                            </div>
                          </div>
                        </td>

                        {/* Entity */}
                        <td className="px-4 py-3.5">
                          <span className="px-2 py-1 rounded-md text-[11px] font-medium bg-slate-800/80 text-cyan-300 border border-slate-700">
                            {ENTITY_LABELS[item.entityType] || item.entityType}
                          </span>
                        </td>

                        {/* Status */}
                        <td className="px-4 py-3.5 whitespace-nowrap">
                          {renderStatusBadge(item.status)}
                        </td>

                        {/* Total Rows */}
                        <td className="px-4 py-3.5 text-center font-semibold text-white">
                          {item.totalRows}
                        </td>

                        {/* Imported Rows */}
                        <td className="px-4 py-3.5 text-center font-bold text-emerald-400">
                          {item.importedRows}
                        </td>

                        {/* Failed Rows */}
                        <td className="px-4 py-3.5 text-center font-bold">
                          <span className={item.failedRows > 0 ? 'text-rose-400' : 'text-slate-500'}>
                            {item.failedRows}
                          </span>
                        </td>

                        {/* Uploader */}
                        <td className="px-4 py-3.5">
                          <div className="flex items-center space-x-1.5 text-slate-300">
                            <User className="w-3.5 h-3.5 text-slate-500" />
                            <span className="truncate max-w-[110px]" title={item.username || 'System'}>
                              {item.username || 'System'}
                            </span>
                          </div>
                        </td>

                        {/* Timestamp */}
                        <td className="px-4 py-3.5 whitespace-nowrap text-slate-400 text-[11px]">
                          <div className="flex items-center space-x-1">
                            <Clock className="w-3 h-3 text-slate-500" />
                            <span>{formatDate(item.startedAt)}</span>
                          </div>
                        </td>

                        {/* Details / Error Toggle */}
                        <td className="px-4 py-3.5 text-right whitespace-nowrap">
                          {hasErrors ? (
                            <button
                              onClick={() => toggleRowExpansion(item.id)}
                              className="inline-flex items-center space-x-1 px-2.5 py-1 rounded-lg bg-rose-950/40 hover:bg-rose-950/80 border border-rose-500/30 text-rose-300 text-[11px] font-medium transition-colors cursor-pointer"
                            >
                              <span>Errors</span>
                              {isExpanded ? (
                                <ChevronDown className="w-3 h-3" />
                              ) : (
                                <ChevronRight className="w-3 h-3" />
                              )}
                            </button>
                          ) : (
                            <span className="text-slate-600 text-[11px]">—</span>
                          )}
                        </td>
                      </tr>

                      {/* Expandable Error Details Accordion */}
                      {isExpanded && (
                        <tr className="bg-slate-950/70">
                          <td colSpan={9} className="px-6 py-4 border-t border-slate-800/80 space-y-3">
                            <div className="flex items-center justify-between text-xs">
                              <span className="font-bold text-rose-400 flex items-center space-x-1.5">
                                <AlertCircle className="w-4 h-4" />
                                <span>Validation Error Details &amp; Diagnostics</span>
                              </span>
                              <div className="flex items-center space-x-2 text-[11px] text-slate-500 font-mono">
                                <Hash className="w-3 h-3" />
                                <span>SHA-256: {item.fileHash || 'N/A'}</span>
                              </div>
                            </div>

                            {item.errorList && item.errorList.length > 0 ? (
                              <ul className="space-y-1 font-mono text-xs text-rose-300/90 bg-rose-950/20 p-3 rounded-lg border border-rose-900/40 max-h-40 overflow-y-auto">
                                {item.errorList.map((err, idx) => (
                                  <li key={idx} className="border-b border-rose-950/60 pb-1 last:border-0 last:pb-0">
                                    {err}
                                  </li>
                                ))}
                              </ul>
                            ) : item.validationErrors ? (
                              <div className="font-mono text-xs text-rose-300/90 bg-rose-950/20 p-3 rounded-lg border border-rose-900/40 whitespace-pre-wrap">
                                {item.validationErrors}
                              </div>
                            ) : (
                              <p className="text-xs text-slate-400 italic">No specific error strings recorded.</p>
                            )}
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default DataImportPage;
