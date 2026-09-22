ALTER TABLE indexing_state ADD COLUMN document_source TEXT;

ALTER TABLE indexing_state ADD CONSTRAINT ck_indexing_state_document_source_json
    CHECK (document_source IS JSON);
