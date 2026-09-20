CREATE SEQUENCE indexing_state_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE indexing_state (
    id               NUMBER(19, 0) DEFAULT indexing_state_seq.NEXTVAL PRIMARY KEY,
    document_id      VARCHAR2(255 CHAR) NOT NULL,
    index_name       VARCHAR2(255 CHAR) NOT NULL,
    status           VARCHAR2(50 CHAR) DEFAULT 'PENDING' NOT NULL,
    search_text_hash VARCHAR2(64 CHAR),
    last_indexed_at   TIMESTAMP(6) WITH TIME ZONE,
    error_message    CLOB,
    retry_count      NUMBER(10, 0) DEFAULT 0 NOT NULL,
    created_at       TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at       TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uq_document_index UNIQUE (document_id, index_name)
);

CREATE INDEX idx_indexing_state_status ON indexing_state (status);
CREATE INDEX idx_indexing_state_document_id ON indexing_state (document_id);
