import React, { useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { UploadCloud, CheckCircle2, AlertCircle, RefreshCw, FileText, ShieldCheck } from 'lucide-react';

export const DataImportPage: React.FC = () => {
  const [entityType, setEntityType] = useState('products');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [loadingFile, setLoadingFile] = useState(false);
  const [result, setResult] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);

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

export default DataImportPage;
