-- V9__create_csv_import_history.sql: Tenant-Scoped CSV Import History
CREATE TABLE IF NOT EXISTS csv_import_history (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    username VARCHAR(100),
    filename VARCHAR(255) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    records_imported INT NOT NULL DEFAULT 0,
    records_failed INT NOT NULL DEFAULT 0,
    error_summary TEXT,
    file_size_bytes BIGINT,
    file_hash VARCHAR(64),
    started_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_csv_import_history_company_id ON csv_import_history(company_id);
CREATE INDEX IF NOT EXISTS idx_csv_import_history_started_at ON csv_import_history(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_csv_import_history_file_hash ON csv_import_history(company_id, file_hash);
