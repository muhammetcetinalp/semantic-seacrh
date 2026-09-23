-- =============================================================================
-- SEMANTIC SEARCH ENGINE — COMPLETE POSTGRESQL 17 SCHEMA (DDL)
-- =============================================================================
-- Bu script, Flyway (V1 - V5) migration dosyalarının birleştirilmiş halidir.
-- Normalde Spring Boot başladığında Flyway bu tabloları otomatik oluşturur.
-- Ancak Flyway kullanmadan manuel oluşturmak isterseniz bu dosyayı çalıştırabilirsiniz.
-- =============================================================================

-- 1. İndeksleme Durum Takip Tablosu (indexing_state)
CREATE SEQUENCE IF NOT EXISTS indexing_state_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS indexing_state (
    id                  BIGINT DEFAULT nextval('indexing_state_seq') PRIMARY KEY,
    document_id         VARCHAR(255) NOT NULL,
    index_name          VARCHAR(255) NOT NULL,
    status              VARCHAR(50) DEFAULT 'PENDING' NOT NULL,
    search_text_hash    VARCHAR(64),
    last_indexed_at     TIMESTAMPTZ,
    error_message       TEXT,
    retry_count         INT DEFAULT 0 NOT NULL,
    created_at          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_event_id       VARCHAR(128),
    last_event_version  BIGINT,
    document_source     TEXT,
    CONSTRAINT uq_document_index UNIQUE (document_id, index_name),
    CONSTRAINT ck_indexing_state_document_source_json CHECK (document_source IS NULL OR document_source IS JSON)
);

CREATE INDEX IF NOT EXISTS idx_indexing_state_status ON indexing_state (status);
CREATE INDEX IF NOT EXISTS idx_indexing_state_document_id ON indexing_state (document_id);


-- 2. Arama Sorgu ve İstatistik Log Tablosu (search_query_log)
CREATE SEQUENCE IF NOT EXISTS search_query_log_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS search_query_log (
    id                     BIGINT DEFAULT nextval('search_query_log_seq') PRIMARY KEY,

    -- Request context
    query                  VARCHAR(2000)   NOT NULL,
    index_name             VARCHAR(255)    NOT NULL,
    search_type            VARCHAR(50)     DEFAULT 'HYBRID_EXPLAIN' NOT NULL,

    -- Timing (ms)
    took_ms                BIGINT,
    bm25_took_ms           BIGINT,
    semantic_took_ms       BIGINT,

    -- Settings snapshot (weights, k, RRF constant)
    bm25_weight            NUMERIC(5,4),
    semantic_weight        NUMERIC(5,4),
    rank_constant          INT,
    candidate_limit        INT,
    result_limit           INT,

    -- Result counts
    bm25_result_count      INT,
    semantic_result_count  INT,
    final_result_count     INT,
    total_candidates       INT,

    -- Full JSON payloads for deep inspection
    bm25_results_json      TEXT,
    semantic_results_json  TEXT,
    final_results_json     TEXT,
    settings_json          TEXT,

    -- Error tracking
    error_message          TEXT,
    status                 VARCHAR(20) DEFAULT 'SUCCESS' NOT NULL,

    -- Timestamps
    created_at             TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sql_query    ON search_query_log (query);
CREATE INDEX IF NOT EXISTS idx_sql_created  ON search_query_log (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sql_status   ON search_query_log (status);
CREATE INDEX IF NOT EXISTS idx_sql_index    ON search_query_log (index_name);
