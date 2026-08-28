import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { FileText, Sparkles, BookOpen, ExternalLink, CheckCircle } from 'lucide-react';

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
      <div>
        <h2 className="text-xl font-bold text-white">Supply Chain Control Tower RAG Knowledge Center</h2>
        <p className="text-xs text-slate-400">Query project technical documentation, forecasting formulas, safety stock policies, and supplier performance methodologies</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Knowledge Sources Overview Card */}
        <div className="glass-panel p-5 rounded-xl border border-slate-800 space-y-4 md:col-span-1">
          <div className="flex items-center space-x-2 text-cyan-400">
            <BookOpen className="w-5 h-5" />
            <h3 className="text-base font-bold text-white">Project Knowledge Base</h3>
          </div>

          <div className="p-3 rounded-lg bg-slate-900 border border-slate-800 text-xs space-y-1">
            <span className="text-slate-400 block text-[11px] uppercase">Indexed Chunks</span>
            <span className="text-xl font-bold text-cyan-400">{sourcesInfo?.totalChunksIndexed ?? 0} Chunks</span>
            <span className="text-[10px] text-slate-500 block">Ingested from Technical Docs & Manuals</span>
          </div>

          <div className="space-y-2 text-xs">
            <span className="text-slate-400 font-semibold block text-[11px] uppercase">Active Knowledge Files:</span>
            <div className="space-y-1.5">
              <div className="p-2.5 rounded-lg bg-slate-900/80 border border-slate-800 flex items-center justify-between text-xs">
                <div className="flex items-center space-x-2 truncate">
                  <FileText className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
                  <span className="text-slate-200 font-mono text-[11px] truncate">technical_documentation_report.md</span>
                </div>
                <CheckCircle className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              </div>

              <div className="p-2.5 rounded-lg bg-slate-900/80 border border-slate-800 flex items-center justify-between text-xs">
                <div className="flex items-center space-x-2 truncate">
                  <FileText className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
                  <span className="text-slate-200 font-mono text-[11px] truncate">README.md</span>
                </div>
                <CheckCircle className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              </div>

              <div className="p-2.5 rounded-lg bg-slate-900/80 border border-slate-800 flex items-center justify-between text-xs">
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
        <div className="glass-panel p-5 rounded-xl border border-slate-800 flex flex-col justify-between space-y-4 md:col-span-2">
          <div className="space-y-4">
            <div className="flex items-center space-x-2 text-indigo-400">
              <Sparkles className="w-5 h-5" />
              <h3 className="text-base font-bold text-white">Grounded Semantic Knowledge Query</h3>
            </div>

            <form onSubmit={handleRagQuery} className="flex space-x-2">
              <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="e.g. What is OTIF and how is it calculated?"
                className="flex-1 px-3.5 py-2.5 rounded-lg bg-slate-900 border border-slate-700 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500"
              />
              <button
                type="submit"
                disabled={searching}
                className="px-4 py-2.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold disabled:opacity-50"
              >
                {searching ? 'Searching...' : 'Query RAG'}
              </button>
            </form>

            <div className="flex items-center space-x-2 flex-wrap gap-y-1 text-[11px]">
              <span className="text-slate-400 font-semibold">Sample Queries:</span>
              <button
                type="button"
                onClick={() => handleSampleQuery("What is OTIF and how is it calculated?")}
                className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-cyan-300 font-mono"
              >
                OTIF Definition
              </button>
              <button
                type="button"
                onClick={() => handleSampleQuery("How is safety stock calculated in this project?")}
                className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-purple-300 font-mono"
              >
                Safety Stock Formula
              </button>
              <button
                type="button"
                onClick={() => handleSampleQuery("What is the company policy for air freight carbon emissions?")}
                className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-amber-300 font-mono"
              >
                Out-of-Bounds Test
              </button>
            </div>
          </div>

          {ragResult && (
            <div className="p-4 rounded-xl bg-slate-900/90 border border-slate-800 text-xs space-y-3">
              <div className="flex items-center justify-between text-slate-400 border-b border-slate-800 pb-2">
                <span className="font-semibold text-indigo-400 flex items-center space-x-1">
                  <Sparkles className="w-3.5 h-3.5 mr-1" />
                  Grounded Response ({ragResult.queryType})
                </span>
                <span className="font-mono text-[11px] text-cyan-400">
                  {ragResult.retrievedSources?.length || 0} Source Chunk(s)
                </span>
              </div>

              <p className="text-slate-200 whitespace-pre-wrap leading-relaxed">{ragResult.answer}</p>

              {ragResult.retrievedSources && ragResult.retrievedSources.length > 0 && (
                <div className="pt-2 border-t border-slate-800 space-y-2">
                  <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">
                    Retrieved Document Source Citations:
                  </span>
                  <div className="space-y-1.5">
                    {ragResult.retrievedSources.map((src: any, idx: number) => (
                      <div key={idx} className="p-2 rounded bg-slate-950 border border-slate-800/80 text-[11px] flex items-center justify-between">
                        <div className="flex items-center space-x-2 truncate">
                          <ExternalLink className="w-3 h-3 text-cyan-400 shrink-0" />
                          <span className="font-mono font-bold text-cyan-300 truncate">{src.sourceName}</span>
                          <span className="text-slate-400 truncate">• {src.title}</span>
                        </div>
                        {src.relevanceScore && (
                          <span className="text-[10px] text-emerald-400 font-mono font-bold shrink-0">Score: {src.relevanceScore}</span>
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
