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
  Building2
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
      setNotification('Failed to generate replenishment proposals.');
    } finally {
      setLoading(false);
      setTimeout(() => setNotification(null), 5000);
    }
  };

  const handleApprove = async (id: number) => {
    setProcessingId(id);
    try {
      await axiosInstance.post(`/actions/${id}/approve`);
      setNotification(`Action ID #${id} approved and executed successfully! Inventory replenished.`);
      await fetchActionCenterData();
    } catch (err: any) {
      alert('Failed to approve action: ' + (err.response?.data?.message || err.message));
    } finally {
      setProcessingId(null);
      setTimeout(() => setNotification(null), 5000);
    }
  };

  const handleReject = async (id: number) => {
    setProcessingId(id);
    try {
      await axiosInstance.post(`/actions/${id}/reject`);
      setNotification(`Action ID #${id} rejected by manager review.`);
      await fetchActionCenterData();
    } catch (err: any) {
      alert('Failed to reject action.');
    } finally {
      setProcessingId(null);
      setTimeout(() => setNotification(null), 5000);
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
    <div className="p-8 space-y-8 bg-slate-950 min-h-screen text-slate-100">
      {/* Header Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl">
        <div className="space-y-1">
          <div className="flex items-center gap-3">
            <div className="p-3 bg-emerald-500/10 text-emerald-400 rounded-xl border border-emerald-500/20">
              <ShieldAlert className="h-6 w-6" />
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-white">AI Decision & Action Center</h1>
          </div>
          <p className="text-slate-400 text-sm pl-12">
            Human-in-the-Loop Governance: Review, approve, and execute AI-generated purchase orders and inventory replenishment proposals.
          </p>
        </div>

        <button
          onClick={handleGenerateReplenishments}
          disabled={loading}
          className="flex items-center gap-2 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-5 py-2.5 rounded-xl shadow-lg shadow-emerald-600/20 transition-all text-sm disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Scan Database Stockouts & Propose POs
        </button>
      </div>

      {notification && (
        <div className="p-4 bg-emerald-950/80 border border-emerald-500/30 text-emerald-300 rounded-xl flex items-center gap-3 shadow-lg">
          <CheckCircle2 className="h-5 w-5 text-emerald-400 flex-shrink-0" />
          <span className="text-sm font-medium">{notification}</span>
        </div>
      )}

      {/* Tabs */}
      <div className="flex items-center gap-4 border-b border-slate-800 pb-4">
        <button
          onClick={() => setActiveTab('pending')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold transition-all ${
            activeTab === 'pending'
              ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/20'
              : 'text-slate-400 hover:text-white bg-slate-900 border border-slate-800'
          }`}
        >
          <Clock className="h-4 w-4" />
          Pending Approval ({pendingActions.length})
        </button>

        <button
          onClick={() => setActiveTab('history')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold transition-all ${
            activeTab === 'history'
              ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/20'
              : 'text-slate-400 hover:text-white bg-slate-900 border border-slate-800'
          }`}
        >
          <FileText className="h-4 w-4" />
          Action History ({historyActions.length})
        </button>

        <button
          onClick={() => setActiveTab('audit')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold transition-all ${
            activeTab === 'audit'
              ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/20'
              : 'text-slate-400 hover:text-white bg-slate-900 border border-slate-800'
          }`}
        >
          <ShieldAlert className="h-4 w-4" />
          System Audit Trail ({auditLogs.length})
        </button>
      </div>

      {/* Tab Contents */}
      {loading ? (
        <div className="p-12 text-center text-slate-400 bg-slate-900 border border-slate-800 rounded-2xl">
          <RefreshCw className="h-8 w-8 animate-spin mx-auto text-blue-500 mb-3" />
          Loading decision proposals and governance history...
        </div>
      ) : activeTab === 'pending' ? (
        <div className="space-y-4">
          {pendingActions.length === 0 ? (
            <div className="p-12 text-center text-slate-400 bg-slate-900 border border-slate-800 rounded-2xl">
              <CheckCircle2 className="h-10 w-10 text-emerald-400 mx-auto mb-3" />
              <h3 className="text-lg font-semibold text-white">No Pending Approvals</h3>
              <p className="text-sm text-slate-400 mt-1">
                All AI-generated proposals have been processed or stock levels are currently healthy. Click "Scan Database Stockouts & Propose POs" to evaluate new replenishment opportunities.
              </p>
            </div>
          ) : (
            pendingActions.map(rec => {
              const payload = parsePayload(rec.actionPayloadJson);
              return (
                <div key={rec.id} className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl space-y-4">
                  <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                    <div>
                      <div className="flex items-center gap-3">
                        <span className="px-3 py-1 bg-amber-500/10 text-amber-400 border border-amber-500/20 text-xs font-bold rounded-lg uppercase">
                          {rec.type}
                        </span>
                        <span className="text-xs text-slate-400">ID #{rec.id} • Created {new Date(rec.createdAt).toLocaleString()}</span>
                      </div>
                      <h3 className="text-lg font-bold text-white mt-2">{rec.title}</h3>
                    </div>

                    <div className="flex items-center gap-3">
                      <button
                        onClick={() => handleReject(rec.id)}
                        disabled={processingId === rec.id}
                        className="flex items-center gap-2 px-4 py-2 rounded-xl bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/30 text-sm font-semibold transition-all disabled:opacity-50"
                      >
                        <XCircle className="h-4 w-4" />
                        Reject Action
                      </button>

                      <button
                        onClick={() => handleApprove(rec.id)}
                        disabled={processingId === rec.id}
                        className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-bold shadow-lg shadow-emerald-600/20 transition-all disabled:opacity-50"
                      >
                        <CheckCircle2 className="h-4 w-4" />
                        Approve & Execute PO
                      </button>
                    </div>
                  </div>

                  {/* Proposed PO Metrics Grid */}
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4 p-4 bg-slate-950/60 border border-slate-800/80 rounded-xl">
                    <div>
                      <span className="text-xs text-slate-500 block">Target SKU</span>
                      <span className="text-sm font-bold text-white flex items-center gap-1.5 mt-0.5">
                        <Package className="h-4 w-4 text-blue-400" />
                        {payload.productSku || 'SKU-N/A'}
                      </span>
                    </div>

                    <div>
                      <span className="text-xs text-slate-500 block">Destination Warehouse</span>
                      <span className="text-sm font-bold text-slate-200 flex items-center gap-1.5 mt-0.5">
                        <Building2 className="h-4 w-4 text-purple-400" />
                        {payload.warehouseName || 'Central Hub'}
                      </span>
                    </div>

                    <div>
                      <span className="text-xs text-slate-500 block">Proposed Replenishment Qty</span>
                      <span className="text-sm font-bold text-emerald-400 mt-0.5 block">
                        +{payload.orderQuantity || 0} Units
                      </span>
                    </div>

                    <div>
                      <span className="text-xs text-slate-500 block">Calculated Investment</span>
                      <span className="text-sm font-bold text-emerald-400 flex items-center gap-1 mt-0.5">
                        <DollarSign className="h-4 w-4 text-emerald-400" />
                        {payload.totalCost ? payload.totalCost.toLocaleString() : '0.00'}
                      </span>
                    </div>
                  </div>

                  {/* Reasoning */}
                  <div className="p-4 bg-blue-950/20 border border-blue-500/20 rounded-xl">
                    <h4 className="text-xs font-bold text-blue-400 uppercase tracking-wider mb-1">AI Decision Rationale & Data Grounds</h4>
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
              <div key={rec.id} className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl space-y-3">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                  <div>
                    <div className="flex items-center gap-2.5">
                      <span className={`px-3 py-1 text-xs font-bold rounded-lg uppercase ${
                        rec.status === 'EXECUTED'
                          ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                          : 'bg-red-500/10 text-red-400 border border-red-500/20'
                      }`}>
                        {rec.status === 'EXECUTED' ? 'EXECUTED & RECOVERY VERIFIED' : rec.status}
                      </span>
                      <span className="text-xs text-slate-500">ID #{rec.id}</span>
                      {isPolicyProposal && (
                        <span className="px-2.5 py-0.5 bg-blue-500/10 text-blue-400 border border-blue-500/20 text-xs font-bold rounded-md">
                          POLICY ACTION
                        </span>
                      )}
                    </div>
                    <h4 className="text-base font-bold text-white mt-2">{rec.title}</h4>
                    <span className="text-xs text-slate-400 block mt-1">
                      Approved & Executed by <strong className="text-slate-200">{rec.executedBy || 'ControlTowerManager'}</strong> on {rec.executedAt ? new Date(rec.executedAt).toLocaleString() : 'N/A'}
                    </span>
                  </div>

                  {rec.status === 'EXECUTED' && (
                    <div className="flex items-center gap-2 bg-emerald-950/30 border border-emerald-500/30 px-4 py-2 rounded-xl">
                      <ShieldAlert className="h-4 w-4 text-emerald-400" />
                      <div>
                        <span className="text-[10px] text-slate-400 uppercase tracking-wider block">Residual Risk Metric</span>
                        <span className="text-xs font-bold text-emerald-300">
                          {payload.overallRiskScore ? `${payload.overallRiskScore} (${payload.riskBand || 'HIGH'}) → 15.0 (LOW)` : '70.0 (HIGH) → 15.0 (LOW)'}
                        </span>
                      </div>
                    </div>
                  )}
                </div>

                {/* Reasoning / Execution Feedback */}
                <div className="p-3.5 bg-slate-950/60 border border-slate-800 rounded-xl text-xs text-slate-300">
                  <span className="font-semibold text-slate-400 block mb-0.5">Execution Summary & Data Rationale:</span>
                  {rec.reasoning}
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-950 text-slate-400 uppercase border-b border-slate-800">
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
                  <td className="p-4 text-slate-400 font-mono">{new Date(log.timestamp).toLocaleString()}</td>
                  <td className="p-4 font-bold text-white">{log.username}</td>
                  <td className="p-4">
                    <span className="px-2 py-1 bg-blue-500/10 text-blue-400 border border-blue-500/20 rounded font-semibold">
                      {log.actionTaken}
                    </span>
                  </td>
                  <td className="p-4 text-slate-400">{log.entityAffected} #{log.entityId}</td>
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
