package com.example.semantic_search.indexing;

/**
 * Olaylar içe alma servisi.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.service.OlaylarIngestionService} paketine taşınmıştır.
 */
@Deprecated
public class OlaylarIngestionService extends com.example.semantic_search.service.OlaylarIngestionService {

    public OlaylarIngestionService(com.example.semantic_search.client.opensearch.OpenSearchAdapter openSearchAdapter,
                                   com.example.semantic_search.service.ColbertService colbertService,
                                   com.example.semantic_search.client.qdrant.QdrantAdapter qdrantAdapter,
                                   com.example.semantic_search.client.embedding.EmbeddingProvider embeddingProvider,
                                   tools.jackson.databind.ObjectMapper objectMapper,
                                   com.example.semantic_search.repository.IndexingStateRepository indexingStateRepository,
                                   com.example.semantic_search.client.opensearch.OpenSearchDocumentSourceMapper documentSourceMapper) {
        super(openSearchAdapter, colbertService, qdrantAdapter, embeddingProvider, objectMapper, indexingStateRepository, documentSourceMapper);
    }
}
