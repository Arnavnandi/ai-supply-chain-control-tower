# MCA Technical Documentation & Viva Presentation Guide
## AI Supply Chain Control Tower — Academic Project Documentation

---

## 1. System Architecture

The AI Supply Chain Control Tower is architected as a multi-tier, microservices-ready web application built using **React**, **TypeScript**, **Spring Boot**, **PostgreSQL**, **Docker**, and **Google Gemini 1.5 Flash (via Spring AI)**.

```
+---------------------------------------------------------------------------------------------------------+
|                                    REACT FRONTEND (TypeScript & Vite)                                   |
|  - App.tsx, Sidebar.tsx, Header.tsx                                                                     |
|  - Pages: DashboardPage, ProductsPage, InventoryPage, RisksPage, ActionCenterPage, AnalyticsPage, etc.  |
|  - Styling: Vanilla CSS / Tailwind Design Tokens (Dark Mode Glassmorphism)                              |
+---------------------------------------------------------------------------------------------------------+
                                                     |  HTTP REST (JSON / JWT)
                                                     v
+---------------------------------------------------------------------------------------------------------+
|                                        NGINX REVERSE PROXY                                              |
|  - Container: control-tower-frontend (Port 3000 -> Port 80)                                             |
|  - Default Config: nginx/default.conf                                                                   |
|  - Proxy Pass: Rewrites /api/* to http://backend:8080                                                    |
+---------------------------------------------------------------------------------------------------------+
                                                     |  HTTP REST (Internal Port 8080)
                                                     v
+---------------------------------------------------------------------------------------------------------+
|                                   SPRING BOOT REST BACKEND (Java 21)                                    |
|  - Security: SecurityConfig.java (Spring Security RBAC + JwtAuthenticationFilter)                       |
|  - REST Controllers:                                                                                    |
|      - AuthController.java (/api/auth/login, /api/auth/me)                                             |
|      - SupplyChainDataController.java (/api/data/*)                                                     |
|      - IntelligenceController.java (/api/intelligence/*, /api/forecast/*)                             |
|      - ActionApprovalController.java (/api/actions/*)                                                   |
|      - AdvancedAnalyticsController.java (/api/analytics/*)                                             |
|      - AiAgentController.java (/api/ai/*)                                                               |
|  - Analytical & Decision Engines:                                                                       |
|      - DemandForecastingEngine.java (Hybrid WMA + Exponential Smoothing)                                 |
|      - ForecastAccuracyEngine.java (Out-of-Sample MAPE & RMSE Backtesting)                              |
|      - InventoryOptimizationEngine.java (Dynamic Safety Stock: SS = Z * sigma_d * sqrt(L))              |
|      - RiskAnalysisEngine.java (Multi-Factor Operational Risk Scoring)                                  |
|      - PurchaseOrderGeneratorEngine.java (PO Payload Calculation)                                      |
|      - ActionExecutionEngine.java (Stock Replenishment & Order Persist)                                 |
|      - StressTestingEngine.java (What-If Disruption Shock Simulator)                                    |
|  - Services: CsvImportService, SeedDataService, ForecastService, ActionApprovalService                  |
+---------------------------------------------------------------------------------------------------------+
                                 |                                       |
                                 v                                       v
+-------------------------------------------------+     +-------------------------------------------------+
|          POSTGRESQL DATABASE (pgvector)         |     |         SPRING AI & GOOGLE GEMINI LAYER         |
|  - Container: control-tower-db (Port 5432)      |     |  - Model: Gemini 1.5 Flash                      |
|  - Database: postgres                           |     |  - Agents: AgentRouter, RiskAgent, etc.         |
|  - Entities: categories, products, suppliers,   |     |  - Tools: InventoryTools, ForecastTools,        |
|    supplier_products, warehouses, inventory,    |     |    SupplierTools, RiskTools, ActionTools        |
|    customer_orders, order_items, shipments,     |     |  - Grounding: Read-only Java Tool Execution     |
|    risk_alerts, recommendations, audit_logs     |     +-------------------------------------------------+
+-------------------------------------------------+
```

### Layer Components & Classes

