-- Search query log table for debugging and analysis.
-- Records every hybrid search explain request with full BM25, semantic and RRF results.

CREATE SEQUENCE search_query_log_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE search_query_log (
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

CREATE INDEX idx_sql_query    ON search_query_log (query);
CREATE INDEX idx_sql_created  ON search_query_log (created_at DESC);
CREATE INDEX idx_sql_status   ON search_query_log (status);
CREATE INDEX idx_sql_index    ON search_query_log (index_name);
