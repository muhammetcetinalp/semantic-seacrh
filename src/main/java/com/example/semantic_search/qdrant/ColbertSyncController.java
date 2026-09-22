package com.example.semantic_search.qdrant;

/**
 * ColBERT senkronizasyon denetleyicisi.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.controller.ColbertSyncController} paketine taşınmıştır.
 */
@Deprecated
public class ColbertSyncController extends com.example.semantic_search.controller.ColbertSyncController {

    public ColbertSyncController(com.example.semantic_search.service.ColbertIndexingSyncService syncService,
                                 com.example.semantic_search.client.qdrant.QdrantAdapter qdrantAdapter,
                                 com.example.semantic_search.service.ColbertService colbertService,
                                 com.example.semantic_search.config.ColbertProperties colbertProperties,
                                 com.example.semantic_search.config.QdrantProperties qdrantProperties) {
        super(syncService, qdrantAdapter, colbertService, colbertProperties, qdrantProperties);
    }
}
