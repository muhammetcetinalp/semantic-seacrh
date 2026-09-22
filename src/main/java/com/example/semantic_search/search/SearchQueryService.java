package com.example.semantic_search.search;

/**
 * Arama sorgulama servisi.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.service.SearchQueryService} paketine taşınmıştır.
 */
@Deprecated
public class SearchQueryService extends com.example.semantic_search.service.SearchQueryService {

    public SearchQueryService(com.example.semantic_search.client.opensearch.OpenSearchAdapter openSearchAdapter,
                              com.example.semantic_search.client.embedding.EmbeddingProvider embeddingProvider,
                              com.example.semantic_search.config.SearchProperties searchProperties,
                              com.example.semantic_search.service.SearchQueryLogService queryLogService,
                              java.util.Optional<com.example.semantic_search.service.RerankingService> rerankingService,
                              java.util.Optional<com.example.semantic_search.service.ColbertService> colbertService,
                              java.util.Optional<com.example.semantic_search.client.qdrant.QdrantAdapter> qdrantAdapter,
                              java.util.Optional<com.example.semantic_search.config.ColbertProperties> colbertProperties) {
        super(openSearchAdapter, embeddingProvider, searchProperties, queryLogService, rerankingService, colbertService, qdrantAdapter, colbertProperties);
    }
}
