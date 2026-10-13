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
  ShieldCheck,
  Database,
  BarChart3,
  Activity,
  Building,
  LogOut
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export const Sidebar: React.FC = () => {
  const { user, logout } = useAuth();

  const primaryNavItems = [
    { name: 'Dashboard Overview', path: '/', icon: LayoutDashboard },
    { name: 'Data Import (CSV)', path: '/import', icon: Database },
    { name: 'AI Assistant', path: '/ai-assistant', icon: Bot },
    { name: 'Scenario Analytics', path: '/analytics', icon: BarChart3 },
  ];

  const operationalNavItems = [
    { name: 'Inventory Stock', path: '/inventory', icon: Boxes },
    { name: 'Products Catalog', path: '/products', icon: Package },
    { name: 'Suppliers & OTIF', path: '/suppliers', icon: Users },
    { name: 'Warehouse Hubs', path: '/warehouses', icon: Building2 },
    { name: 'Shipments & Freight', path: '/shipments', icon: Truck },
    { name: 'Action Center & Risks', path: '/action-center', icon: ShieldCheck },
  ];

  return (
    <aside className="w-64 glass-panel border-r border-slate-800 flex flex-col justify-between h-screen sticky top-0 z-30 select-none">
      <div className="flex flex-col flex-1 min-h-0">
        {/* Brand Header */}
        <div className="p-4 border-b border-slate-800/80 flex items-center space-x-3 shrink-0">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-cyan-500 via-blue-600 to-indigo-600 flex items-center justify-center shadow-lg shadow-cyan-500/20">
            <Bot className="w-5 h-5 text-white" />
          </div>
          <div className="min-w-0">
            <h1 className="text-sm font-bold text-white tracking-wider truncate">TOWER AI</h1>
            <p className="text-[11px] text-cyan-400 font-medium truncate">Supply Chain Intelligence</p>
          </div>
        </div>

        {/* Tenant & User Identification */}
        <div className="px-4 py-2.5 bg-slate-900/70 border-b border-slate-800/60 flex items-center justify-between text-xs shrink-0">
          <div className="flex items-center space-x-2 min-w-0">
            <Building className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
            <span className="text-slate-300 font-medium truncate" title={user?.companyName || 'Enterprise Workspace'}>
              {user?.companyName || 'Personal Workspace'}
            </span>
          </div>
          <span className="px-1.5 py-0.5 rounded bg-slate-800 text-[10px] font-mono font-bold text-slate-300 border border-slate-700 uppercase shrink-0">
            {user?.role?.replace('ROLE_', '') || 'USER'}
          </span>
        </div>

        {/* Grouped Navigation Links */}
        <nav className="p-3 space-y-4 overflow-y-auto flex-1 text-xs">
          {/* Section 1: Core Navigation */}
          <div className="space-y-1">
            <span className="px-3 text-[10px] font-bold text-cyan-400/90 tracking-wider uppercase block">
              Core Platform
            </span>
            <div className="space-y-0.5">
              {primaryNavItems.map((item) => (
                <NavLink
                  key={item.path}
                  to={item.path}
                  className={({ isActive }) =>
                    `flex items-center space-x-2.5 px-3 py-2 rounded-lg text-xs font-medium transition-all duration-150 ${
                      isActive
                        ? 'bg-cyan-500/15 text-cyan-300 border border-cyan-500/30 font-semibold shadow-inner'
                        : 'text-slate-300 hover:text-white hover:bg-slate-800/60'
                    }`
                  }
                >
                  <item.icon className="w-4 h-4 shrink-0 text-cyan-400" />
                  <span className="truncate">{item.name}</span>
                </NavLink>
              ))}
            </div>
          </div>

          {/* Section 2: Operational Data & Catalogs */}
          <div className="space-y-1 pt-1 border-t border-slate-800/50">
            <span className="px-3 text-[10px] font-bold text-slate-500 tracking-wider uppercase block">
              Operational Data &amp; Catalogs
            </span>
            <div className="space-y-0.5">
              {operationalNavItems.map((item) => (
                <NavLink
                  key={item.path}
                  to={item.path}
                  className={({ isActive }) =>
                    `flex items-center space-x-2.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all duration-150 ${
                      isActive
                        ? 'bg-slate-800 text-white font-semibold border border-slate-700'
                        : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
                    }`
                  }
                >
                  <item.icon className="w-3.5 h-3.5 shrink-0 text-slate-400" />
                  <span className="truncate text-[11px]">{item.name}</span>
                </NavLink>
              ))}
            </div>
          </div>
        </nav>
      </div>

      {/* Secondary System Diagnostics & Sign Out Footer */}
      <div className="p-3 border-t border-slate-800/80 space-y-2 shrink-0 bg-slate-950/40">
        <div className="px-2 pt-0.5 text-[9px] font-bold text-slate-500 uppercase tracking-wider">
          System Diagnostics
        </div>
        <NavLink
          to="/system-health"
          className={({ isActive }) =>
            `flex items-center space-x-2.5 px-3 py-1.5 rounded-lg text-xs transition-colors ${
              isActive
                ? 'bg-cyan-500/15 text-cyan-400 border border-cyan-500/30'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
            }`
          }
        >
          <Activity className="w-3.5 h-3.5 text-slate-400 shrink-0" />
          <span className="truncate text-[11px]">System Health & Telemetry</span>
        </NavLink>

        <button
          onClick={logout}
          className="w-full flex items-center justify-center space-x-2 px-3 py-1.5 rounded-lg bg-slate-900 hover:bg-rose-950/60 text-slate-400 hover:text-rose-300 border border-slate-800 hover:border-rose-900/50 transition-colors text-xs font-medium cursor-pointer"
        >
          <LogOut className="w-3.5 h-3.5" />
          <span>Sign Out ({user?.username})</span>
        </button>
      </div>
    </aside>
  );
};

export default Sidebar;
