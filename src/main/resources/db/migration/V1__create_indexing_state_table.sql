CREATE SEQUENCE indexing_state_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE indexing_state (
    id               BIGINT DEFAULT nextval('indexing_state_seq') PRIMARY KEY,
    document_id      VARCHAR(255) NOT NULL,
    index_name       VARCHAR(255) NOT NULL,
    status           VARCHAR(50) DEFAULT 'PENDING' NOT NULL,
    search_text_hash VARCHAR(64),
    last_indexed_at  TIMESTAMPTZ,
    error_message    TEXT,
    retry_count      INT DEFAULT 0 NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at       TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_document_index UNIQUE (document_id, index_name)
);

CREATE INDEX idx_indexing_state_status ON indexing_state (status);
CREATE INDEX idx_indexing_state_document_id ON indexing_state (document_id);
