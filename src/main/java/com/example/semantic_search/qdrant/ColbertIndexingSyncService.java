package com.example.semantic_search.qdrant;

/**
 * ColBERT indeks senkronizasyon servisi.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.service.ColbertIndexingSyncService} paketine taşınmıştır.
 */
@Deprecated
public class ColbertIndexingSyncService extends com.example.semantic_search.service.ColbertIndexingSyncService {

    public ColbertIndexingSyncService(com.example.semantic_search.client.opensearch.OpenSearchAdapter openSearchAdapter,
                                      com.example.semantic_search.service.ColbertService colbertService,
                                      com.example.semantic_search.client.qdrant.QdrantAdapter qdrantAdapter,
                                      com.example.semantic_search.config.ColbertProperties colbertProperties,
                                      com.example.semantic_search.config.QdrantProperties qdrantProperties,
                                      com.example.semantic_search.config.SearchProperties searchProperties) {
        super(openSearchAdapter, colbertService, qdrantAdapter, colbertProperties, qdrantProperties, searchProperties);
    }
}
