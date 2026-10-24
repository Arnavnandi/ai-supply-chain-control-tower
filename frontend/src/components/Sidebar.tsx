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
  ShieldAlert,
  Database,
  BarChart3,
  Activity,
  Building,
  LogOut,
  BookOpen,
  X
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

interface SidebarProps {
  isMobileOpen?: boolean;
  onCloseMobile?: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ isMobileOpen, onCloseMobile }) => {
  const { user, logout } = useAuth();

  const primaryNavItems = [
    { name: 'Dashboard Overview', path: '/', icon: LayoutDashboard },
    { name: 'Data Import (CSV)', path: '/import', icon: Database },
    { name: 'AI Assistant', path: '/ai-assistant', icon: Bot },
    { name: 'Scenario Analytics', path: '/analytics', icon: BarChart3 },
    { name: 'Policy Knowledge RAG', path: '/documents', icon: BookOpen },
  ];

  const operationalNavItems = [
    { name: 'Inventory Stock', path: '/inventory', icon: Boxes },
    { name: 'Products Catalog', path: '/products', icon: Package },
    { name: 'Suppliers & OTIF', path: '/suppliers', icon: Users },
    { name: 'Warehouse Hubs', path: '/warehouses', icon: Building2 },
    { name: 'Shipments & Freight', path: '/shipments', icon: Truck },
    { name: 'Operational Risks', path: '/risks', icon: ShieldAlert },
    { name: 'Action Center & HITL', path: '/action-center', icon: ShieldCheck },
  ];

  return (
    <>
      {/* Mobile Backdrop Overlay */}
      {isMobileOpen && (
        <div
          className="fixed inset-0 bg-black/70 backdrop-blur-sm z-40 md:hidden transition-opacity"
          onClick={onCloseMobile}
          aria-hidden="true"
        />
      )}

      <aside
        className={`w-64 glass-panel border-r border-slate-800/80 flex flex-col justify-between h-screen select-none transition-transform duration-300 ease-in-out z-50 md:z-30 ${
          isMobileOpen
            ? 'fixed inset-y-0 left-0 bg-[#090f1d] translate-x-0 shadow-2xl'
            : 'fixed inset-y-0 left-0 -translate-x-full md:relative md:translate-x-0 md:sticky md:top-0'
        }`}
      >
        <div className="flex flex-col flex-1 min-h-0">
          {/* Brand Header */}
          <div className="p-4 border-b border-cyan-500/10 flex items-center justify-between shrink-0 bg-slate-950/40">
            <div className="flex items-center space-x-3 min-w-0">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-cyan-500 via-blue-600 to-indigo-600 flex items-center justify-center shadow-lg shadow-cyan-500/25 ring-1 ring-cyan-400/30">
                <Bot className="w-5 h-5 text-white" />
              </div>
              <div className="min-w-0">
                <div className="flex items-center space-x-1.5">
                  <h1 className="text-sm font-black text-white tracking-widest uppercase">TOWER AI</h1>
                  <span className="h-1.5 w-1.5 rounded-full bg-cyan-400 animate-pulse" />
                </div>
                <p className="text-[10px] text-cyan-400/90 font-medium tracking-wider uppercase truncate">Control Tower v2.4</p>
              </div>
            </div>
            {onCloseMobile && (
              <button
                id="mobile-menu-close-button"
                onClick={onCloseMobile}
                className="md:hidden p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800/80 transition-colors cursor-pointer"
                aria-label="Close navigation menu"
              >
                <X className="w-5 h-5" />
              </button>
            )}
          </div>

          {/* Tenant & User Identification */}
          <div className="px-3.5 py-2.5 bg-[#0b1324]/90 border-b border-cyan-500/10 flex items-center justify-between text-xs shrink-0">
            <div className="flex items-center space-x-2 min-w-0">
              <div className="w-6 h-6 rounded-md bg-cyan-950/60 border border-cyan-500/30 flex items-center justify-center shrink-0">
                <Building className="w-3.5 h-3.5 text-cyan-400" />
              </div>
              <div className="min-w-0">
                <p className="text-[11px] font-semibold text-slate-200 truncate leading-tight" title={user?.companyName || 'Enterprise Workspace'}>
                  {user?.companyName || 'Personal Workspace'}
                </p>
                <p className="text-[9px] font-mono text-cyan-400/80 tracking-wider uppercase">
                  TENANT: {user?.companyCode || 'DEMO'}
                </p>
              </div>
            </div>
            <span className="px-1.5 py-0.5 rounded bg-cyan-950/80 text-[9px] font-mono font-bold text-cyan-300 border border-cyan-500/30 uppercase shrink-0">
              {user?.role?.replace('ROLE_', '') || 'USER'}
            </span>
          </div>

          {/* Grouped Navigation Links */}
          <nav className="p-3 space-y-4 overflow-y-auto flex-1 text-xs">
            {/* Section 1: Core Navigation */}
            <div className="space-y-1">
              <div className="flex items-center justify-between px-3 mb-1.5">
                <span className="text-[9px] font-bold text-cyan-400/90 tracking-widest uppercase block">
                  Core Platform
                </span>
                <span className="text-[9px] font-mono text-slate-500">SYS</span>
              </div>
              <div className="space-y-0.5">
                {primaryNavItems.map((item) => (
                  <NavLink
                    key={item.path}
                    to={item.path}
                    onClick={() => onCloseMobile && onCloseMobile()}
                    className={({ isActive }) =>
                      `group relative flex items-center space-x-2.5 px-3 py-2 rounded-lg text-xs font-medium transition-all duration-200 ${
                        isActive
                          ? 'bg-gradient-to-r from-cyan-500/20 to-blue-500/10 text-cyan-300 border border-cyan-500/40 font-semibold shadow-sm shadow-cyan-950'
                          : 'text-slate-300 hover:text-white hover:bg-slate-800/60 border border-transparent'
                      }`
                    }
                  >
                    {({ isActive }) => (
                      <>
                        {isActive && (
                          <span className="absolute left-0 top-1.5 bottom-1.5 w-1 rounded-r-full bg-cyan-400 shadow-[0_0_8px_rgba(6,182,212,0.8)]" />
                        )}
                        <item.icon className={`w-4 h-4 shrink-0 transition-colors ${isActive ? 'text-cyan-400' : 'text-slate-400 group-hover:text-cyan-300'}`} />
                        <span className="truncate">{item.name}</span>
                      </>
                    )}
                  </NavLink>
                ))}
              </div>
            </div>

            {/* Section 2: Operational Data & Catalogs */}
            <div className="space-y-1 pt-2 border-t border-slate-800/60">
              <div className="flex items-center justify-between px-3 mb-1.5">
                <span className="text-[9px] font-bold text-slate-400 tracking-widest uppercase block">
                  Operational Data &amp; Catalogs
                </span>
                <span className="text-[9px] font-mono text-slate-600">OPS</span>
              </div>
              <div className="space-y-0.5">
                {operationalNavItems.map((item) => (
                  <NavLink
                    key={item.path}
                    to={item.path}
                    onClick={() => onCloseMobile && onCloseMobile()}
                    className={({ isActive }) =>
                      `group relative flex items-center space-x-2.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all duration-200 ${
                        isActive
                          ? 'bg-slate-800/90 text-white font-semibold border border-cyan-500/30'
                          : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40 border border-transparent'
                      }`
                    }
                  >
                    {({ isActive }) => (
                      <>
                        {isActive && (
                          <span className="absolute left-0 top-1 bottom-1 w-0.5 rounded-r-full bg-cyan-400" />
                        )}
                        <item.icon className={`w-3.5 h-3.5 shrink-0 transition-colors ${isActive ? 'text-cyan-400' : 'text-slate-400 group-hover:text-slate-200'}`} />
                        <span className="truncate text-[11px]">{item.name}</span>
                      </>
                    )}
                  </NavLink>
                ))}
              </div>
            </div>
          </nav>
        </div>

        {/* Secondary System Diagnostics & Sign Out Footer */}
        <div className="p-3 border-t border-slate-800/80 space-y-2 shrink-0 bg-[#070c18]/90">
          <div className="px-2 pt-0.5 flex items-center justify-between text-[9px] font-bold text-slate-500 uppercase tracking-widest">
            <span>Diagnostics</span>
            <span className="flex items-center space-x-1 text-emerald-400 font-mono text-[8px]">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
              <span>LIVE</span>
            </span>
          </div>
          <NavLink
            to="/system-health"
            onClick={() => onCloseMobile && onCloseMobile()}
            className={({ isActive }) =>
              `flex items-center space-x-2.5 px-3 py-1.5 rounded-lg text-xs transition-all duration-150 ${
                isActive
                  ? 'bg-cyan-500/15 text-cyan-400 border border-cyan-500/30 font-semibold'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60 border border-transparent'
              }`
            }
          >
            <Activity className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
            <span className="truncate text-[11px]">System Health &amp; Telemetry</span>
          </NavLink>

          <button
            onClick={logout}
            className="w-full flex items-center justify-center space-x-2 px-3 py-1.5 rounded-lg bg-slate-900/90 hover:bg-rose-950/60 text-slate-400 hover:text-rose-300 border border-slate-800 hover:border-rose-900/50 transition-colors text-xs font-medium cursor-pointer"
          >
            <LogOut className="w-3.5 h-3.5" />
            <span>Sign Out ({user?.username})</span>
          </button>
        </div>
      </aside>
    </>
  );
};

export default Sidebar;
