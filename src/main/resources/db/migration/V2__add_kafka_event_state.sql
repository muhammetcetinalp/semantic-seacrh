ALTER TABLE indexing_state ADD (
    last_event_id VARCHAR2(128 CHAR),
    last_event_version NUMBER(19, 0)
);
