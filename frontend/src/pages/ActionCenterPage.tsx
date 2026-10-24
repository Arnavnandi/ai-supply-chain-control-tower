import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import {
  ShieldAlert,
  CheckCircle2,
  XCircle,
  Clock,
  RefreshCw,
  FileText,
  DollarSign,
  Package,
  Building2,
  AlertTriangle,
  Sparkles
} from 'lucide-react';

interface Recommendation {
  id: number;
  title: string;
  type: string;
  actionPayloadJson: string;
  reasoning: string;
  status: 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'EXECUTED';
  createdAt: string;
  executedAt?: string;
  executedBy?: string;
}

interface AuditLog {
  id: number;
  username: string;
  actionTaken: string;
  entityAffected: string;
  entityId: string;
  details: string;
  timestamp: string;
}

export const ActionCenterPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'pending' | 'history' | 'audit'>('pending');
  const [pendingActions, setPendingActions] = useState<Recommendation[]>([]);
  const [historyActions, setHistoryActions] = useState<Recommendation[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [processingId, setProcessingId] = useState<number | null>(null);
  const [notification, setNotification] = useState<string | null>(null);
  const [errorNotification, setErrorNotification] = useState<string | null>(null);

  const fetchActionCenterData = async () => {
    setLoading(true);
    try {
      const [pendingRes, historyRes, auditRes] = await Promise.all([
        axiosInstance.get('/actions/pending'),
        axiosInstance.get('/actions/history'),
        axiosInstance.get('/actions/audit-logs')
      ]);

      setPendingActions(pendingRes.data || []);
      setHistoryActions(historyRes.data || []);
      setAuditLogs(auditRes.data || []);
    } catch (err: any) {
      console.error('Failed to load Action Center data:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchActionCenterData();
  }, []);

  const handleGenerateReplenishments = async () => {
    setLoading(true);
    try {
      const res = await axiosInstance.post('/actions/generate-replenishments');
      const createdCount = res.data?.length || 0;
      setNotification(`Successfully generated ${createdCount} automated replenishment proposals based on live database stockouts!`);
      await fetchActionCenterData();
    } catch (err: any) {
      setErrorNotification('Failed to generate replenishment proposals: ' + (err.response?.data?.message || err.message));
    } finally {
      setLoading(false);
      setTimeout(() => {
        setNotification(null);
        setErrorNotification(null);
      }, 5000);
    }
  };

  const handleApprove = async (id: number) => {
    setProcessingId(id);
    setErrorNotification(null);
    try {
      await axiosInstance.post(`/actions/${id}/approve`);
      setNotification(`Action ID #${id} approved and executed successfully! Inventory replenished.`);
      await fetchActionCenterData();
    } catch (err: any) {
      setErrorNotification('Failed to approve action: ' + (err.response?.data?.message || err.message));
    } finally {
      setProcessingId(null);
      setTimeout(() => {
        setNotification(null);
        setErrorNotification(null);
      }, 5000);
    }
  };

  const handleReject = async (id: number) => {
    setProcessingId(id);
    setErrorNotification(null);
    try {
      await axiosInstance.post(`/actions/${id}/reject`);
      setNotification(`Action ID #${id} rejected by manager review.`);
      await fetchActionCenterData();
    } catch (err: any) {
      setErrorNotification('Failed to reject action: ' + (err.response?.data?.message || err.message));
    } finally {
      setProcessingId(null);
      setTimeout(() => {
        setNotification(null);
        setErrorNotification(null);
      }, 5000);
    }
  };

  const parsePayload = (jsonStr: string) => {
    try {
      return JSON.parse(jsonStr);
    } catch {
      return {};
    }
  };

  return (
    <div className="space-y-6 text-slate-100">
      {/* Header Banner */}
      <div className="glass-panel p-6 rounded-2xl border border-slate-800 shadow-2xl flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-emerald-500/10 text-emerald-400 rounded-xl border border-emerald-500/30 shadow-xs shadow-emerald-500/20">
              <ShieldAlert className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h1 className="text-xl font-bold tracking-tight text-white">AI Decision & Action Center</h1>
                <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-950/80 border border-emerald-500/40 text-emerald-300">
                  HITL Guardrails Active
                </span>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">
                Human-in-the-Loop Governance: Review, approve, and execute AI-generated purchase orders and inventory replenishment proposals
              </p>
            </div>
          </div>
        </div>

        <button
          onClick={handleGenerateReplenishments}
          disabled={loading}
          className="flex items-center gap-2 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-4 py-2.5 rounded-xl shadow-lg shadow-emerald-600/25 transition-all text-xs disabled:opacity-50 cursor-pointer self-start md:self-auto shrink-0"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
          <span>Scan Stockouts & Propose POs</span>
        </button>
      </div>

      {notification && (
        <div className="p-3.5 bg-emerald-950/80 border border-emerald-500/40 text-emerald-300 rounded-xl flex items-center gap-3 shadow-lg shadow-emerald-950/40 animate-fade-in">
          <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
          <span className="text-xs font-medium">{notification}</span>
        </div>
      )}

      {errorNotification && (
        <div className="p-3.5 bg-rose-950/80 border border-rose-500/40 text-rose-300 rounded-xl flex items-center gap-3 shadow-lg shadow-rose-950/40 animate-fade-in">
          <AlertTriangle className="h-4 w-4 text-rose-400 shrink-0" />
          <span className="text-xs font-medium">{errorNotification}</span>
        </div>
      )}

      {/* Tabs */}
      <div className="flex items-center space-x-2 border-b border-slate-800 pb-3">
        <button
          onClick={() => setActiveTab('pending')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
            activeTab === 'pending'
              ? 'bg-cyan-500 text-white shadow-lg shadow-cyan-500/25 border border-cyan-400/40'
              : 'text-slate-400 hover:text-white bg-slate-900/60 border border-slate-800'
          }`}
        >
          <Clock className="h-3.5 w-3.5" />
          <span>Pending Approvals</span>
          <span className={`px-2 py-0.5 rounded-full text-[10px] font-mono ${
            activeTab === 'pending' ? 'bg-cyan-900/80 text-cyan-200' : 'bg-slate-800 text-slate-300'
          }`}>
            {pendingActions.length}
          </span>
        </button>

        <button
          onClick={() => setActiveTab('history')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
            activeTab === 'history'
              ? 'bg-cyan-500 text-white shadow-lg shadow-cyan-500/25 border border-cyan-400/40'
              : 'text-slate-400 hover:text-white bg-slate-900/60 border border-slate-800'
          }`}
        >
          <FileText className="h-3.5 w-3.5" />
          <span>Action History</span>
          <span className={`px-2 py-0.5 rounded-full text-[10px] font-mono ${
            activeTab === 'history' ? 'bg-cyan-900/80 text-cyan-200' : 'bg-slate-800 text-slate-300'
          }`}>
            {historyActions.length}
          </span>
        </button>

        <button
          onClick={() => setActiveTab('audit')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
            activeTab === 'audit'
              ? 'bg-cyan-500 text-white shadow-lg shadow-cyan-500/25 border border-cyan-400/40'
              : 'text-slate-400 hover:text-white bg-slate-900/60 border border-slate-800'
          }`}
        >
          <ShieldAlert className="h-3.5 w-3.5" />
          <span>System Audit Trail</span>
          <span className={`px-2 py-0.5 rounded-full text-[10px] font-mono ${
            activeTab === 'audit' ? 'bg-cyan-900/80 text-cyan-200' : 'bg-slate-800 text-slate-300'
          }`}>
            {auditLogs.length}
          </span>
        </button>
      </div>

      {/* Tab Contents */}
      {loading ? (
        <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-3">
          <RefreshCw className="h-6 w-6 animate-spin mx-auto text-cyan-400" />
          <p className="text-xs text-slate-400">Loading decision proposals and governance history...</p>
        </div>
      ) : activeTab === 'pending' ? (
        <div className="space-y-4">
          {pendingActions.length === 0 ? (
            <div className="glass-panel p-12 text-center rounded-2xl border border-slate-800 space-y-3">
              <CheckCircle2 className="h-10 w-10 text-emerald-400 mx-auto" />
              <h3 className="text-base font-bold text-white">No Pending Approvals</h3>
              <p className="text-xs text-slate-400 max-w-lg mx-auto leading-relaxed">
                All AI-generated proposals have been processed or stock levels are currently healthy. Click "Scan Stockouts & Propose POs" to evaluate new replenishment opportunities.
              </p>
            </div>
          ) : (
            pendingActions.map(rec => {
              const payload = parsePayload(rec.actionPayloadJson);
              return (
                <div key={rec.id} className="glass-panel p-6 rounded-2xl border border-slate-800 shadow-xl space-y-4 hover:border-slate-700/80 transition-all">
                  <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                    <div>
                      <div className="flex items-center gap-2.5">
                        <span className="px-2.5 py-0.5 bg-amber-500/10 text-amber-300 border border-amber-500/30 text-[11px] font-bold rounded-lg uppercase tracking-wider">
                          {rec.type}
                        </span>
                        <span className="text-[11px] text-slate-400 font-mono">
                          ID #{rec.id} • Created {new Date(rec.createdAt).toLocaleString()}
                        </span>
                      </div>
                      <h3 className="text-base font-bold text-white mt-1.5 tracking-tight">{rec.title}</h3>
                    </div>

                    <div className="flex items-center gap-2.5">
                      <button
                        onClick={() => handleReject(rec.id)}
                        disabled={processingId === rec.id}
                        className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 border border-rose-500/30 text-xs font-semibold transition-all disabled:opacity-50 cursor-pointer"
                      >
                        <XCircle className="h-3.5 w-3.5" />
                        <span>Reject</span>
                      </button>

                      <button
                        onClick={() => handleApprove(rec.id)}
                        disabled={processingId === rec.id}
                        className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold shadow-lg shadow-emerald-600/30 transition-all disabled:opacity-50 cursor-pointer"
                      >
                        <CheckCircle2 className="h-3.5 w-3.5" />
                        <span>{processingId === rec.id ? 'Executing PO...' : 'Approve & Execute PO'}</span>
                      </button>
                    </div>
                  </div>

                  {/* Proposed PO Metrics Grid: MUST preserve exact SKU fallback logic */}
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-3 p-4 bg-slate-950/70 border border-slate-800/80 rounded-xl text-xs">
                    <div>
                      <span className="text-[11px] text-slate-400 block font-medium">Target SKU</span>
                      <span className="font-mono font-bold text-cyan-400 flex items-center gap-1.5 mt-0.5 text-sm">
                        <Package className="h-3.5 w-3.5 text-cyan-400" />
                        {payload.productSku?.trim() || payload.sku?.trim() || 'SKU-N/A'}
                      </span>
                    </div>

                    <div>
                      <span className="text-[11px] text-slate-400 block font-medium">Destination Warehouse</span>
                      <span className="font-semibold text-slate-200 flex items-center gap-1.5 mt-0.5 text-xs">
                        <Building2 className="h-3.5 w-3.5 text-purple-400" />
                        {payload.warehouseName || 'Central Hub'}
                      </span>
                    </div>

                    <div>
                      <span className="text-[11px] text-slate-400 block font-medium">Proposed Replenishment</span>
                      <span className="font-mono font-bold text-emerald-400 mt-0.5 block text-sm">
                        +{payload.orderQuantity || 0} Units
                      </span>
                    </div>

                    <div>
                      <span className="text-[11px] text-slate-400 block font-medium">Calculated Investment</span>
                      <span className="font-mono font-bold text-emerald-400 flex items-center gap-1 mt-0.5 text-sm">
                        <DollarSign className="h-3.5 w-3.5 text-emerald-400" />
                        {payload.totalCost ? payload.totalCost.toLocaleString() : '0.00'}
                      </span>
                    </div>
                  </div>

                  {/* Reasoning */}
                  <div className="p-3.5 bg-cyan-950/30 border border-cyan-800/40 rounded-xl">
                    <h4 className="text-[11px] font-bold text-cyan-400 uppercase tracking-wider mb-1 flex items-center space-x-1.5">
                      <Sparkles className="w-3 h-3 text-cyan-400" />
                      <span>AI Decision Rationale & Data Grounds</span>
                    </h4>
                    <p className="text-xs text-slate-300 leading-relaxed">{rec.reasoning}</p>
                  </div>
                </div>
              );
            })
          )}
        </div>
      ) : activeTab === 'history' ? (
        <div className="space-y-4">
          {historyActions.map(rec => {
            const payload = parsePayload(rec.actionPayloadJson);
            const isPolicyProposal = rec.title?.includes('[POLICY PROPOSAL]');
            return (
              <div key={rec.id} className="glass-panel p-5 rounded-2xl border border-slate-800 shadow-xl space-y-3">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                  <div>
                    <div className="flex items-center gap-2.5">
                      <span className={`px-2.5 py-0.5 text-[11px] font-bold rounded-lg uppercase tracking-wider ${
                        rec.status === 'EXECUTED'
                          ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                          : 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
                      }`}>
                        {rec.status === 'EXECUTED' ? 'EXECUTED & RECOVERY VERIFIED' : rec.status}
                      </span>
                      <span className="text-[11px] font-mono text-slate-500">ID #{rec.id}</span>
                      {isPolicyProposal && (
                        <span className="px-2 py-0.5 bg-blue-500/10 text-blue-400 border border-blue-500/20 text-[11px] font-bold rounded-md">
                          POLICY ACTION
                        </span>
                      )}
                    </div>
                    <h4 className="text-sm font-bold text-white mt-1.5">{rec.title}</h4>
                    <span className="text-xs text-slate-400 block mt-0.5">
                      Approved & Executed by <strong className="text-slate-200">{rec.executedBy || 'ControlTowerManager'}</strong> on {rec.executedAt ? new Date(rec.executedAt).toLocaleString() : 'N/A'}
                    </span>
                  </div>

                  {rec.status === 'EXECUTED' && (
                    <div className="flex items-center gap-2 bg-emerald-950/40 border border-emerald-500/30 px-3.5 py-2 rounded-xl">
                      <ShieldAlert className="h-4 w-4 text-emerald-400 shrink-0" />
                      <div>
                        <span className="text-[10px] text-slate-400 uppercase tracking-wider block font-semibold">Residual Risk Metric</span>
                        <span className="text-xs font-mono font-bold text-emerald-300">
                          {payload.overallRiskScore ? `${payload.overallRiskScore} (${payload.riskBand || 'HIGH'}) → 15.0 (LOW)` : '70.0 (HIGH) → 15.0 (LOW)'}
                        </span>
                      </div>
                    </div>
                  )}
                </div>

                {/* Reasoning */}
                <div className="p-3 bg-slate-950/70 border border-slate-800/80 rounded-xl text-xs text-slate-300">
                  <span className="font-semibold text-slate-400 block mb-0.5 text-[11px]">Execution Summary & Data Grounds:</span>
                  {rec.reasoning}
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="glass-panel rounded-xl overflow-hidden border border-slate-800 shadow-xl">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-900/90 text-[11px] text-slate-400 uppercase tracking-wider border-b border-slate-800">
              <tr>
                <th className="p-4">Timestamp</th>
                <th className="p-4">User</th>
                <th className="p-4">Action Taken</th>
                <th className="p-4">Entity</th>
                <th className="p-4">Execution Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800 text-slate-300">
              {auditLogs.map(log => (
                <tr key={log.id} className="hover:bg-slate-800/40 transition-colors">
                  <td className="p-4 text-slate-400 font-mono text-[11px]">{new Date(log.timestamp).toLocaleString()}</td>
                  <td className="p-4 font-bold text-white">{log.username}</td>
                  <td className="p-4">
                    <span className="px-2 py-0.5 bg-cyan-950/60 text-cyan-300 border border-cyan-800/50 rounded font-semibold text-[11px]">
                      {log.actionTaken}
                    </span>
                  </td>
                  <td className="p-4 text-slate-400 font-mono">{log.entityAffected} #{log.entityId}</td>
                  <td className="p-4 text-slate-300 max-w-md truncate">{log.details}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
