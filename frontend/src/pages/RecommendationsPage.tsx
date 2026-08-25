import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { CheckCircle2, XCircle, ShieldCheck, Play } from 'lucide-react';

export const RecommendationsPage: React.FC = () => {
  const [recommendations, setRecommendations] = useState<any[]>([]);

  const fetchRecommendations = async () => {
    try {
      const res = await axiosInstance.get('/ai/recommendations');
      setRecommendations(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    fetchRecommendations();
  }, []);

  const handleApprove = async (id: number) => {
    try {
      await axiosInstance.post(`/actions/${id}/approve`);
      fetchRecommendations();
    } catch (err) {
      alert('Action approval failed');
    }
  };

  const handleReject = async (id: number) => {
    try {
      await axiosInstance.post(`/actions/${id}/reject`);
      fetchRecommendations();
    } catch (err) {
      alert('Action rejection failed');
    }
  };

  return (
    <div className="space-y-6">
      <div className="glass-panel p-5 rounded-xl border border-cyan-500/30 bg-cyan-950/20 flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <ShieldCheck className="w-8 h-8 text-cyan-400" />
          <div>
            <h2 className="text-lg font-bold text-white">Human-in-the-Loop AI Action Approvals</h2>
            <p className="text-xs text-slate-400">
              AI recommendations are held in <span className="text-amber-400 font-semibold">PENDING_APPROVAL</span> state. Authorized managers must approve before executing business changes.
            </p>
          </div>
        </div>
      </div>

      <div className="space-y-4">
        {recommendations.map((rec) => (
          <div key={rec.id} className="glass-panel p-5 rounded-xl border border-slate-800 space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div>
                <span className="text-xs font-mono text-cyan-400 font-semibold">{rec.type}</span>
                <h3 className="text-base font-bold text-white">{rec.title}</h3>
              </div>

              <span className={`px-3 py-1 rounded-full text-xs font-bold self-start sm:self-auto ${
                rec.status === 'PENDING_APPROVAL' ? 'bg-amber-950 text-amber-400 border border-amber-800' :
                rec.status === 'EXECUTED' ? 'bg-emerald-950 text-emerald-400 border border-emerald-800' :
                'bg-rose-950 text-rose-400 border border-rose-800'
              }`}>
                {rec.status}
              </span>
            </div>

            <p className="text-xs text-slate-300 bg-slate-900/60 p-3 rounded-lg border border-slate-800 font-mono">
              <span className="text-slate-500">Execution Payload: </span>
              {rec.actionPayloadJson}
            </p>

            <p className="text-xs text-slate-400">
              <span className="font-semibold text-slate-300">Reasoning: </span>
              {rec.reasoning}
            </p>

            {rec.status === 'PENDING_APPROVAL' && (
              <div className="flex items-center justify-end space-x-3 pt-3 border-t border-slate-800">
                <button
                  onClick={() => handleReject(rec.id)}
                  className="px-4 py-2 rounded-lg bg-slate-800 hover:bg-rose-950/80 text-rose-300 text-xs font-semibold border border-slate-700 hover:border-rose-700/50 flex items-center space-x-1.5 transition-all"
                >
                  <XCircle className="w-3.5 h-3.5" />
                  <span>Reject</span>
                </button>

                <button
                  onClick={() => handleApprove(rec.id)}
                  className="px-4 py-2 rounded-lg bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-400 hover:to-teal-500 text-white text-xs font-semibold shadow-lg shadow-emerald-500/20 flex items-center space-x-1.5 transition-all"
                >
                  <Play className="w-3.5 h-3.5 fill-white" />
                  <span>Approve & Execute Action</span>
                </button>
              </div>
            )}

            {rec.status === 'EXECUTED' && (
              <div className="text-xs text-emerald-400 flex items-center space-x-1.5 pt-2 border-t border-slate-800">
                <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                <span>Executed by {rec.executedBy} at {new Date(rec.executedAt).toLocaleString()} (Logged in Audit Log)</span>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};
