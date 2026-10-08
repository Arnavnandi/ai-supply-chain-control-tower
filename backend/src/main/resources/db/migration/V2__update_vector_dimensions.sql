-- V2__update_vector_dimensions.sql
-- Safely alter vector_store table embedding dimension from 384 to 768 for cloud Gemini deployment

DROP INDEX IF EXISTS vector_store_embedding_idx;

TRUNCATE TABLE vector_store;

ALTER TABLE vector_store ALTER COLUMN embedding TYPE VECTOR(768);
