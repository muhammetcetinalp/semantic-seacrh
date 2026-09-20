package com.example.semantic_search.kafka;

import com.example.semantic_search.indexing.IndexDocumentRequest;

/** Replace this bean to adapt JSON envelopes, domain payloads, or source API lookups. */
public interface SearchEventMapper {
    SearchIndexingEvent read(String message);
    IndexDocumentRequest mapDocument(SearchIndexingEvent event, KafkaIndexingProperties.EventRoute route);
}
