import React from 'react';
import { Activity } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export const Header: React.FC<{ title: string }> = ({ title }) => {
  const { user } = useAuth();

  return (
    <header className="h-16 glass-panel border-b border-slate-800/80 px-6 flex items-center justify-between sticky top-0 z-20">
      <div>
        <h2 className="text-xl font-bold text-white tracking-tight">{title}</h2>
      </div>

      <div className="flex items-center space-x-4">
        {/* Live System Status Indicator */}
        <div className="hidden sm:flex items-center space-x-2 px-3 py-1 rounded-full bg-emerald-950/60 border border-emerald-500/30 text-emerald-400 text-xs font-semibold">
          <Activity className="w-3.5 h-3.5 animate-pulse" />
          <span>Spring AI Agentic Core Online</span>
        </div>

        {/* User Profile */}
        <div className="flex items-center space-x-3 pl-4 border-l border-slate-800">
          <div className="w-8 h-8 rounded-full bg-gradient-to-r from-blue-500 to-cyan-500 flex items-center justify-center font-bold text-white text-xs shadow-md">
            {user?.username ? user.username.substring(0, 2).toUpperCase() : 'US'}
          </div>
          <div className="hidden md:block">
            <p className="text-xs font-semibold text-slate-200">{user?.username}</p>
            <p className="text-[10px] text-slate-400">{user?.email}</p>
          </div>
        </div>
      </div>
    </header>
  );
};
