-- V6__add_correlation_id_to_telemetry.sql: Add correlation_id tracking to telemetry_events table

ALTER TABLE telemetry_events ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_telemetry_events_correlation_id ON telemetry_events(correlation_id);