| Layer | Key File / Class Name | Primary Responsibility |
| :--- | :--- | :--- |
| **Frontend UI** | [`App.tsx`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/frontend/src/App.tsx) | Application routing, theme context, protected page layout wrapper |
| **Frontend UI** | [`AnalyticsPage.tsx`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/frontend/src/pages/AnalyticsPage.tsx) | Renders Forecast Backtesting, Safety Stock Optimization, and What-If Simulator |
| **Frontend UI** | [`ActionCenterPage.tsx`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/frontend/src/pages/ActionCenterPage.tsx) | Renders Human-in-the-Loop decision cards, approval buttons, and live audit trail |
| **Reverse Proxy** | `nginx/default.conf` | Routes static Single Page Application (SPA) requests and proxies `/api` to Spring Boot |
| **Security** | [`SecurityConfig.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/config/SecurityConfig.java) | Configures Spring Security Stateless JWT session handling and role-based access control (RBAC) |
| **REST Controller** | [`AdvancedAnalyticsController.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/controller/AdvancedAnalyticsController.java) | Exposes endpoints `/api/analytics/accuracy/{id}`, `/api/analytics/optimization`, `/api/analytics/simulate` |
| **REST Controller** | [`ActionApprovalController.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/controller/ActionApprovalController.java) | Exposes endpoints `/api/actions/pending`, `/api/actions/history`, `/api/actions/audit-logs`, `/approve`, `/reject` |
| **Analytics Engine** | [`DemandForecastingEngine.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/analytics/DemandForecastingEngine.java) | Computes Weighted Moving Average + Exponential Smoothing ($\alpha=0.3$) + 95% Corridor |
| **Analytics Engine** | [`ForecastAccuracyEngine.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/analytics/ForecastAccuracyEngine.java) | Evaluates out-of-sample prediction MAPE and RMSE on expanding window sales arrays |
| **Analytics Engine** | [`InventoryOptimizationEngine.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/analytics/InventoryOptimizationEngine.java) | Calculates dynamic safety stock $SS = Z \cdot \sigma_d \cdot \sqrt{L}$ and capital valuation |
| **Analytics Engine** | [`StressTestingEngine.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/analytics/StressTestingEngine.java) | Executes What-If scenario simulations (demand surges, lead time delays) |
| **Execution Engine** | [`ActionExecutionEngine.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/service/ActionExecutionEngine.java) | Increments PostgreSQL warehouse stock and persists customer order PO records |
| **Database** | PostgreSQL (`control-tower-db`) | Relational persistence store mapped via Spring Data JPA entities |
| **AI Integration** | `ActionTools.java`, `RiskAgent.java` | Spring AI function tool declarations grounding Gemini 1.5 Flash in backend telemetry |

---

## 2. Dataset Documentation

### File Paths, Structure, and Schemas
The application embeds a 12-month synthetic supply chain relational dataset under [`backend/src/main/resources/datasets/`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/resources/datasets/):

1. **`categories.csv`** (6 rows, 3 cols): `id`, `name`, `description`. Defines item categories.
2. **`products.csv`** (26 rows, 10 cols): `id`, `sku`, `name`, `description`, `price`, `reorder_level`, `safety_stock`, `lead_time_days`, `unit_of_measure`, `category_id`.
3. **`suppliers.csv`** (11 rows, 11 cols): `id`, `code`, `name`, `contact_person`, `email`, `phone`, `country`, `reliability_score`, `delivery_performance_pct`, `average_lead_time_days`, `lead_time_variance_days`.
4. **`supplier_products.csv`** (39 rows, 7 cols): `id`, `supplier_id`, `product_id`, `contract_price`, `lead_time_days`, `minimum_order_quantity`, `is_preferred_supplier`.
5. **`warehouses.csv`** (5 rows, 9 cols): `id`, `code`, `name`, `location`, `total_capacity_units`, `current_utilization_units`, `utilization_percentage`, `manager_name`, `contact_email`.
6. **`inventories.csv`** (101 rows, 8 cols): `id`, `product_id`, `warehouse_id`, `quantity_available`, `reserved_quantity`, `reorder_level`, `safety_stock`, `last_restocked_at`.
7. **`orders.csv`** (310 rows, 7 cols): `id`, `order_number`, `customer_name`, `order_date`, `expected_delivery_date`, `status`, `total_amount`. Spans historical dates from Sept 2025 to Aug 2026.
8. **`order_items.csv`** (790 rows, 5 cols): `id`, `order_id`, `product_id`, `quantity`, `unit_price`.
9. **`shipments.csv`** (249 rows, 13 cols): `id`, `tracking_code`, `supplier_id`, `destination_warehouse_id`, `order_id`, `origin`, `destination`, `shipped_date`, `estimated_delivery_date`, `actual_delivery_date`, `status`, `delay_days`, `carrier_name`.

