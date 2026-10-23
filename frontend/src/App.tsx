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

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center text-slate-400">
        <div className="w-8 h-8 border-4 border-cyan-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  const isDemoTenant = user?.companyId === 1;

  return (
    <div className="flex min-h-screen bg-[#090d16] text-slate-100">
      <Sidebar />
      <div className="flex-1 flex flex-col min-w-0">
        {/* Dynamic Tenant Environment Transparency Banner */}
        {isDemoTenant ? (
          <div className="bg-amber-950/90 border-b border-amber-500/30 px-6 py-2 flex flex-wrap items-center justify-between text-xs text-amber-200 shadow-inner">
            <div className="flex items-center space-x-2">
              <span className="flex h-2 w-2 rounded-full bg-amber-400 animate-pulse" />
              <span className="font-bold uppercase tracking-wider text-amber-400">Demo Environment</span>
              <span className="text-amber-500/60">•</span>
              <span className="text-amber-200/90">
                Operating on synthetic demonstration dataset. Upload company operational data via <Link to="/import" className="underline font-semibold hover:text-amber-100">Ingestion Pipeline</Link>.
              </span>
            </div>
          </div>
        ) : (
          <div className="bg-slate-900/90 border-b border-slate-800 px-6 py-2 flex flex-wrap items-center justify-between text-xs text-slate-300">
            <div className="flex items-center space-x-2">
              <span className="flex h-2 w-2 rounded-full bg-emerald-400" />
              <span className="font-bold uppercase tracking-wider text-emerald-400">Active Tenant: {user?.companyName || 'Enterprise Workspace'}</span>
              <span className="text-slate-600">•</span>
              <span className="text-slate-400">
                Data-isolated PostgreSQL operational environment. Upload operational records via <Link to="/import" className="underline text-cyan-400 hover:text-cyan-300 font-semibold">Ingestion Pipeline</Link>.
              </span>
            </div>
          </div>
        )}
        <Header title={title} />
        <main className="p-6 flex-1 overflow-y-auto">{children}</main>
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
