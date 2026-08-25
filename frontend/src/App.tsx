import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
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
import { RecommendationsPage } from './pages/RecommendationsPage';
import { DocumentRagPage } from './pages/DocumentRagPage';

const ProtectedLayout: React.FC<{ title: string; children: React.ReactNode }> = ({ title, children }) => {
  const { token, isLoading } = useAuth();

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

  return (
    <div className="flex min-h-screen bg-[#090d16] text-slate-100">
      <Sidebar />
      <div className="flex-1 flex flex-col min-w-0">
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
          <Route path="/shipments" element={<ProtectedLayout title="Logistics & Shipment Delays"><ShipmentsPage /></ProtectedLayout>} />
          <Route path="/risks" element={<ProtectedLayout title="Operational Risk Monitor"><RisksPage /></ProtectedLayout>} />
          <Route path="/ai-assistant" element={<ProtectedLayout title="AI Control Center Workspace"><AiAssistantPage /></ProtectedLayout>} />
          <Route path="/recommendations" element={<ProtectedLayout title="Human-in-the-Loop Action Approvals"><RecommendationsPage /></ProtectedLayout>} />
          <Route path="/documents" element={<ProtectedLayout title="Supply Chain Policy RAG"><DocumentRagPage /></ProtectedLayout>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
