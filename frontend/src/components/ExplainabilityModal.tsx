import React from 'react';
import { AlertTriangle, Database, CheckCircle, X, ShieldAlert } from 'lucide-react';

export interface ExplainableRiskItem {
  id: string;
  category: string;
  title: string;
  severity: string;
  problemDetected: string;
  dataCause: string;
  actionRecommended: string;
  status: string;
}

interface ExplainabilityModalProps {
  item: ExplainableRiskItem | null;
  onClose: () => void;
}

export const ExplainabilityModal: React.FC<ExplainabilityModalProps> = ({ item, onClose }) => {
  if (!item) return null;

  const getSeverityBadge = (severity: string) => {
    switch (severity) {
      case 'CRITICAL':
        return 'bg-red-500/20 text-red-400 border-red-500/30';
      case 'HIGH':
        return 'bg-amber-500/20 text-amber-400 border-amber-500/30';
      case 'MEDIUM':
        return 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30';
      default:
        return 'bg-blue-500/20 text-blue-400 border-blue-500/30';
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
      <div className="relative w-full max-w-2xl bg-slate-900 border border-slate-700/80 rounded-2xl shadow-2xl overflow-hidden">
        {/* Modal Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-800 bg-slate-950/60">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
              <ShieldAlert className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-semibold text-slate-100 text-lg">AI Recommendation Explainability</h3>
              <p className="text-xs text-slate-400">Root Cause Telemetry & Recommended Mitigation</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 space-y-5 max-h-[80vh] overflow-y-auto">
          {/* Header Info */}
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono uppercase tracking-wider text-slate-400 bg-slate-800/80 px-2.5 py-1 rounded border border-slate-700">
              {item.category} RISK • {item.id}
            </span>
            <span className={`text-xs font-semibold px-3 py-1 rounded-full border ${getSeverityBadge(item.severity)}`}>
              {item.severity} SEVERITY
            </span>
          </div>

          {/* 1. Problem Detected */}
          <div className="p-4 rounded-xl bg-red-950/20 border border-red-900/30 space-y-1.5">
            <div className="flex items-center gap-2 text-red-400 font-medium text-sm">
              <AlertTriangle className="w-4 h-4 shrink-0" />
              <span>1. Problem Detected</span>
            </div>
            <p className="text-slate-200 text-sm pl-6 leading-relaxed">
              {item.problemDetected}
            </p>
          </div>

          {/* 2. Database Metric Cause */}
          <div className="p-4 rounded-xl bg-blue-950/20 border border-blue-900/30 space-y-1.5">
            <div className="flex items-center gap-2 text-blue-400 font-medium text-sm">
              <Database className="w-4 h-4 shrink-0" />
              <span>2. Underlying Database Metrics Cause</span>
            </div>
            <p className="text-slate-200 text-sm pl-6 font-mono text-xs leading-relaxed bg-slate-950/40 p-3 rounded-lg border border-slate-800 text-blue-200">
              {item.dataCause}
            </p>
          </div>

          {/* 3. Recommended Action */}
          <div className="p-4 rounded-xl bg-emerald-950/20 border border-emerald-900/30 space-y-1.5">
            <div className="flex items-center gap-2 text-emerald-400 font-medium text-sm">
              <CheckCircle className="w-4 h-4 shrink-0" />
              <span>3. Recommended Mitigation Action</span>
            </div>
            <p className="text-slate-200 text-sm pl-6 leading-relaxed">
              {item.actionRecommended}
            </p>
          </div>
        </div>

        {/* Modal Footer */}
        <div className="px-6 py-4 border-t border-slate-800 bg-slate-950/60 flex justify-end">
          <button
            onClick={onClose}
            className="px-5 py-2 text-sm font-medium text-slate-200 bg-indigo-600 hover:bg-indigo-500 rounded-xl transition-all shadow-lg shadow-indigo-600/20"
          >
            Close Analysis
          </button>
        </div>
      </div>
    </div>
  );
};
