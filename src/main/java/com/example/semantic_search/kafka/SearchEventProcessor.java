package com.example.semantic_search.kafka;

import com.example.semantic_search.indexing.IndexDocumentRequest;
import com.example.semantic_search.indexing.IndexingService;
import com.example.semantic_search.indexing.IndexingState;
import com.example.semantic_search.indexing.IndexingStateRepository;
import jakarta.validation.Validator;
import org.springframework.transaction.annotation.Transactional;

/** The transaction commits before the listener returns and Kafka commits its offset. */
public class SearchEventProcessor {

    public enum Outcome { PROCESSED, IGNORED, STALE }

    private final SearchEventMapper mapper;
    private final KafkaIndexingProperties properties;
    private final IndexingService indexingService;
    private final IndexingStateRepository repository;
    private final Validator validator;

    public SearchEventProcessor(SearchEventMapper mapper, KafkaIndexingProperties properties,
                                IndexingService indexingService, IndexingStateRepository repository,
                                Validator validator) {
        this.mapper = mapper;
        this.properties = properties;
        this.indexingService = indexingService;
        this.repository = repository;
        this.validator = validator;
    }

    @Transactional
    public Outcome process(String message) {
        SearchIndexingEvent event = mapper.read(message);
        KafkaIndexingProperties.EventRoute route = properties.getRoutes().get(event.eventType());
        if (route == null) return Outcome.IGNORED;
        validate(event);

        // Mapping/validation happens before creating state or doing external work.
        IndexDocumentRequest document = null;
        if (route.operation() == KafkaIndexingProperties.Operation.UPSERT) {
            document = mapper.mapDocument(event, route);
            validate(document);
            if (!event.documentId().equals(document.getId()) || !route.indexName().equals(document.getIndexName())) {
                throw new InvalidSearchEventException("Mapped document identity does not match its event route.");
            }
        }

        IndexingState state = repository.findByDocumentIdAndIndexNameForUpdate(event.documentId(), route.indexName())
                .orElseGet(() -> {
                    IndexingState pending = new IndexingState();
                    pending.setDocumentId(event.documentId());
                    pending.setIndexName(route.indexName());
                    // The unique constraint serializes racing first deliveries; losers retry.
                    return repository.saveAndFlush(pending);
                });

        if (state.getLastEventVersion() != null && event.version() <= state.getLastEventVersion()) {
            return Outcome.STALE;
        }

        switch (route.operation()) {
            case UPSERT -> indexingService.updateDocument(event.documentId(), document);
            case DELETE -> indexingService.deleteDocument(route.indexName(), event.documentId());
        }

        state.setLastEventId(event.eventId());
        state.setLastEventVersion(event.version());
        repository.saveAndFlush(state);
        return Outcome.PROCESSED;
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) {
            throw new InvalidSearchEventException("Selected event or mapped document failed validation.");
        }
    }
}
