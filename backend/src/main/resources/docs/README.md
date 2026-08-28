# AI Supply Chain Control Tower

An enterprise-grade, AI-driven Supply Chain Control Tower built with **Spring Boot 3**, **Spring AI (Gemini)**, **PostgreSQL (pgvector)**, and **React**.

---

## Phase 2 Architecture & Intelligence Data Flow

```
+-----------------------------------------------------------------------------------+
|                            POSTGRESQL DATABASE (1,528 Records)                    |
| (categories, products, suppliers, supplier_products, warehouses, inventories,      |
|  customer_orders, order_items, shipments, risk_alerts, vector_store)            |
+-----------------------------------------------------------------------------------+
                                       |
                                       v
+-----------------------------------------------------------------------------------+
|                      SUPPLY CHAIN INTELLIGENCE & ANALYTICS LAYER                  |
|                                                                                   |
|  1. DemandForecastingEngine (WMA + Exponential Smoothing + 95% Confidence Bounds) |
|  2. RiskAnalysisEngine (Inventory Risks, Supplier Risks, Logistics Delays)        |
|  3. SupplyChainIntelligenceService (KPI Aggregation, Prioritized Action Items)    |
+-----------------------------------------------------------------------------------+
                                       |
                                       v
+-----------------------------------------------------------------------------------+
|                        SPRING AI & GEMINI GROUNDING LAYER                         |
|  - Tools: InventoryTools, SupplierTools, LogisticsTools, ForecastTools, RiskTools |
|  - Grounded Prompts & Explainability Metadata (Problem, Metric Cause, Action)     |
+-----------------------------------------------------------------------------------+
                                       |
                                       v
+-----------------------------------------------------------------------------------+
|                      REACT CONTROL TOWER DASHBOARD & REST API                      |
|  - Risk Radar & Score (0-100), AI Executive Briefing, Forecast Explorer           |
|  - Explainability Modal ("Why was this recommendation generated?")                 |
+-----------------------------------------------------------------------------------+
```

---

## Forecasting Methodology & Statistical Bounds

Demand forecasting is performed by [`DemandForecastingEngine.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/analytics/DemandForecastingEngine.java):

1. **Deterministic Historical Demand Aggregation**:
   - Monthly sales are aggregated from `customer_orders` and `order_items` across the 12-month historical dataset.
2. **Hybrid Math Model**:
   - **3-Month Weighted Moving Average**: $WMA = 0.5 S_t + 0.3 S_{t-1} + 0.2 S_{t-2}$
   - **Single Exponential Smoothing**: $E_t = \alpha S_t + (1-\alpha) E_{t-1}$ with $\alpha = 0.3$
   - **Final Monthly Projection**: $F_{t+1} = \frac{WMA + E_t}{2}$
3. **95% Confidence Corridor**:
   - Calculated using standard deviation $s = \sqrt{\frac{\sum (S_i - \bar{S})^2}{n-1}}$
   - Upper/Lower bounds: $\text{Forecast} \pm 1.96 \times s$
4. **Daily Burn Rate & Stockout Horizon**:
   - Daily burn rate = $\frac{F_{t+1}}{30}$
   - Days until stockout = $\lfloor \frac{\text{Current Available Stock}}{\text{Daily Burn Rate}} \rfloor$
   - Stockout Warning triggered when $\text{Days Until Stockout} \le \text{Lead Time Days}$.

---

## Multi-Dimensional Risk Detection Engine

Real-time risk scoring is evaluated by [`RiskAnalysisEngine.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/analytics/RiskAnalysisEngine.java):

- **Inventory Risk**: Evaluates stockout risks ($\text{Stock} < \text{Safety Stock}$) and overstock accumulation ($\text{Stock} > 3 \times \text{Reorder Level}$).
- **Supplier Risk**: Detects vendor unreliability when $\text{Reliability Score} < 85\%$ or $\text{Delivery Performance} < 85\%$.
- **Shipment / Logistics Risk**: Detects active delays in cargo transit ($\text{Status} = \text{DELAYED}$ or $\text{ETA} < \text{Today}$).
- **Overall System Risk Score**: Weighted score ($0 - 100$) based on severity counts ($25 \times \text{Critical} + 10 \times \text{High} + 3 \times \text{Medium}$).

---

## Explainability Model

Every risk alert and AI recommendation includes structured explainability metadata:

1. **Problem Detected**: High-level anomaly description (e.g. *"Stockout Risk for product 'Microcontroller Board v2' at 'North Hub Distribution Center'"*).
2. **Underlying Database Metrics Cause**: Exact raw database values triggering the alert (e.g. *"Available Stock (34 units) is below Safety Stock (50 units) and Reorder Level (150 units)."*).
3. **Recommended Mitigation Action**: Actionable operational resolution step (e.g. *"Generate immediate Purchase Order for SKU 'SKU-ELEC-001' from preferred supplier and expedite delivery to warehouse 'North Hub Distribution Center'."*).

---

## Dataset Schema & CSV Ingestion

Datasets are stored in `backend/src/main/resources/datasets/` and ingested via REST API:

```bash
# 1-Click Sample Dataset Ingestion API
POST /api/data/import/sample-dataset
Header: Authorization: Bearer <JWT_TOKEN>
```

---

## Running with Docker Compose

```bash
# Clean build and start containers
docker compose up --build -d

# Verify container status
docker ps
```
- **Frontend Control Tower UI**: `http://localhost:3000`
- **Backend REST API**: `http://localhost:8080`
- **PostgreSQL Database**: `localhost:5432`

---

## Credentials

- **Admin**: `admin` / `admin123`
- **Supply Chain Manager**: `manager` / `manager123`
