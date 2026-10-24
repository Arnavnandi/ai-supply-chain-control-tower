import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { FileText, Sparkles, BookOpen, ExternalLink, CheckCircle, Search, RefreshCw } from 'lucide-react';

export const DocumentRagPage: React.FC = () => {
  const [sourcesInfo, setSourcesInfo] = useState<any>(null);
  const [query, setQuery] = useState('');
  const [ragResult, setRagResult] = useState<any>(null);
  const [searching, setSearching] = useState(false);

  const fetchSources = async () => {
    try {
      const res = await axiosInstance.get('/ai/rag/sources');
      setSourcesInfo(res.data);
    } catch (err) {
      console.error('Failed to load RAG sources:', err);
    }
  };

  useEffect(() => {
    fetchSources();
  }, []);

  const handleRagQuery = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;

    setSearching(true);
    try {
      const res = await axiosInstance.post('/ai/rag/query', { question: query });
      setRagResult(res.data);
    } catch (err) {
      console.error('RAG query error:', err);
    } finally {
      setSearching(false);
    }
  };

  const handleSampleQuery = (sampleText: string) => {
    setQuery(sampleText);
  };

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      {/* Page Header */}
      <div>
        <div className="flex items-center space-x-2.5">
          <h2 className="text-xl font-bold text-white tracking-tight">Supply Chain Policy Knowledge RAG</h2>
          <span className="px-2.5 py-0.5 rounded-full text-xs font-mono font-semibold bg-cyan-950/70 text-cyan-400 border border-cyan-800/60">
            Hybrid RRF Vector Search
          </span>
        </div>
        <p className="text-xs text-slate-400 mt-1">
          Query project operational manuals, forecasting formulas, safety buffer policies, and vendor SLA benchmarks
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Knowledge Sources Overview Card */}
        <div className="glass-panel p-5 rounded-2xl border border-slate-800 shadow-xl space-y-4 md:col-span-1">
          <div className="flex items-center space-x-2 text-cyan-400">
            <BookOpen className="w-5 h-5 text-cyan-400" />
            <h3 className="text-base font-bold text-white tracking-tight">Project Knowledge Base</h3>
          </div>

          <div className="p-3.5 rounded-xl bg-slate-950/70 border border-slate-800 text-xs space-y-1">
            <span className="text-slate-400 block text-[11px] uppercase tracking-wider font-semibold">Indexed Chunks</span>
            <span className="text-2xl font-bold font-mono text-cyan-400">{sourcesInfo?.totalChunksIndexed ?? 0} Chunks</span>
            <span className="text-[10px] text-slate-500 block">Ingested from Technical Docs & Policy Specs</span>
          </div>

          <div className="space-y-2 text-xs">
            <span className="text-slate-400 font-semibold block text-[11px] uppercase tracking-wider">Active Policy Corpora:</span>
            <div className="space-y-1.5">
              <div className="p-2.5 rounded-xl bg-slate-900/80 border border-slate-800/80 flex items-center justify-between text-xs">
                <div className="flex items-center space-x-2 truncate">
                  <FileText className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
                  <span className="text-slate-200 font-mono text-[11px] truncate">technical_documentation_report.md</span>
                </div>
                <CheckCircle className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              </div>

              <div className="p-2.5 rounded-xl bg-slate-900/80 border border-slate-800/80 flex items-center justify-between text-xs">
                <div className="flex items-center space-x-2 truncate">
                  <FileText className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
                  <span className="text-slate-200 font-mono text-[11px] truncate">README.md</span>
                </div>
                <CheckCircle className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              </div>

              <div className="p-2.5 rounded-xl bg-slate-900/80 border border-slate-800/80 flex items-center justify-between text-xs">
                <div className="flex items-center space-x-2 truncate">
                  <FileText className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
                  <span className="text-slate-200 font-mono text-[11px] truncate">walkthrough.md</span>
                </div>
                <CheckCircle className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              </div>
            </div>
          </div>
        </div>

        {/* Grounded Policy Q&A Search */}
        <div className="glass-panel p-5 rounded-2xl border border-slate-800 shadow-xl flex flex-col justify-between space-y-4 md:col-span-2">
          <div className="space-y-4">
            <div className="flex items-center space-x-2 text-cyan-400">
              <Sparkles className="w-5 h-5 text-cyan-400" />
              <h3 className="text-base font-bold text-white tracking-tight">Grounded Semantic Knowledge Query</h3>
            </div>

            <form onSubmit={handleRagQuery} className="flex space-x-2">
              <div className="relative flex-1">
                <Search className="w-4 h-4 text-cyan-400 absolute left-3.5 top-3" />
                <input
                  type="text"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="e.g. What is OTIF and how is it calculated?"
                  className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-950 border border-slate-700/80 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-400 focus:ring-1 focus:ring-cyan-500/30"
                />
              </div>
              <button
                type="submit"
                disabled={searching || !query.trim()}
                className="px-5 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold disabled:opacity-50 shadow-lg shadow-cyan-600/25 transition-all cursor-pointer flex items-center space-x-1.5"
              >
                {searching && <RefreshCw className="w-3.5 h-3.5 animate-spin" />}
                <span>{searching ? 'Querying...' : 'Search Policy'}</span>
              </button>
            </form>

            <div className="flex items-center space-x-2 flex-wrap gap-y-1 text-[11px]">
              <span className="text-slate-400 font-semibold text-[11px]">Sample Queries:</span>
              <button
                type="button"
                onClick={() => handleSampleQuery("What is OTIF and how is it calculated?")}
                className="px-2.5 py-1 rounded-lg bg-slate-900 hover:bg-slate-800 text-cyan-300 font-mono text-[11px] border border-slate-800 hover:border-cyan-500/40 transition-all cursor-pointer"
              >
                OTIF Definition
              </button>
              <button
                type="button"
                onClick={() => handleSampleQuery("How is safety stock calculated in this project?")}
                className="px-2.5 py-1 rounded-lg bg-slate-900 hover:bg-slate-800 text-purple-300 font-mono text-[11px] border border-slate-800 hover:border-purple-500/40 transition-all cursor-pointer"
              >
                Safety Stock Formula
              </button>
              <button
                type="button"
                onClick={() => handleSampleQuery("What is the company policy for air freight carbon emissions?")}
                className="px-2.5 py-1 rounded-lg bg-slate-900 hover:bg-slate-800 text-amber-300 font-mono text-[11px] border border-slate-800 hover:border-amber-500/40 transition-all cursor-pointer"
              >
                Out-of-Bounds Test
              </button>
            </div>
          </div>

          {ragResult && (
            <div className="p-4 rounded-xl bg-slate-950/80 border border-slate-800 text-xs space-y-3 mt-4 animate-fade-in shadow-inner">
              <div className="flex items-center justify-between text-slate-400 border-b border-slate-800 pb-2">
                <span className="font-semibold text-cyan-300 flex items-center space-x-1.5">
                  <Sparkles className="w-3.5 h-3.5 text-cyan-400" />
                  <span>Grounded Response ({ragResult.queryType})</span>
                </span>
                <span className="font-mono text-[11px] text-cyan-400">
                  {ragResult.retrievedSources?.length || 0} Source Chunk(s)
                </span>
              </div>

              <p className="text-slate-200 whitespace-pre-wrap leading-relaxed">{ragResult.answer}</p>

              {ragResult.retrievedSources && ragResult.retrievedSources.length > 0 && (
                <div className="pt-2 border-t border-slate-800 space-y-2">
                  <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                    Retrieved Document Source Citations:
                  </span>
                  <div className="space-y-1.5">
                    {ragResult.retrievedSources.map((src: any, idx: number) => (
                      <div key={idx} className="p-2.5 rounded-lg bg-slate-900 border border-slate-800/80 text-[11px] flex items-center justify-between">
                        <div className="flex items-center space-x-2 truncate">
                          <ExternalLink className="w-3 h-3 text-cyan-400 shrink-0" />
                          <span className="font-mono font-bold text-cyan-300 truncate">{src.sourceName}</span>
                          <span className="text-slate-400 truncate">• {src.title}</span>
                        </div>
                        {src.relevanceScore && (
                          <span className="text-[10px] text-emerald-400 font-mono font-bold shrink-0">
                            Score: {src.relevanceScore}
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