- **Total CSV Input Rows**: **1,537 rows** across 9 CSV files.
- **Date/Time Horizon**: 12 consecutive months (September 2025 – August 2026).
- **Ingestion & Normalization Class**: [`CsvImportService.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/service/CsvImportService.java) parses raw CSV strings using Apache Commons CSV, converts dates (`yyyy-MM-dd`), maps foreign key constraints (`product_id`, `warehouse_id`, `supplier_id`), and persists entities into PostgreSQL.

### Why 1,537 CSV Rows Result in 3,191 PostgreSQL Records
When the dataset is ingested, PostgreSQL maintains relational integrity across foreign key associations. In addition to primary entities (`orders`, `order_items`, `shipments`, `inventories`), application operations (such as executing purchase order approvals and logging system events) append new runtime records into `customer_orders`, `order_items`, `recommendations`, and `audit_logs`. The database currently holds **3,191 live records**.

---

## 3. Database Schema

```
+------------------+         +------------------+         +------------------+
|    categories    |         |     products     |         |    suppliers     |
+------------------+         +------------------+         +------------------+
| PK id            |<-------1| PK id            |1------->| PK id            |
|    name          |         |    sku           |         |    code          |
|    description   |         |    price         |         |    reliability_..|
+------------------+         +------------------+         +------------------+
                               |        |                          |
                               |        |                          |
                               v 1      v 1                        v 1
+------------------+         +------------+             +--------------------+
|    warehouses    |         | inventory  |             | supplier_products  |
+------------------+         +------------+             +--------------------+
| PK id            |<-------1| PK id      |             | PK id              |
|    code          |         | FK prod_id |             | FK supplier_id     |
|    total_capacity|         | FK wh_id   |             | FK product_id      |
+------------------+         +------------+             +--------------------+
                                |
                                v
+------------------+         +------------------+         +------------------+
| customer_orders  |1------->|   order_items    |         |    shipments     |
+------------------+         +------------------+         +------------------+
| PK id            |         | PK id            |         | PK id            |
|    order_number  |         | FK order_id      |         | FK order_id      |
|    status        |         | FK product_id    |         | FK supplier_id   |
+------------------+         +------------------+         +------------------+
```

### Core Tables & Purpose

1. **`products`**: Catalog of 25 SKUs containing standard lead-time days, reorder levels, safety stocks, unit prices, and unit of measure (UOM).
2. **`inventory`**: 100 stock records tracking `quantity_available`, `reserved_quantity`, and `last_restocked_at` per product per warehouse.
3. **`suppliers`**: 10 vendor records containing reliability scores (%), delivery performance (%), average lead time (days), and variance (days).
4. **`supplier_products`**: 116 contract pricing records linking suppliers to SKUs with contract unit price, lead time, MOQ, and preferred flag.
5. **`customer_orders` & `order_items`**: 311 customer orders and 2,369 order line items capturing historical monthly sales volume.
6. **`shipments`**: 248 logistics tracking records recording tracking code, carrier name, origin, destination, delay days, and shipment status (`IN_TRANSIT`, `DELIVERED`, `DELAYED`).
7. **`recommendations`**: AI-generated purchase order action proposals in `PENDING_APPROVAL`, `EXECUTED`, or `REJECTED` status.
8. **`audit_logs`**: Immutable audit trail logging user action, timestamp, entity affected, and JSON payload.

---

## 4. Demand Forecasting & Model Backtesting

### Algorithm Specification
The forecasting engine ([`DemandForecastingEngine.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/analytics/DemandForecastingEngine.java)) implements a **Hybrid Weighted Moving Average (WMA) + Exponential Smoothing** model:

1. **Weighted Moving Average (WMA)**:
   Assigns decaying weights to the 3 most recent historical months ($M_t, M_{t-1}, M_{t-2}$):
   $$\text{WMA} = 0.5 \cdot M_t + 0.3 \cdot M_{t-1} + 0.2 \cdot M_{t-2}$$

