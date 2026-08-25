import React, { useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { Bot, Send, User, Sparkles, Database, Cpu } from 'lucide-react';

interface ChatMessage {
  id: string;
  sender: 'user' | 'ai';
  agentType?: string;
  text: string;
  timestamp: string;
}

export const AiAssistantPage: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: '1',
      sender: 'ai',
      agentType: 'EXECUTIVE',
      text: '### 🛸 Executive Control Tower AI Assistant Online\nI am connected to your PostgreSQL domain database via **Spring AI Tool Calling**.\nHow can I analyze inventory stockouts, supplier reliability, or shipment transit risks for you today?',
      timestamp: new Date().toLocaleTimeString()
    }
  ]);
  const [inputPrompt, setInputPrompt] = useState('');
  const [selectedAgent, setSelectedAgent] = useState('EXECUTIVE');
  const [loading, setLoading] = useState(false);

  const agents = [
    { id: 'EXECUTIVE', label: 'Executive Control Assistant', desc: 'Overall Supply Chain Risk & Decisions' },
    { id: 'INVENTORY', label: 'Inventory Intelligence Agent', desc: 'Stockout & Overstock Analysis' },
    { id: 'SUPPLIER', label: 'Supplier Intelligence Agent', desc: 'Performance & Lead Time Comparison' },
    { id: 'LOGISTICS', label: 'Logistics Intelligence Agent', desc: 'Shipment Delays & Carrier Tracking' },
    { id: 'WAREHOUSE', label: 'Warehouse Optimization Agent', desc: 'Capacity & Stock Reallocation' },
  ];

  const presets = [
    'Which products are at high risk of stockout in the next 7 days?',
    'Which supplier should we choose for SKU-ELEC-001?',
    'Which shipments are currently delayed in transit?',
    'Which warehouse distribution center has highest utilization?'
  ];

  const handleSend = async (queryText?: string) => {
    const textToSend = queryText || inputPrompt;
    if (!textToSend.trim() || loading) return;

    const userMsg: ChatMessage = {
      id: Date.now().toString(),
      sender: 'user',
      text: textToSend,
      timestamp: new Date().toLocaleTimeString()
    };

    setMessages(prev => [...prev, userMsg]);
    if (!queryText) setInputPrompt('');
    setLoading(true);

    try {
      const res = await axiosInstance.post('/ai/query', {
        prompt: textToSend,
        agentType: selectedAgent
      });

      const aiMsg: ChatMessage = {
        id: (Date.now() + 1).toString(),
        sender: 'ai',
        agentType: res.data.agentUsed,
        text: res.data.response,
        timestamp: new Date().toLocaleTimeString()
      };

      setMessages(prev => [...prev, aiMsg]);
    } catch (err: any) {
      const errorMsg: ChatMessage = {
        id: (Date.now() + 1).toString(),
        sender: 'ai',
        agentType: 'ERROR',
        text: 'Failed to process query with Spring AI backend. Please verify server connectivity.',
        timestamp: new Date().toLocaleTimeString()
      };
      setMessages(prev => [...prev, errorMsg]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-4 max-w-6xl mx-auto">
      {/* Header & Agent Selector */}
      <div className="glass-panel p-5 rounded-2xl border border-slate-800 space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-cyan-500 to-blue-600 flex items-center justify-center shadow-lg shadow-cyan-500/20">
              <Bot className="w-6 h-6 text-white" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white">AI Control Center Workspace</h2>
              <p className="text-xs text-cyan-400">Spring AI Tool Calling • PostgreSQL Vector Engine Grounding</p>
            </div>
          </div>

          <div className="flex items-center space-x-2 text-xs text-slate-400 bg-slate-900 px-3 py-1.5 rounded-lg border border-slate-800">
            <Database className="w-3.5 h-3.5 text-cyan-400" />
            <span>Database Tools Active</span>
          </div>
        </div>

        {/* Agent Selector Bar */}
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 pt-2 border-t border-slate-800">
          {agents.map((agent) => (
            <button
              key={agent.id}
              onClick={() => setSelectedAgent(agent.id)}
              className={`p-2.5 rounded-xl text-left transition-all text-xs border ${
                selectedAgent === agent.id
                  ? 'bg-cyan-500/20 border-cyan-500/50 text-white font-semibold shadow-inner'
                  : 'bg-slate-900/60 border-slate-800 text-slate-400 hover:text-slate-200'
              }`}
            >
              <p className="font-bold truncate">{agent.label}</p>
              <p className="text-[10px] text-slate-400 truncate mt-0.5">{agent.desc}</p>
            </button>
          ))}
        </div>
      </div>

      {/* Chat Conversation Stream */}
      <div className="glass-panel p-6 rounded-2xl border border-slate-800 min-h-[450px] max-h-[500px] flex flex-col justify-between overflow-hidden">
        <div className="overflow-y-auto space-y-4 pr-2">
          {messages.map((m) => (
            <div
              key={m.id}
              className={`flex items-start space-x-3 ${m.sender === 'user' ? 'justify-end' : 'justify-start'}`}
            >
              {m.sender === 'ai' && (
                <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-cyan-500 to-blue-600 flex items-center justify-center text-white shrink-0 mt-1 shadow-md">
                  <Bot className="w-4 h-4" />
                </div>
              )}

              <div
                className={`max-w-2xl p-4 rounded-2xl text-sm leading-relaxed ${
                  m.sender === 'user'
                    ? 'bg-cyan-600 text-white rounded-tr-none shadow-lg shadow-cyan-600/20'
                    : 'bg-slate-900/90 border border-slate-800 text-slate-200 rounded-tl-none whitespace-pre-wrap'
                }`}
              >
                {m.sender === 'ai' && (
                  <div className="mb-2 pb-1.5 border-b border-slate-800/80 flex items-center justify-between text-[11px] text-cyan-400 font-mono">
                    <span className="flex items-center space-x-1">
                      <Cpu className="w-3 h-3" />
                      <span>{m.agentType || selectedAgent} AGENT</span>
                    </span>
                    <span className="text-slate-500">{m.timestamp}</span>
                  </div>
                )}
                <div className="prose prose-invert max-w-none text-xs sm:text-sm">{m.text}</div>
              </div>

              {m.sender === 'user' && (
                <div className="w-8 h-8 rounded-lg bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-300 shrink-0 mt-1">
                  <User className="w-4 h-4" />
                </div>
              )}
            </div>
          ))}

          {loading && (
            <div className="flex items-center space-x-3">
              <div className="w-8 h-8 rounded-lg bg-cyan-600 flex items-center justify-center text-white">
                <Bot className="w-4 h-4 animate-spin" />
              </div>
              <div className="p-3 rounded-2xl bg-slate-900 border border-slate-800 text-slate-400 text-xs flex items-center space-x-2">
                <Sparkles className="w-3.5 h-3.5 text-cyan-400 animate-pulse" />
                <span>Executing Spring AI Tool Calling against PostgreSQL database...</span>
              </div>
            </div>
          )}
        </div>

        {/* Input Bar & One-click Presets */}
        <div className="mt-4 pt-4 border-t border-slate-800/80 space-y-3">
          {/* Presets */}
          <div className="flex items-center space-x-2 overflow-x-auto pb-1 text-xs">
            <span className="text-slate-500 shrink-0 font-medium">Quick Queries:</span>
            {presets.map((p, idx) => (
              <button
                key={idx}
                onClick={() => handleSend(p)}
                className="px-3 py-1 rounded-full bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-800 hover:border-cyan-500/40 shrink-0 transition-all text-xs"
              >
                {p}
              </button>
            ))}
          </div>

          <div className="flex items-center space-x-2">
            <input
              type="text"
              value={inputPrompt}
              onChange={(e) => setInputPrompt(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSend()}
              placeholder={`Ask ${selectedAgent} Agent a supply chain question...`}
              className="flex-1 px-4 py-3 rounded-xl bg-slate-900 border border-slate-700/80 text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500 text-sm"
            />
            <button
              onClick={() => handleSend()}
              disabled={loading || !inputPrompt.trim()}
              className="px-5 py-3 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-white font-semibold text-sm shadow-lg shadow-cyan-500/25 transition-all flex items-center space-x-2 disabled:opacity-50"
            >
              <span>Send</span>
              <Send className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
