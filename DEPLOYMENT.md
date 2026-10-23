# Deployment Guide — AI Supply Chain Control Tower

This guide details the deployment architecture for both local containerized environments and cloud staging.

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
  - Decision Intelligence & Spring AI Agent Consensus
  - Hybrid RRF RAG Retrieval (Gemini Embeddings + Lexical Fallback)
       │ JDBC Connection
       ▼
[ PostgreSQL pgvector Database (Port 5432) ]
  - Multi-tenant data partitioning by company
  - Vector similarity search (HNSW index, 768 dimensions)
```

---

## ☁️ Staging Environment

- **Frontend URL**: `https://ai-supply-chain-control-tower-staging.arnavnandi45.workers.dev`
- **Backend URL**: `https://ai-supply-chain-control-tower-backend-nkw0.onrender.com`
- **WebSocket Telemetry**: `wss://ai-supply-chain-control-tower-backend-nkw0.onrender.com/ws/telemetry?token=<JWT_TOKEN>`
- **Actuator Health**: `https://ai-supply-chain-control-tower-backend-nkw0.onrender.com/actuator/health`

### Staging Frontend Build & Deploy
```bash
cd frontend
npm run build -- --mode staging
# Deploy to Cloudflare Workers staging target:
# npx wrangler deploy
```

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
- `SPRING_DATASOURCE_PASSWORD`: Secure database password
- `JWT_SECRET`: High-entropy 32+ character signing key
- `EMBEDDING_PROVIDER`: `gemini` (default) or `onnx` (legacy local)
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
- **Backend API**: `http://localhost:8080/api/public/simulation/executive/command-center`
- **Health Check**: `http://localhost:8080/actuator/health`

---

## 🔒 Security & Multi-Tenant Governance
- **Consequential Actions**: All write actions (`POST /api/actions/{id}/approve`) require an authenticated Manager JWT Bearer token.
- **Tenant Context**: All operational records are strictly scoped to the authenticated user's company context via `TenantContext`.
- **Zero Unauthenticated Execution**: Unauthenticated action execution requests are rejected with `HTTP 403 Forbidden`.