2. **Exponential Smoothing**:
   Applies smoothing factor $\alpha = 0.3$ against the previous baseline forecast $F_{t-1}$:
   $$F_t = \alpha \cdot M_t + (1 - \alpha) \cdot F_{t-1}$$

3. **Hybrid Forecast Combination**:
   $$\text{Forecast}_{30D} = \frac{\text{WMA} + F_t}{2}$$

4. **95% Confidence Corridor**:
   Using the historical demand standard deviation $s$:
   $$\text{Upper Corridor} = \text{Forecast} + 1.96 \cdot s$$
   $$\text{Lower Corridor} = \max(0, \text{Forecast} - 1.96 \cdot s)$$

### Out-of-Sample Backtesting (MAPE & RMSE)
In [`ForecastAccuracyEngine.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/analytics/ForecastAccuracyEngine.java), model performance is validated using out-of-sample expanding window backtesting:
- **Expanding Window**: Trains on past sub-series $[0..i-1]$ to predict month $[i]$ out-of-sample without data leakage.
- **Mean Absolute Percentage Error (MAPE)**:
  $$\text{MAPE} = \frac{1}{n} \sum_{i=1}^n \left| \frac{A_i - F_i}{A_i} \right| \times 100\%$$
- **Root Mean Squared Error (RMSE)**:
  $$\text{RMSE} = \sqrt{\frac{1}{n} \sum_{i=1}^n (A_i - F_i)^2}$$

### Numerical Calculation Example
For `Microcontroller Board v2` (`SKU-ELEC-001`) with actual sales $A = [384, 99, 626]$ and predictions $F = [112, 209, 176]$:
- $M_4$: $|384 - 112| / 384 = \mathbf{70.8\%}$
- $M_5$: $|99 - 209| / 99 = \mathbf{111.1\%}$
- $M_6$: $|626 - 176| / 626 = \mathbf{71.9\%}$
- $\text{MAPE} = \frac{70.8\% + 111.1\% + 71.9\%}{3} = \mathbf{84.6\%}$
- $\text{RMSE} = \sqrt{\frac{(384-112)^2 + (99-209)^2 + (626-176)^2}{3}} = \sqrt{\frac{73984 + 12100 + 202500}{3}} = \mathbf{310.2}$

---

## 5. Dynamic Safety Stock & Inventory Optimization

### Industrial Engineering Formula
In [`InventoryOptimizationEngine.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/analytics/InventoryOptimizationEngine.java):

$$SS = Z \cdot \sigma_d \cdot \sqrt{L}$$

- **$Z = 1.65$**: Constant factor representing a 95% Service Level under standard normal distribution.
- **$\sigma_d$**: Standard deviation of monthly customer sales demand (computed from PostgreSQL `order_items`).
- **$L$**: Supplier lead time in months ($L = \text{lead\_time\_days} / 30.0$, queried from PostgreSQL `products`).

### Reorder Point & Replenishment Logic
$$\text{Reorder Point (ROP)} = SS + (\text{Mean Monthly Demand} \cdot L)$$
- **Stockout Risk**: Triggered when `quantity_available < SS`. Generates an alert and automated PO recommendation.
- **Excess Inventory Risk**: Triggered when `quantity_available > (ROP * 2.5)`. Computes excess capital valuation:
  $$\text{Capital Potential} = (\text{Quantity} - \text{Optimal ROP}) \cdot \text{Contract Price}$$

---

## 6. What-If Supply Chain Stress Testing Simulator

