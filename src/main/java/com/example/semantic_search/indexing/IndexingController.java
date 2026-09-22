package com.example.semantic_search.indexing;

/**
 * İndeksleme denetleyicisi.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.controller.IndexingController} paketine taşınmıştır.
 */
@Deprecated
public class IndexingController extends com.example.semantic_search.controller.IndexingController {

    public IndexingController(com.example.semantic_search.service.IndexingService indexingService) {
        super(indexingService);
    }
}
