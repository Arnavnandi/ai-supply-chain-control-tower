# AI Supply Chain Control Tower — Frontend Application

Modern enterprise Single Page Application (SPA) built with **React 19**, **TypeScript**, **Vite 8**, and **Tailwind CSS v4** featuring the **Premium Dark Control Tower** design system.

---

## 🏗 Architecture & Technologies

- **Core**: React 19 (`^19.2.8`), React DOM (`^19.2.8`), TypeScript (`~6.0.2`), Vite 8 (`^8.2.0`)
- **Routing**: React Router DOM 7 (`^7.18.2`) with client-side SPA routing and single-page fallback
- **Styling**: Tailwind CSS v4 (`^4.3.3`, `@tailwindcss/postcss`) with custom CSS custom properties (`index.css`)
- **Visuals**: Deep navy surfaces (`#060b14`, `#090f1d`), cyan/emerald accents, radar scan animations, glassmorphism panels
- **Charts**: Recharts (`^3.10.1`) for multi-warehouse inventory trends and demand forecasting vs. orders
- **Icons**: Lucide React (`^1.31.0`)
- **Networking**: Axios (`^1.19.0`) with JWT bearer injection interceptors and cold-start classified error handling
- **Linter**: Oxlint (`^1.75.0`)

---

## 📁 Directory Structure

```
frontend/
├── src/
│   ├── api/                   # Axios client instance, timeout logic, error classification
│   ├── components/            # Reusable UI components
│   │   ├── Header.tsx         # Top bar with tenant status, system health indicator, user profile
│   │   ├── Sidebar.tsx        # Navigation sidebar with responsive mobile slide-out drawer
│   │   └── ExplainabilityModal.tsx # Root-cause explainability modal for risk alerts
│   ├── context/               # AuthContext (JWT management, multi-factor OTP state)
│   ├── pages/                 # 14 functional control tower views
│   │   ├── DashboardPage.tsx  # Executive Control Tower overview & resiliency scorecard
│   │   ├── ProductsPage.tsx   # SKU catalog with debounced search
│   │   ├── InventoryPage.tsx  # Multi-warehouse stock tracking & adjustments
│   │   ├── SuppliersPage.tsx  # Vendor reliability & OTIF metrics
│   │   ├── WarehousesPage.tsx # Regional logistics hubs & capacity meters
│   │   ├── ShipmentsPage.tsx  # In-transit cargo tracking & carrier delay metrics
│   │   ├── RisksPage.tsx      # Operational risk alerts & mitigation recommendations
│   │   ├── ActionCenterPage.tsx # Human-in-the-Loop decision & action center
│   │   ├── AnalyticsPage.tsx  # Disruption stress-testing & simulation suite (9 tabs)
│   │   ├── AiAssistantPage.tsx # Multi-agent AI assistant workspace
│   │   ├── DocumentRagPage.tsx # Policy knowledge hybrid RRF RAG search
│   │   ├── SystemHealthDashboardPage.tsx # WebSocket telemetry terminal & actuator probes
│   │   ├── DataImportPage.tsx # 5-step CSV ingestion pipeline & upload history
│   │   └── LoginPage.tsx      # Multi-tenant login, registration, and 2FA OTP verification
│   ├── index.css              # Control tower design tokens and utilities
│   ├── App.tsx                # Client-side routing table and ProtectedLayout
│   └── main.tsx               # Application mount point
├── public/                    # Static assets
├── nginx/                     # Local Docker Nginx configuration
├── wrangler.json              # Cloudflare Workers production configuration
├── wrangler.staging.json      # Cloudflare Workers staging configuration
└── package.json               # Dependencies and scripts
```

---

## 🛠 Available Scripts

```bash
# Start local Vite development server (default development mode -> localhost:8080)
npm run dev

# Start local dev server connected to staging Render backend
npm run dev -- --mode staging

# Run unit and regression test suite (57 tests)
npm test

# Run linter
npm run lint

# TypeScript compilation check
npx tsc -b

# Build production bundle (dist/)
npm run build

# Build staging bundle (dist/ configured for staging Render backend)
npm run build -- --mode staging
```

---

## ☁️ Deployment

### Staging Deployment (Cloudflare Workers)
```bash
# 1. Build staging bundle
npm run build -- --mode staging

# 2. Deploy exclusively using staging configuration
npx wrangler deploy --config wrangler.staging.json
```
Target Worker: `ai-supply-chain-control-tower-staging`
Live URL: `https://ai-supply-chain-control-tower-staging.arnavnandi45.workers.dev`