In [`StressTestingEngine.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/analytics/StressTestingEngine.java), managers run real-time stress testing simulations without mutating baseline database records:

1. **Simulation Inputs**:
   - `demandSurgePercentage`: Simulated market demand increase (+0% to +100%).
   - `supplierLeadTimeDelayDays`: Overseas procurement delay (+0 to +14 days).
2. **Dynamic Calculation**:
   $$\text{Simulated 30D Demand} = \text{Baseline Demand} \cdot \left(1.0 + \frac{\text{Surge\%}}{100.0}\right)$$
   $$\text{Effective Safety Stock} = \text{Baseline SS} \cdot \left(1.0 + \frac{\text{Delay Days}}{10.0}\right)$$
3. **Outputs**:
   - Simulated System Risk Score (0–100, CRITICAL / HIGH / MODERATE).
   - Projected Stockout Item Count.
   - Projected Financial Risk Exposure ($).

---

## 7. Gemini / LLM Layer Architecture

### What Gemini DOES Do
- Accepts natural-language user queries in the **AI Control Center** (`/ai-assistant`).
- Invokes declared Spring AI read-only Java tools (`InventoryTools`, `SupplierTools`, `ForecastTools`, `RiskTools`, `ActionTools`) via function calling.
- Synthesizes JSON outputs returned by Java analytical engines into natural-language executive briefings.

### What Gemini DOES NOT Do
- ❌ Does **NOT** train or host any machine learning models.
- ❌ Does **NOT** calculate demand forecasts directly (calls `DemandForecastingEngine`).
- ❌ Does **NOT** directly modify or update database records.
- ❌ Does **NOT** execute purchase order approvals without human manager sign-off.

### End-to-End Trace Example

```
USER QUESTION: "What are the active stockout risks and recommended purchase orders?"
                           |
                           v
1. FRONTEND: AiAssistantPage sends query via POST /api/ai/query
                           |
                           v
2. SPRING BOOT & GEMINI: AgentRouter passes prompt + tool declarations to Gemini 1.5 Flash API
                           |
                           v
3. TOOL EXECUTION: Gemini requests tool call `getPendingActionRecommendations()`
                           |
                           v
4. JAVA ENGINE: ActionTools executes `ActionApprovalService.getPendingRecommendations()`
                           |
                           v
5. POSTGRESQL: Queries `recommendations` table WHERE status = 'PENDING_APPROVAL'
                           |
                           v
6. RETURN DATA: Java returns JSON array to Gemini
                           |
                           v
7. EXECUTIVE SYNTHESIS: Gemini formats answer: "There is 1 active pending purchase order..."
                           |
                           v
8. FRONTEND DISPLAY: AiAssistantPage renders natural-language response + interactive recommendation card
```

---

## 8. Why the System is Genuinely Data-Driven

The platform is **genuinely data-driven** because every calculation, risk score, forecast curve, MAPE metric, dynamic safety stock level, and purchase order quantity is computed dynamically from **3,191 live records in PostgreSQL**. Modifying data in PostgreSQL immediately alters all analytical outputs across the application.

---

## 9. Why Machine Learning is Not Currently Used

Classical statistical time-series algorithms (WMA + Exponential Smoothing) and industrial engineering formulas ($SS = Z \cdot \sigma_d \cdot \sqrt{L}$) were selected because:
1. **Explainability**: Statistical models provide transparent, step-by-step mathematical formulas suitable for auditing and academic presentation.
2. **Dataset Size**: A 12-month historical dataset provides insufficient volume for deep learning / neural network training without severe overfitting.
3. **Determinism**: Statistical models guarantee deterministic, repeatable results required for supply chain operations.

---

## 10. Important Academic Definitions

1. **Demand Forecasting**: Predicting future customer demand over a specified time horizon using historical sales data and statistical methods.
2. **Time-Series Forecasting**: Predicting future values of a variable based sequentially on past chronologically ordered observations.
3. **Weighted Moving Average (WMA)**: A forecasting technique assigning higher mathematical weights to more recent historical periods.
4. **Exponential Smoothing**: A time-series forecasting method applying exponentially decreasing weights to past observations using a smoothing factor $\alpha$.
5. **Mean Absolute Percentage Error (MAPE)**: A metric measuring forecast accuracy as an average percentage of absolute errors relative to actual values.
6. **Root Mean Squared Error (RMSE)**: A standard metric measuring the square root of average squared differences between predicted and actual values.
7. **Safety Stock (SS)**: Buffer inventory held to protect against demand volatility and supplier lead-time delays.
8. **Reorder Point (ROP)**: The inventory level threshold that triggers a replenishment order ($ROP = SS + \text{Lead Time Demand}$).
9. **What-If Analysis**: A simulation technique evaluating potential outcomes under varied operational assumptions or disruption scenarios.
10. **Risk Score**: A composite numerical index (0–100) representing overall operational risk across inventory, suppliers, and logistics.
11. **AI-Assisted System**: A software architecture combining automated statistical analytics with LLM natural-language orchestration and Human-in-the-Loop approval workflows.
12. **Machine Learning (ML)**: Algorithms that learn statistical patterns from data to make predictions without being explicitly programmed.

---

## 11. 15 Key MCA Viva Questions & Answers

### Q1: What is the overall architecture of your application?
**Answer**: It is a 3-tier architecture with a React Single Page Application frontend served by Nginx, a Spring Boot REST API backend, and a PostgreSQL database. It integrates Google Gemini 1.5 Flash via Spring AI function calling for natural-language query routing.

### Q2: What datasets are used in this project?
**Answer**: A 12-month synthetic supply chain relational dataset comprising 1,537 rows across 9 CSV files (`categories`, `products`, `suppliers`, `supplier_products`, `warehouses`, `inventories`, `orders`, `order_items`, `shipments`).

### Q3: How is data imported into the PostgreSQL database?
**Answer**: Through [`CsvImportService.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/service/CsvImportService.java) using Apache Commons CSV parser, mapping raw CSV strings to Spring Data JPA entity repositories.

