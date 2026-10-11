-- V7__add_company_multi_tenancy.sql: Multi-tenant Company Model & Database Isolation

-- 1. Create Companies Table
CREATE TABLE IF NOT EXISTS companies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    code VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed Default Baseline Demo Company
INSERT INTO companies (id, name, code, status)
VALUES (1, 'Demo Supply Chain Corp', 'DEMO', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- Synchronize Identity Sequence
SELECT setval('companies_id_seq', GREATEST(100, (SELECT COALESCE(MAX(id), 1) FROM companies)));

-- 2. Add company_id columns to all operational tables
ALTER TABLE users ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE supplier_products ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE warehouses ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE inventories ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE demand_forecasts ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE risk_alerts ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE document_metadata ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE telemetry_events ADD COLUMN IF NOT EXISTS company_id BIGINT;

-- 3. Backfill all existing records to Demo Company (ID = 1)
UPDATE users SET company_id = 1 WHERE company_id IS NULL;
UPDATE categories SET company_id = 1 WHERE company_id IS NULL;
UPDATE products SET company_id = 1 WHERE company_id IS NULL;
UPDATE suppliers SET company_id = 1 WHERE company_id IS NULL;
UPDATE supplier_products SET company_id = 1 WHERE company_id IS NULL;
UPDATE warehouses SET company_id = 1 WHERE company_id IS NULL;
UPDATE inventories SET company_id = 1 WHERE company_id IS NULL;
UPDATE customer_orders SET company_id = 1 WHERE company_id IS NULL;
UPDATE order_items SET company_id = 1 WHERE company_id IS NULL;
UPDATE shipments SET company_id = 1 WHERE company_id IS NULL;
UPDATE demand_forecasts SET company_id = 1 WHERE company_id IS NULL;
UPDATE risk_alerts SET company_id = 1 WHERE company_id IS NULL;
UPDATE recommendations SET company_id = 1 WHERE company_id IS NULL;
UPDATE audit_logs SET company_id = 1 WHERE company_id IS NULL;
UPDATE document_metadata SET company_id = 1 WHERE company_id IS NULL;
UPDATE telemetry_events SET company_id = 1 WHERE company_id IS NULL;

-- 4. Drop legacy global unique constraints
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_sku_key;
ALTER TABLE suppliers DROP CONSTRAINT IF EXISTS suppliers_code_key;
ALTER TABLE warehouses DROP CONSTRAINT IF EXISTS warehouses_code_key;
ALTER TABLE customer_orders DROP CONSTRAINT IF EXISTS customer_orders_order_number_key;
ALTER TABLE shipments DROP CONSTRAINT IF EXISTS shipments_tracking_code_key;

-- 5. Add tenant-scoped composite unique constraints
ALTER TABLE products ADD CONSTRAINT uk_products_company_sku UNIQUE (company_id, sku);
ALTER TABLE suppliers ADD CONSTRAINT uk_suppliers_company_code UNIQUE (company_id, code);
ALTER TABLE warehouses ADD CONSTRAINT uk_warehouses_company_code UNIQUE (company_id, code);
ALTER TABLE customer_orders ADD CONSTRAINT uk_orders_company_number UNIQUE (company_id, order_number);
ALTER TABLE shipments ADD CONSTRAINT uk_shipments_company_tracking UNIQUE (company_id, tracking_code);

-- 6. Enforce NOT NULL and Foreign Key Constraints
ALTER TABLE users ALTER COLUMN company_id SET NOT NULL, ADD CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE categories ALTER COLUMN company_id SET NOT NULL, ADD CONSTRAINT fk_categories_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE products ALTER COLUMN company_id SET NOT NULL, ADD CONSTRAINT fk_products_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE suppliers ALTER COLUMN company_id SET NOT NULL, ADD CONSTRAINT fk_suppliers_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE supplier_products ALTER COLUMN company_id SET NOT NULL, ADD CONSTRAINT fk_supplier_products_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE warehouses ALTER COLUMN company_id SET NOT NULL, ADD CONSTRAINT fk_warehouses_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE inventories ALTER COLUMN company_id SET NOT NULL, ADD CONSTRAINT fk_inventories_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE customer_orders ALTER COLUMN company_id SET NOT NULL, ADD CONSTRAINT fk_customer_orders_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE order_items ALTER COLUMN company_id SET NOT NULL, ADD CONSTRAINT fk_order_items_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE shipments ALTER COLUMN company_id SET NOT NULL, ADD CONSTRAINT fk_shipments_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE demand_forecasts ALTER COLUMN company_id SET NOT NULL, ADD CONSTRAINT fk_demand_forecasts_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE risk_alerts ALTER COLUMN company_id SET NOT NULL, ADD CONSTRAINT fk_risk_alerts_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE recommendations ALTER COLUMN company_id SET NOT NULL, ADD CONSTRAINT fk_recommendations_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE audit_logs ALTER COLUMN company_id SET NOT NULL, ADD CONSTRAINT fk_audit_logs_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE document_metadata ADD CONSTRAINT fk_document_metadata_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE telemetry_events ALTER COLUMN company_id SET NOT NULL, ADD CONSTRAINT fk_telemetry_events_company FOREIGN KEY (company_id) REFERENCES companies(id);
