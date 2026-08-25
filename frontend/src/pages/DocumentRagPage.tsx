import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { FileText, Upload, Sparkles } from 'lucide-react';

export const DocumentRagPage: React.FC = () => {
  const [documents, setDocuments] = useState<any[]>([]);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [query, setQuery] = useState('');
  const [ragResult, setRagResult] = useState<any>(null);
  const [searching, setSearching] = useState(false);

  const fetchDocuments = async () => {
    try {
      const res = await axiosInstance.get('/documents');
      setDocuments(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    fetchDocuments();
  }, []);

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile) return;

    setUploading(true);
    const formData = new FormData();
    formData.append('file', selectedFile);

    try {
      await axiosInstance.post('/documents/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      setSelectedFile(null);
      fetchDocuments();
      alert('Document successfully processed and indexed into pgvector!');
    } catch (err) {
      alert('Upload failed');
    } finally {
      setUploading(false);
    }
  };

  const handleRagQuery = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;

    setSearching(true);
    try {
      const res = await axiosInstance.post('/documents/query', { query });
      setRagResult(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setSearching(false);
    }
  };

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      <div>
        <h2 className="text-xl font-bold text-white">Supply Chain Policy Knowledge Base (RAG)</h2>
        <p className="text-xs text-slate-400">Upload procurement SOPs, contracts, and guidelines to enable vector similarity Q&A</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Document Upload Box */}
        <div className="glass-panel p-5 rounded-xl border border-slate-800 space-y-4">
          <div className="flex items-center space-x-2 text-cyan-400">
            <Upload className="w-5 h-5" />
            <h3 className="text-base font-bold text-white">Upload SOP / Policy Document</h3>
          </div>

          <form onSubmit={handleUpload} className="space-y-4">
            <div className="border-2 border-dashed border-slate-700/80 rounded-xl p-6 text-center hover:border-cyan-500/50 transition-all bg-slate-900/40">
              <input
                type="file"
                accept=".pdf,.txt,.doc,.docx"
                onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
                className="hidden"
                id="doc-upload"
              />
              <label htmlFor="doc-upload" className="cursor-pointer block">
                <FileText className="w-10 h-10 text-cyan-400 mx-auto mb-2" />
                <p className="text-xs font-semibold text-slate-200">
                  {selectedFile ? selectedFile.name : 'Click to select PDF or Text document'}
                </p>
                <p className="text-[11px] text-slate-500 mt-1">Supports PDF, TXT up to 20MB</p>
              </label>
            </div>

            <button
              type="submit"
              disabled={!selectedFile || uploading}
              className="w-full py-2.5 rounded-lg bg-cyan-500 hover:bg-cyan-400 text-white font-semibold text-sm transition-all disabled:opacity-50"
            >
              {uploading ? 'Extracting Text & Indexing Vector Embedding...' : 'Upload & Index into pgvector'}
            </button>
          </form>

          {/* Uploaded Files List */}
          <div className="pt-3 border-t border-slate-800">
            <h4 className="text-xs font-semibold text-slate-400 mb-2">Indexed Documents ({documents.length}):</h4>
            <div className="space-y-1.5 max-h-40 overflow-y-auto">
              {documents.map((d) => (
                <div key={d.id} className="p-2 rounded-lg bg-slate-900/80 border border-slate-800 text-xs flex items-center justify-between">
                  <div className="flex items-center space-x-2 truncate">
                    <FileText className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
                    <span className="text-slate-200 font-medium truncate">{d.fileName}</span>
                  </div>
                  <span className="text-[10px] text-slate-500 shrink-0">{d.chunkCount} chunks</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Grounded Policy Q&A Search */}
        <div className="glass-panel p-5 rounded-xl border border-slate-800 flex flex-col justify-between space-y-4">
          <div>
            <div className="flex items-center space-x-2 text-indigo-400 mb-2">
              <Sparkles className="w-5 h-5" />
              <h3 className="text-base font-bold text-white">Grounded Policy Search</h3>
            </div>
            <p className="text-xs text-slate-400 mb-4">Ask policy questions grounded in uploaded company documentation</p>

            <form onSubmit={handleRagQuery} className="flex space-x-2">
              <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="e.g. What is the approved supplier payment policy?"
                className="flex-1 px-3.5 py-2.5 rounded-lg bg-slate-900 border border-slate-700 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500"
              />
              <button
                type="submit"
                disabled={searching}
                className="px-4 py-2.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold"
              >
                Query
              </button>
            </form>
          </div>

          {ragResult && (
            <div className="p-4 rounded-xl bg-slate-900/90 border border-slate-800 text-xs space-y-2">
              <div className="flex items-center justify-between text-slate-400 border-b border-slate-800 pb-2">
                <span className="font-semibold text-indigo-400">pgvector Retrieved Grounding</span>
                <span>Sources: {ragResult.sourcesCount}</span>
              </div>
              <p className="text-slate-200 whitespace-pre-wrap leading-relaxed">{ragResult.groundedAnswer}</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
