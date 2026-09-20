ALTER TABLE indexing_state ADD document_source CLOB;

ALTER TABLE indexing_state ADD CONSTRAINT ck_indexing_state_document_source_json
    CHECK (document_source IS JSON);
