package com.example.semantic_search.kafka;

/**
 * Arama olay işlemcisi.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.service.SearchEventProcessor} paketine taşınmıştır.
 */
@Deprecated
public class SearchEventProcessor extends com.example.semantic_search.service.SearchEventProcessor {

    public SearchEventProcessor(com.example.semantic_search.kafka.SearchEventMapper mapper,
                                com.example.semantic_search.config.KafkaIndexingProperties properties,
                                com.example.semantic_search.service.IndexingService indexingService,
                                com.example.semantic_search.repository.IndexingStateRepository repository,
                                jakarta.validation.Validator validator) {
        super(mapper, properties, indexingService, repository, validator);
    }
}
