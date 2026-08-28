-- V5__create_telemetry_events_table.sql: Persistent storage for real-time telemetry events & alert querying

CREATE TABLE IF NOT EXISTS telemetry_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    source_domain VARCHAR(100),
    entity_id VARCHAR(100),
    message TEXT NOT NULL,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_telemetry_events_created_at ON telemetry_events(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_events_severity ON telemetry_events(severity);
CREATE INDEX IF NOT EXISTS idx_telemetry_events_source_domain ON telemetry_events(source_domain);
