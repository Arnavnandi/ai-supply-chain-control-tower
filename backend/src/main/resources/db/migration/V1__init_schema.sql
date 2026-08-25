-- V1__init_schema.sql: Initial database schema migration for AI Supply Chain Control Tower

CREATE EXTENSION IF NOT EXISTS vector;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL
);

-- Product Categories
CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT
);

-- SKU Products catalog
CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price NUMERIC(15, 2) NOT NULL,
    reorder_level INT NOT NULL DEFAULT 0,
    safety_stock INT NOT NULL DEFAULT 0,
    lead_time_days INT DEFAULT 7,
    unit_of_measure VARCHAR(50) DEFAULT 'Units',
    category_id BIGINT REFERENCES categories(id) ON DELETE SET NULL
);

-- Suppliers catalog
CREATE TABLE IF NOT EXISTS suppliers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    contact_person VARCHAR(100),
    email VARCHAR(150),
    phone VARCHAR(50),
    country VARCHAR(100),
    reliability_score NUMERIC(5, 2) DEFAULT 90.0,
    delivery_performance_pct NUMERIC(5, 2) DEFAULT 90.0,
    average_lead_time_days DOUBLE PRECISION DEFAULT 7.0,
    lead_time_variance_days DOUBLE PRECISION DEFAULT 1.0
);

-- Supplier Products contract pricing
CREATE TABLE IF NOT EXISTS supplier_products (
    id BIGSERIAL PRIMARY KEY,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    contract_price NUMERIC(15, 2) NOT NULL,
    lead_time_days INT NOT NULL,
    minimum_order_quantity INT DEFAULT 1,
    is_preferred_supplier BOOLEAN DEFAULT FALSE
);

-- Warehouses
CREATE TABLE IF NOT EXISTS warehouses (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    location VARCHAR(200),
    total_capacity_units INT NOT NULL DEFAULT 10000,
    current_utilization_units INT NOT NULL DEFAULT 0,
    utilization_percentage NUMERIC(5, 2) DEFAULT 0.0,
    manager_name VARCHAR(100),
    contact_email VARCHAR(150)
);

-- Multi-Warehouse Inventory Stock
CREATE TABLE IF NOT EXISTS inventories (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    quantity_available INT NOT NULL DEFAULT 0,
    reserved_quantity INT NOT NULL DEFAULT 0,
    reorder_level INT NOT NULL DEFAULT 0,
    safety_stock INT NOT NULL DEFAULT 0,
    last_restocked_at TIMESTAMP
);

-- Customer Orders
CREATE TABLE IF NOT EXISTS customer_orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(100) NOT NULL UNIQUE,
    customer_name VARCHAR(200) NOT NULL,
    order_date DATE NOT NULL,
    expected_delivery_date DATE,
    status VARCHAR(50) NOT NULL,
    total_amount NUMERIC(15, 2) NOT NULL
);

-- Order Items
CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity INT NOT NULL,
    unit_price NUMERIC(15, 2) NOT NULL
);

-- Shipments & Logistics Tracking
CREATE TABLE IF NOT EXISTS shipments (
    id BIGSERIAL PRIMARY KEY,
    tracking_code VARCHAR(100) NOT NULL UNIQUE,
    supplier_id BIGINT REFERENCES suppliers(id) ON DELETE SET NULL,
    destination_warehouse_id BIGINT REFERENCES warehouses(id) ON DELETE SET NULL,
    order_id BIGINT REFERENCES customer_orders(id) ON DELETE SET NULL,
    origin VARCHAR(200),
    destination VARCHAR(200),
    shipped_date DATE,
    estimated_delivery_date DATE,
    actual_delivery_date DATE,
    status VARCHAR(50) NOT NULL,
    delay_days INT DEFAULT 0,
    carrier_name VARCHAR(100)
);

-- Risk Alerts
CREATE TABLE IF NOT EXISTS risk_alerts (
    id BIGSERIAL PRIMARY KEY,
    risk_category VARCHAR(50) NOT NULL,
    severity_level VARCHAR(50) NOT NULL,
    entity_type VARCHAR(100),
    entity_id BIGINT,
    description TEXT NOT NULL,
    recommendation_text TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Action Approvals & Recommendations (Human-in-the-loop)
CREATE TABLE IF NOT EXISTS recommendations (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    type VARCHAR(50) NOT NULL,
    action_payload_json TEXT,
    reasoning TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_APPROVAL',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    executed_at TIMESTAMP,
    executed_by VARCHAR(100)
);

-- Document Metadata for Policy RAG
CREATE TABLE IF NOT EXISTS document_metadata (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(100),
    file_size BIGINT,
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    summary TEXT,
    chunk_count INT DEFAULT 0
);

-- Audit Log for Action Approvals
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(100),
    action_taken VARCHAR(200) NOT NULL,
    entity_affected VARCHAR(100),
    entity_id VARCHAR(100),
    details TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Demand Forecast Results Cache
CREATE TABLE IF NOT EXISTS demand_forecasts (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    forecast_date DATE NOT NULL,
    projected7day_demand INT,
    projected30day_demand INT,
    days_until_stockout INT,
    stockout_warning BOOLEAN DEFAULT FALSE,
    confidence_score NUMERIC(5, 2)
);

-- Vector Store for PgVector
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT,
    metadata JSON,
    embedding VECTOR(768)
);
