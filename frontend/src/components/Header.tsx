import React from 'react';
import { Activity, Menu } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

interface HeaderProps {
  title: string;
  onToggleMobileMenu?: () => void;
}

export const Header: React.FC<HeaderProps> = ({ title, onToggleMobileMenu }) => {
  const { user } = useAuth();

  return (
    <header className="h-16 glass-panel border-b border-cyan-500/10 px-4 md:px-6 flex items-center justify-between sticky top-0 z-20 bg-[#090f1d]/85">
      <div className="flex items-center space-x-3 min-w-0">
        {onToggleMobileMenu && (
          <button
            id="mobile-menu-button"
            onClick={onToggleMobileMenu}
            className="md:hidden p-2 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800/80 transition-colors cursor-pointer"
            aria-label="Open navigation menu"
          >
            <Menu className="w-5 h-5" />
          </button>
        )}
        <div className="min-w-0">
          <div className="flex items-center space-x-2 text-[10px] font-mono text-cyan-400/80 uppercase tracking-widest hidden sm:flex">
            <span>TOWER AI</span>
            <span>/</span>
            <span>SYSTEM CONSOLE</span>
          </div>
          <h2 className="text-lg md:text-xl font-bold text-white tracking-tight truncate">{title}</h2>
        </div>
      </div>

      <div className="flex items-center space-x-3 md:space-x-4">
        {/* Live System Status Indicator */}
        <div className="hidden sm:flex items-center space-x-2 px-3 py-1 rounded-full bg-emerald-950/60 border border-emerald-500/30 text-emerald-400 text-xs font-semibold shadow-sm shadow-emerald-950">
          <Activity className="w-3.5 h-3.5 animate-pulse" />
          <span>Spring AI Agentic Core Online</span>
        </div>

        {/* User Profile */}
        <div className="flex items-center space-x-3 pl-3 md:pl-4 border-l border-slate-800/80">
          <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-cyan-500 via-blue-600 to-indigo-600 flex items-center justify-center font-bold text-white text-xs shadow-md shadow-cyan-500/20 ring-1 ring-cyan-400/30">
            {user?.username ? user.username.substring(0, 2).toUpperCase() : 'US'}
          </div>
          <div className="hidden md:block">
            <p className="text-xs font-semibold text-slate-200 leading-tight">{user?.username}</p>
            <p className="text-[10px] text-slate-400 font-mono truncate max-w-[140px]">{user?.email}</p>
          </div>
        </div>
      </div>
    </header>
  );
};

export default Header;
