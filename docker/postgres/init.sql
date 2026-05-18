CREATE EXTENSION IF NOT EXISTS vector;

CREATE SCHEMA IF NOT EXISTS rag;

CREATE TABLE IF NOT EXISTS rag.documents (
    id bigserial PRIMARY KEY,
    source_type text NOT NULL,
    source_id text NOT NULL,
    title text,
    content text NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    embedding vector(1536) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS documents_source_unique_idx
    ON rag.documents (source_type, source_id);

CREATE INDEX IF NOT EXISTS documents_metadata_gin_idx
    ON rag.documents USING gin (metadata);

-- No ivfflat index by design: this project ships with a small corpus (handful of books).
-- An IVF index with the default probes=1 returns empty/partial results when row count is much
-- less than `lists`. With this few rows a sequential scan over the cosine operator (<=>) is
-- both correct and faster. Add an ivfflat or hnsw index once the corpus is large enough,
-- and tune `lists` / `probes` accordingly.

