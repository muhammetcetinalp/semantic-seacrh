ALTER TABLE indexing_state
    ADD COLUMN last_event_id VARCHAR(128),
    ADD COLUMN last_event_version BIGINT;