### Q4: Which forecasting algorithm is implemented?
**Answer**: A Hybrid Weighted Moving Average (weights 0.5, 0.3, 0.2) + Exponential Smoothing ($\alpha = 0.3$) model with a 95% Confidence Corridor ($F \pm 1.96 s$).

### Q5: How do you evaluate forecast accuracy?
**Answer**: Using out-of-sample expanding window backtesting in [`ForecastAccuracyEngine.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/analytics/ForecastAccuracyEngine.java), calculating Mean Absolute Percentage Error (MAPE) and Root Mean Squared Error (RMSE).

### Q6: What is the dynamic safety stock formula used in the application?
**Answer**: $SS = Z \cdot \sigma_d \cdot \sqrt{L}$, where $Z = 1.65$ (95% service level), $\sigma_d$ is monthly demand standard deviation, and $L$ is lead time in months.

### Q7: How does the Human-in-the-Loop (HITL) approval workflow work?
**Answer**: When an inventory stockout is detected, `ReplenishmentProposalService` generates a purchase order recommendation in `PENDING_APPROVAL` status. A manager must explicitly approve it via `POST /api/actions/{id}/approve`, which triggers `ActionExecutionEngine` to increment stock, persist a PO record, and log an immutable entry in `audit_logs`.

### Q8: Does Gemini AI directly execute purchase orders or modify database state?
**Answer**: No. Gemini function tools are strictly read-only. Action execution requires human manager authentication (`ROLE_ADMIN` / `ROLE_SUPPLY_CHAIN_MANAGER`).

### Q9: How is What-If stress testing implemented?
**Answer**: In [`StressTestingEngine.java`](file:///Users/arnavnandi/.gemini/antigravity-ide/scratch/ai-supply-chain-control-tower/backend/src/main/java/com/supplychain/controltower/analytics/StressTestingEngine.java), scenario parameters (demand surge %, lead-time delay days) dynamically scale demand and safety thresholds to project stockout counts and financial exposure without mutating baseline database state.

### Q10: How are Spring AI tools connected to Gemini?
**Answer**: Java methods are annotated as Spring `@Bean` function tools (`InventoryTools`, `ForecastTools`, `RiskTools`, `ActionTools`). Spring AI passes these tool declarations to Gemini, allowing Gemini to invoke backend methods dynamically during chat sessions.

### Q11: Is machine learning used for demand forecasting in this project?
**Answer**: No. Classical statistical time-series algorithms (WMA + Exponential Smoothing) were chosen for deterministic explainability and lightweight suitability on a 12-month dataset.

### Q12: How are API endpoints secured?
**Answer**: Via Spring Security (`SecurityConfig.java`) enforcing Stateless JWT authentication and Role-Based Access Control (`@PreAuthorize("hasAnyRole(...)")`).

### Q13: What happens when an action recommendation is rejected?
**Answer**: The recommendation status transitions from `PENDING_APPROVAL` to `REJECTED`, stock is not modified, and a `REJECTED_AI_RECOMMENDATION` audit record is persisted in `audit_logs`.

### Q14: How does the application prevent duplicate purchase order proposals?
**Answer**: `ReplenishmentProposalService` queries `recommendations` table before generating proposals. If an active `PENDING_APPROVAL` proposal already exists for that product and warehouse, creation of duplicates is skipped.

### Q15: Why is this system considered "genuinely data-driven"?
**Answer**: Because all risk scores, forecasts, MAPE backtest indices, dynamic safety stock levels, and purchase order quantities are calculated dynamically from 3,191 live records in PostgreSQL.
