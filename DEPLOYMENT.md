# Deployment Guide — AI Supply Chain Control Tower

This guide details the deployment architecture for both local containerized environments and the cloud staging deployment.

---

## 🏗 Architecture Overview

```text
[ Internet Client ]
       │ HTTPS / WSS
       ▼
[ Frontend Layer ]
  - Staging: Cloudflare Workers (Static SPA + React Router DOM)
  - Local:   Nginx Container (Port 3000 -> 80)
       │ Proxy / Direct REST & WebSocket (/api/, /ws/telemetry)
       ▼
[ Spring Boot Backend (Port 8080) ]
  - Staging: Render Web Service (Java 21, Spring Boot 3.3.4)
  - Local:   Docker Container (control-tower-backend)
  - Decision Intelligence & Spring AI Multi-Agent Consensus
  - Hybrid RRF RAG Retrieval (Gemini Embeddings + Lexical Fallback)
       │ JDBC Connection
       ▼
[ PostgreSQL pgvector Database (Port 5432) ]
  - Multi-tenant data partitioning by company_id
  - Vector similarity search (HNSW index, 768 dimensions)
```

---

## ☁️ Staging Environment

- **Frontend URL**: `https://ai-supply-chain-control-tower-staging.arnavnandi45.workers.dev`
- **Backend URL**: `https://ai-supply-chain-control-tower-backend-nkw0.onrender.com`
- **WebSocket Telemetry**: `wss://ai-supply-chain-control-tower-backend-nkw0.onrender.com/ws/telemetry`
- **Actuator Health**: `https://ai-supply-chain-control-tower-backend-nkw0.onrender.com/actuator/health`

### Staging Frontend Build & Deploy
Always use `wrangler.staging.json` (or `npm run deploy:staging`) for staging deployments to guarantee the production Worker is never targeted:

```bash
cd frontend

# Automated staging deploy (builds with --mode staging and targets wrangler.staging.json)
npm run deploy:staging

# Alternatively, step-by-step:
npm run build:staging
npx wrangler deploy --config wrangler.staging.json
```

> [!NOTE]
> **Render Free/Hobby Tier Cold Starts**: On free/hobby hosting tiers, the backend container spins down after 15 minutes of inactivity. Initial request wake-up takes 30–60 seconds. The staging frontend bundle is pre-configured with a 60-second request timeout (`STAGING_TIMEOUT = 60000`) and automatic retry prompts.

---

## 🚀 Local Docker Deployment

### 1. Environment Setup
Copy the environment template and set credentials:
```bash
cp .env.example .env
```

Key environment configuration variables:
- `SPRING_DATASOURCE_URL`: `jdbc:postgresql://postgres:5432/postgres`
- `SPRING_DATASOURCE_USERNAME`: `postgres`
- `SPRING_DATASOURCE_PASSWORD`: Secure database password placeholder
- `JWT_SECRET`: High-entropy 32+ character signing key placeholder
- `EMBEDDING_PROVIDER`: `gemini` (active default for 768-dim pgvector)
- `GEMINI_API_KEY`: Google Gemini API Key for LLM reasoning and embedding generation

### 2. Start Complete Application Stack
Launch all micro-containers via Docker Compose:
```bash
docker compose up -d --build
```

### 3. Verify Container Status
```bash
docker compose ps
```
Expected output:
- `control-tower-frontend`: Up (Port `3000`)
- `control-tower-backend`: Up (Port `8080`)
- `control-tower-db`: Up / Healthy (Port `5432`)

---

## 🌐 Endpoints & Health Verification

- **Frontend Application**: `http://localhost:3000`
- **Health Check (Unauthenticated)**: `http://localhost:8080/actuator/health`
- **Simulation & Analytics APIs (Authenticated)**:
  All analytical and simulation endpoints require an authenticated user JWT Bearer token to establish the caller's tenant context (`TenantContext`):
  ```bash
  # 1. Obtain token via authentication
  TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"usernameOrEmail":"admin","password":"admin123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

  # 2. Query tenant-scoped simulation scorecard
  curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/public/simulation/executive/command-center
  ```
  *(Note: Calling operational or simulation endpoints without a Bearer token returns `HTTP 403 Forbidden` due to strict tenant isolation).*

---

## 🔒 Security & Multi-Tenant Governance
- **Consequential Actions**: All write actions (`POST /api/actions/{id}/approve`) require an authenticated Manager JWT Bearer token (`ROLE_SUPPLY_CHAIN_MANAGER` or `ROLE_ADMIN`).
- **Tenant Context**: All operational records and analytical engines are strictly scoped to the authenticated user's company context via `TenantContext`.
- **Zero Unauthenticated Execution**: Unauthenticated action execution requests and operational API calls without valid tenant context are rejected with `HTTP 403 Forbidden`.
