import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Package,
  Boxes,
  Truck,
  Building2,
  Users,
  Bot,
  CheckSquare,
  FileText,
  AlertTriangle,
  LogOut,
  ShieldCheck
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export const Sidebar: React.FC = () => {
  const { user, logout } = useAuth();

  const navItems = [
    { name: 'Control Tower', path: '/', icon: LayoutDashboard },
    { name: 'Products', path: '/products', icon: Package },
    { name: 'Inventory Stock', path: '/inventory', icon: Boxes },
    { name: 'Warehouses', path: '/warehouses', icon: Building2 },
    { name: 'Suppliers', path: '/suppliers', icon: Users },
    { name: 'Active Shipments', path: '/shipments', icon: Truck },
    { name: 'Risk Monitor', path: '/risks', icon: AlertTriangle },
    { name: 'AI Control Center', path: '/ai-assistant', icon: Bot, highlight: true },
    { name: 'Action Approvals', path: '/recommendations', icon: CheckSquare },
    { name: 'Policy Knowledge RAG', path: '/documents', icon: FileText },
  ];

  return (
    <aside className="w-64 glass-panel border-r border-slate-800 flex flex-col justify-between h-screen sticky top-0 z-30">
      <div>
        {/* Brand Header */}
        <div className="p-5 border-b border-slate-800/80 flex items-center space-x-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-cyan-500 via-blue-600 to-indigo-600 flex items-center justify-center shadow-lg shadow-cyan-500/20">
            <Bot className="w-6 h-6 text-white" />
          </div>
          <div>
            <h1 className="text-lg font-bold text-white tracking-wide">TOWER AI</h1>
            <p className="text-xs text-cyan-400 font-medium">Supply Chain Intelligence</p>
          </div>
        </div>

        {/* User Role Badge */}
        <div className="px-5 py-3 bg-slate-900/60 border-b border-slate-800/40 flex items-center space-x-2">
          <ShieldCheck className="w-4 h-4 text-emerald-400" />
          <span className="text-xs text-slate-300 font-semibold truncate">{user?.username} ({user?.role?.replace('ROLE_', '')})</span>
        </div>

        {/* Nav Links */}
        <nav className="p-3 space-y-1 overflow-y-auto max-h-[calc(100vh-220px)]">
          {navItems.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                `flex items-center space-x-3 px-3.5 py-2.5 rounded-lg text-sm font-medium transition-all duration-150 ${
                  isActive
                    ? 'bg-cyan-500/15 text-cyan-400 border border-cyan-500/30 font-semibold shadow-inner'
                    : item.highlight
                    ? 'text-cyan-300 bg-cyan-950/40 hover:bg-cyan-900/50 border border-cyan-800/40'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
                }`
              }
            >
              <item.icon className={`w-4 h-4 ${item.highlight ? 'text-cyan-400 animate-pulse' : ''}`} />
              <span>{item.name}</span>
            </NavLink>
          ))}
        </nav>
      </div>

      {/* Logout */}
      <div className="p-4 border-t border-slate-800/80">
        <button
          onClick={logout}
          className="w-full flex items-center justify-center space-x-2 px-4 py-2 rounded-lg bg-slate-800/80 hover:bg-rose-950/60 text-slate-300 hover:text-rose-300 border border-slate-700/50 transition-colors text-sm font-medium"
        >
          <LogOut className="w-4 h-4" />
          <span>Sign Out</span>
        </button>
      </div>
    </aside>
  );
};
