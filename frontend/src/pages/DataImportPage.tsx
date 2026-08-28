import React, { useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { UploadCloud, Database, CheckCircle2, AlertCircle, RefreshCw, FileText } from 'lucide-react';

export const DataImportPage: React.FC = () => {
  const [entityType, setEntityType] = useState('products');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [loadingSample, setLoadingSample] = useState(false);
  const [loadingFile, setLoadingFile] = useState(false);
  const [result, setResult] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);

  const handleSampleImport = async () => {
    setLoadingSample(true);
    setResult(null);
    setError(null);
    try {
      const res = await axiosInstance.post('/data/import/sample-dataset');
      setResult(res.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Sample dataset import failed.');
    } finally {
      setLoadingSample(false);
    }
  };

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
      setError(err.response?.data?.message || 'CSV file upload failed.');
    } finally {
      setLoadingFile(false);
    }
  };

  return (
    <div className="space-y-6 max-w-5xl">
      <div>
        <h2 className="text-xl font-bold text-white">Dataset Ingestion & Data Pipeline</h2>
        <p className="text-xs text-slate-400">
          Import 12-month historical supply-chain datasets or upload custom CSV entity records into PostgreSQL
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Option 1: 1-Click Sample Dataset Import */}
        <div className="glass-panel p-6 rounded-2xl border border-cyan-500/30 bg-slate-900/60 flex flex-col justify-between space-y-4">
          <div>
            <div className="flex items-center space-x-3 mb-3">
              <div className="p-3 rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
                <Database className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-bold text-white">1-Click Sample Dataset</h3>
                <p className="text-xs text-cyan-400 font-medium">12-Month Supply Chain Telemetry</p>
              </div>
            </div>

            <p className="text-xs text-slate-300 leading-relaxed mb-4">
              Imports 9 interconnected synthetic CSV files containing 25 products, 10 global suppliers, 4 logistics hubs, inventory thresholds, 300+ historical customer orders, 800+ order line items, and 250+ shipment tracking records.
            </p>

            <div className="p-3 rounded-lg bg-slate-950/80 border border-slate-800 text-xs text-slate-400 space-y-1 font-mono">
              <p>✔ Connects to DemandForecastingEngine (12-month sales data)</p>
              <p>✔ Triggers low stock & shipment delay risk alerts</p>
              <p>✔ Fully compatible with Spring AI database tools</p>
            </div>
          </div>

          <button
            onClick={handleSampleImport}
            disabled={loadingSample || loadingFile}
            className="w-full py-3 px-4 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-white font-semibold text-sm shadow-lg shadow-cyan-500/20 flex items-center justify-center space-x-2 transition-all disabled:opacity-50"
          >
            {loadingSample ? (
              <>
                <RefreshCw className="w-4 h-4 animate-spin" />
                <span>Ingesting 12-Month Dataset...</span>
              </>
            ) : (
              <>
                <Database className="w-4 h-4" />
                <span>Import Packaged 12-Month Dataset</span>
              </>
            )}
          </button>
        </div>

        {/* Option 2: Upload Custom Entity CSV */}
        <div className="glass-panel p-6 rounded-2xl border border-slate-800 bg-slate-900/60 flex flex-col justify-between space-y-4">
          <div>
            <div className="flex items-center space-x-3 mb-3">
              <div className="p-3 rounded-xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                <UploadCloud className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-bold text-white">Upload Custom Entity CSV</h3>
                <p className="text-xs text-indigo-400 font-medium">Single Entity Import</p>
              </div>
            </div>

            <form onSubmit={handleFileUpload} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">Target Entity</label>
                <select
                  value={entityType}
                  onChange={(e) => setEntityType(e.target.value)}
                  className="w-full px-4 py-2.5 rounded-lg bg-slate-950 border border-slate-800 text-white text-sm focus:outline-none focus:border-cyan-500"
                >
                  <option value="products">Products (SKU Catalog)</option>
                  <option value="suppliers">Suppliers</option>
                  <option value="supplier_products">Supplier Product Contracts</option>
                  <option value="warehouses">Warehouses</option>
                  <option value="inventories">Inventory Stock</option>
                  <option value="orders">Customer Orders</option>
                  <option value="order_items">Order Items</option>
                  <option value="shipments">Shipments & Logistics</option>
                  <option value="categories">Categories</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">Select CSV File</label>
                <div className="relative">
                  <input
                    type="file"
                    accept=".csv"
                    onChange={(e) => setSelectedFile(e.target.files ? e.target.files[0] : null)}
                    className="w-full text-xs text-slate-400 file:mr-4 file:py-2.5 file:px-4 file:rounded-lg file:border-0 file:text-xs file:font-semibold file:bg-slate-800 file:text-cyan-400 hover:file:bg-slate-700 bg-slate-950 border border-slate-800 rounded-lg cursor-pointer"
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={loadingSample || loadingFile || !selectedFile}
                className="w-full py-3 px-4 rounded-xl bg-gradient-to-r from-indigo-500 to-purple-600 hover:from-indigo-400 hover:to-purple-500 text-white font-semibold text-sm shadow-lg shadow-indigo-500/20 flex items-center justify-center space-x-2 transition-all disabled:opacity-50"
              >
                {loadingFile ? (
                  <>
                    <RefreshCw className="w-4 h-4 animate-spin" />
                    <span>Validating & Importing...</span>
                  </>
                ) : (
                  <>
                    <UploadCloud className="w-4 h-4" />
                    <span>Upload & Ingest CSV</span>
                  </>
                )}
              </button>
            </form>
          </div>
        </div>
      </div>

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
    </div>
  );
};
