import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, Link } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Sidebar } from './components/Sidebar';
import { Header } from './components/Header';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { ProductsPage } from './pages/ProductsPage';
import { InventoryPage } from './pages/InventoryPage';
import { WarehousesPage } from './pages/WarehousesPage';
import { SuppliersPage } from './pages/SuppliersPage';
import { ShipmentsPage } from './pages/ShipmentsPage';
import { RisksPage } from './pages/RisksPage';
import { AiAssistantPage } from './pages/AiAssistantPage';
import { DocumentRagPage } from './pages/DocumentRagPage';
import { DataImportPage } from './pages/DataImportPage';
import { ActionCenterPage } from './pages/ActionCenterPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { SystemHealthDashboardPage } from './pages/SystemHealthDashboardPage';

const ProtectedLayout: React.FC<{ title: string; children: React.ReactNode }> = ({ title, children }) => {
  const { token, user, isLoading } = useAuth();
  const [mobileMenuOpen, setMobileMenuOpen] = React.useState(false);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#060b14] flex flex-col items-center justify-center text-slate-400 space-y-4">
        <div className="relative">
          <div className="w-12 h-12 border-4 border-cyan-500/20 border-t-cyan-400 rounded-full animate-spin" />
          <div className="absolute inset-0 w-12 h-12 rounded-full blur-md bg-cyan-500/20" />
        </div>
        <p className="text-xs font-mono text-cyan-400 tracking-widest uppercase">Initializing Control Tower...</p>
      </div>
    );
  }

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  const isDemoTenant = user?.companyId === 1;

  return (
    <div className="flex min-h-screen bg-[#060b14] text-slate-100">
      <Sidebar isMobileOpen={mobileMenuOpen} onCloseMobile={() => setMobileMenuOpen(false)} />
      <div className="flex-1 flex flex-col min-w-0">
        {/* Dynamic Tenant Environment Transparency Banner */}
        {isDemoTenant ? (
          <div className="bg-gradient-to-r from-amber-950/90 via-amber-900/80 to-amber-950/90 border-b border-amber-500/30 px-4 md:px-6 py-2 flex flex-wrap items-center justify-between text-xs text-amber-200 shadow-inner">
            <div className="flex items-center space-x-2.5">
              <span className="flex h-2 w-2 rounded-full bg-amber-400 animate-pulse shadow-[0_0_8px_rgba(251,191,36,0.8)]" />
              <span className="font-bold uppercase tracking-wider text-amber-400 font-mono text-[11px]">Demo Environment</span>
              <span className="text-amber-500/60">•</span>
              <span className="text-amber-200/90 text-xs">
                Operating on synthetic demonstration dataset. Upload company operational data via <Link to="/import" className="underline font-semibold hover:text-amber-100 text-amber-300">Ingestion Pipeline</Link>.
              </span>
            </div>
            <span className="hidden lg:inline-block font-mono text-[10px] text-amber-400/80 uppercase tracking-widest">
              TENANT: DEMO-CORP (ID: 1)
            </span>
          </div>
        ) : (
          <div className="bg-gradient-to-r from-[#0d1c33]/90 via-[#0a182d]/90 to-[#0d1c33]/90 border-b border-cyan-500/20 px-4 md:px-6 py-2 flex flex-wrap items-center justify-between text-xs text-slate-300 shadow-sm">
            <div className="flex items-center space-x-2.5">
              <span className="flex h-2 w-2 rounded-full bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.8)]" />
              <span className="font-bold uppercase tracking-wider text-emerald-400 font-mono text-[11px]">Active Tenant: {user?.companyName || 'Enterprise Workspace'}</span>
              <span className="text-slate-600">•</span>
              <span className="text-slate-300 text-xs">
                Data-isolated PostgreSQL operational environment. Upload operational records via <Link to="/import" className="underline text-cyan-400 hover:text-cyan-300 font-semibold">Ingestion Pipeline</Link>.
              </span>
            </div>
            <span className="hidden lg:inline-block font-mono text-[10px] text-cyan-400 uppercase tracking-widest">
              TENANT CODE: {user?.companyCode || 'PROD'}
            </span>
          </div>
        )}
        <Header title={title} onToggleMobileMenu={() => setMobileMenuOpen(prev => !prev)} />
        <main className="p-4 sm:p-6 lg:p-8 flex-1 overflow-y-auto">{children}</main>
      </div>
    </div>
  );
};

export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<ProtectedLayout title="Executive Control Tower"><DashboardPage /></ProtectedLayout>} />
          <Route path="/products" element={<ProtectedLayout title="Products & SKU Catalog"><ProductsPage /></ProtectedLayout>} />
          <Route path="/inventory" element={<ProtectedLayout title="Multi-Warehouse Inventory Stock"><InventoryPage /></ProtectedLayout>} />
          <Route path="/warehouses" element={<ProtectedLayout title="Warehouse Facilities"><WarehousesPage /></ProtectedLayout>} />
          <Route path="/suppliers" element={<ProtectedLayout title="Supplier Performance Index"><SuppliersPage /></ProtectedLayout>} />
          <Route path="/shipments" element={<ProtectedLayout title="Logistics & Shipment Freight"><ShipmentsPage /></ProtectedLayout>} />
          <Route path="/risks" element={<ProtectedLayout title="Operational Risk Monitor"><RisksPage /></ProtectedLayout>} />
          <Route path="/analytics" element={<ProtectedLayout title="Analytics & Stress-Testing Simulator"><AnalyticsPage /></ProtectedLayout>} />
          <Route path="/system-health" element={<ProtectedLayout title="System Health & Live Telemetry"><SystemHealthDashboardPage /></ProtectedLayout>} />
          <Route path="/import" element={<ProtectedLayout title="Dataset Ingestion & Pipeline"><DataImportPage /></ProtectedLayout>} />
          <Route path="/ai-assistant" element={<ProtectedLayout title="AI Assistant Workspace"><AiAssistantPage /></ProtectedLayout>} />
          <Route path="/recommendations" element={<Navigate to="/action-center" replace />} />
          <Route path="/action-center" element={<ProtectedLayout title="AI Decision & Action Center (HITL)"><ActionCenterPage /></ProtectedLayout>} />
          <Route path="/documents" element={<ProtectedLayout title="Supply Chain Policy RAG"><DocumentRagPage /></ProtectedLayout>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
