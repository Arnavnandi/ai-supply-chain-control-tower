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
    switch (severity?.toUpperCase()) {
      case 'CRITICAL':
        return 'bg-rose-500/20 text-rose-300 border-rose-500/40 shadow-sm shadow-rose-950';
      case 'HIGH':
        return 'bg-amber-500/20 text-amber-300 border-amber-500/40 shadow-sm shadow-amber-950';
      case 'MEDIUM':
        return 'bg-yellow-500/20 text-yellow-300 border-yellow-500/30';
      default:
        return 'bg-cyan-500/20 text-cyan-300 border-cyan-500/30';
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fadeIn">
      <div className="relative w-full max-w-2xl bg-[#0c162b] border border-cyan-500/30 rounded-2xl shadow-2xl shadow-black/80 overflow-hidden ring-1 ring-white/10">
        {/* Modal Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-cyan-500/20 bg-[#080f1e]/90">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-cyan-500/15 text-cyan-400 border border-cyan-500/30 shadow-inner">
              <ShieldAlert className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <span className="text-[10px] font-mono text-cyan-400 uppercase tracking-widest">DECISION INTELLIGENCE</span>
                <span className="text-slate-600">•</span>
                <span className="text-[10px] font-mono text-slate-400 uppercase">SPRING AI EXPLAINABILITY</span>
              </div>
              <h3 className="font-bold text-white text-lg tracking-tight">AI Root-Cause Telemetry &amp; Explainability</h3>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800/80 transition-colors cursor-pointer"
            aria-label="Close modal"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 space-y-5 max-h-[80vh] overflow-y-auto">
          {/* Header Info */}
          <div className="flex items-center justify-between flex-wrap gap-2 pb-2 border-b border-slate-800/60">
            <div className="flex items-center space-x-2">
              <span className="text-xs font-mono uppercase tracking-wider text-cyan-300 bg-cyan-950/60 px-3 py-1 rounded-md border border-cyan-500/30">
                {item.category} RISK • {item.id}
              </span>
              <span className="text-xs text-slate-400 font-medium">Status: {item.status}</span>
            </div>
            <span className={`text-xs font-semibold px-3 py-1 rounded-full border ${getSeverityBadge(item.severity)}`}>
              {item.severity} SEVERITY
            </span>
          </div>

          {/* 1. Problem Detected */}
          <div className="p-4 rounded-xl bg-rose-950/20 border border-rose-900/40 space-y-2">
            <div className="flex items-center gap-2 text-rose-400 font-semibold text-xs tracking-wider uppercase">
              <AlertTriangle className="w-4 h-4 shrink-0" />
              <span>1. Problem Detected by Anomaly Radar</span>
            </div>
            <p className="text-slate-200 text-sm leading-relaxed pl-6 font-medium">
              {item.problemDetected}
            </p>
          </div>

          {/* 2. Database Metric Cause */}
          <div className="p-4 rounded-xl bg-cyan-950/20 border border-cyan-900/40 space-y-2">
            <div className="flex items-center gap-2 text-cyan-400 font-semibold text-xs tracking-wider uppercase">
              <Database className="w-4 h-4 shrink-0" />
              <span>2. Underlying PostgreSQL Operational Stream Telemetry</span>
            </div>
            <div className="pl-6">
              <p className="text-cyan-200 text-xs font-mono leading-relaxed bg-[#060c18]/80 p-3 rounded-lg border border-cyan-900/40 shadow-inner">
                {item.dataCause}
              </p>
            </div>
          </div>

          {/* 3. Recommended Action */}
          <div className="p-4 rounded-xl bg-emerald-950/20 border border-emerald-900/40 space-y-2">
            <div className="flex items-center gap-2 text-emerald-400 font-semibold text-xs tracking-wider uppercase">
              <CheckCircle className="w-4 h-4 shrink-0" />
              <span>3. Agentic Mitigation Consensus &amp; Recommendation</span>
            </div>
            <p className="text-slate-200 text-sm leading-relaxed pl-6 font-medium">
              {item.actionRecommended}
            </p>
          </div>
        </div>

        {/* Modal Footer */}
        <div className="px-6 py-3.5 border-t border-cyan-500/10 bg-[#080f1e]/90 flex items-center justify-between text-xs text-slate-400">
          <span className="font-mono text-[11px] text-slate-500">HUMAN-IN-THE-LOOP SAFETY VERIFIED</span>
          <button
            onClick={onClose}
            className="px-4 py-1.5 rounded-lg bg-cyan-500/15 hover:bg-cyan-500/25 text-cyan-300 border border-cyan-500/40 font-semibold transition-colors cursor-pointer"
          >
            Close Telemetry Review
          </button>
        </div>
      </div>
    </div>
  );
};
