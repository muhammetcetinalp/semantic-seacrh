package com.example.semantic_search.indexing;

/**
 * İndeksleme servisi.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.service.IndexingService} paketine taşınmıştır.
 */
@Deprecated
public class IndexingService extends com.example.semantic_search.service.IndexingService {

    public IndexingService(com.example.semantic_search.client.opensearch.OpenSearchAdapter openSearchAdapter,
                           com.example.semantic_search.client.embedding.EmbeddingProvider embeddingProvider,
                           com.example.semantic_search.repository.IndexingStateRepository indexingStateRepository,
                           com.example.semantic_search.config.SearchProperties searchProperties,
                           com.example.semantic_search.client.opensearch.OpenSearchDocumentSourceMapper documentSourceMapper,
                           java.util.Optional<com.example.semantic_search.service.ColbertIndexingSyncService> colbertSyncService) {
        super(openSearchAdapter, embeddingProvider, indexingStateRepository, searchProperties, documentSourceMapper, colbertSyncService);
    }
}
