# Production Deployment Guide — AI Supply Chain Control Tower

This guide details the single-command deployment architecture for the **AI Supply Chain Control Tower**.

---

## 🏗 Architecture Overview

```text
[ Internet Client ]
       │ HTTPS / HTTP
       ▼
[ Nginx Frontend (Port 3000 / 80) ]
       │ Internal Docker Network Proxy (/api/, /ws/, /actuator/)
       ▼
[ Spring Boot Backend (Port 8080) ]
       │ JDBC Connection
       ▼
[ PostgreSQL pgvector Database (Port 5432) ]
```

---

## 🚀 Quick Deployment Steps

### 1. Environment Setup
Copy the environment template and set secure production credentials:
```bash
cp .env.example .env
```

Edit `.env` to set:
- `POSTGRES_PASSWORD`: Strong database password
- `JWT_SECRET`: High-entropy 32+ character signing key
- `GEMINI_API_KEY`: (Optional) Google Gemini API Key for LLM RAG operations

### 2. Start Full Production Stack
Launch all 3 micro-containers via Docker Compose:
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

- **Frontend Application**: `http://localhost:3000` (or `http://YOUR_SERVER_IP:3000`)
- **Backend API**: `http://localhost:8080/api/public/simulation/executive/command-center`
- **Health Check**: `http://localhost:8080/actuator/health`

---

## 🔒 Security Governance
- All write/consequential execution endpoints (`POST /api/actions/{id}/approve`) require a Manager JWT Bearer token.
- Unauthenticated requests are rejected with `HTTP 403 Forbidden`.
